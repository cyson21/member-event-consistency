package com.example.consistency.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FirstLoginRewardDbConcurrencyIT {

    private static final int REQUEST_COUNT = 8;
    private static final String UNIQUE_VIOLATION = "23505";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Test
    void uniqueRewardIssueConstraintAllowsOnlyOneFirstLoginRewardPerMemberUnderConcurrentAttempts() throws Exception {
        long memberId = insertMember("reward-db-concurrency@example.com");
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<InsertResult>> tasks = new ArrayList<>();

        for (int index = 0; index < REQUEST_COUNT; index++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return insertFirstLoginReward(memberId);
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        try {
            var futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();

            List<InsertResult> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }

            assertThat(results.stream().filter(InsertResult::inserted).count()).isEqualTo(1);
            assertThat(results.stream().filter(InsertResult::uniqueRejected).count()).isEqualTo(REQUEST_COUNT - 1L);
        } finally {
            executor.shutdownNow();
        }

        assertThat(rewardIssueCount(memberId)).isEqualTo(1L);
        assertThat(duplicateRewardGroupCount(memberId)).isZero();
    }

    private static long insertMember(String email) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into members (email, status) values (?, 'ACTIVE')",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, email);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private static InsertResult insertFirstLoginReward(long memberId) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into reward_issues (member_id, reward_type, status)
                     values (?, 'FIRST_LOGIN', 'ISSUED')
                     """)) {
            statement.setLong(1, memberId);
            statement.executeUpdate();
            return InsertResult.success();
        } catch (SQLException exception) {
            if (UNIQUE_VIOLATION.equals(exception.getSQLState())) {
                return InsertResult.rejectedByUniqueConstraint();
            }
            throw exception;
        }
    }

    private static long rewardIssueCount(long memberId) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select count(*)
                     from reward_issues
                     where member_id = ?
                       and reward_type = 'FIRST_LOGIN'
                     """)) {
            statement.setLong(1, memberId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static long duplicateRewardGroupCount(long memberId) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select count(*)
                     from (
                         select member_id, reward_type
                         from reward_issues
                         where member_id = ?
                         group by member_id, reward_type
                         having count(*) > 1
                     ) duplicates
                     """)) {
            statement.setLong(1, memberId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static Connection newConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private record InsertResult(boolean inserted, boolean uniqueRejected) {
        private static InsertResult success() {
            return new InsertResult(true, false);
        }

        private static InsertResult rejectedByUniqueConstraint() {
            return new InsertResult(false, true);
        }
    }
}
