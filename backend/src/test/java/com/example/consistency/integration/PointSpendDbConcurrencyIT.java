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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PointSpendDbConcurrencyIT {

    private static final String CHECK_VIOLATION = "23514";

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
    void rowLockSpendAllowsOnlyOneConcurrentDebitWhenBalanceCanCoverOneRequest() throws Exception {
        long memberId = insertMember("point-db-concurrency@example.com");
        insertPointAccount(memberId, 100L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int index = 0; index < 2; index++) {
            String idempotencyKey = "point-db-concurrency-" + index;
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return spendWithRowLock(memberId, 60L, idempotencyKey);
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();

            List<Boolean> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }

            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(pointBalance(memberId)).isEqualTo(40L);
        assertThat(pointLedgerCount(memberId)).isEqualTo(1L);
    }

    @Test
    void nonNegativeBalanceCheckRejectsUncheckedOverspendAsTheFinalDbGuard() throws Exception {
        long memberId = insertMember("point-check-guard@example.com");
        insertPointAccount(memberId, 40L);

        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     update point_accounts
                     set balance = balance - ?
                     where member_id = ?
                     """)) {
            statement.setLong(1, 60L);
            statement.setLong(2, memberId);
            try {
                statement.executeUpdate();
            } catch (SQLException exception) {
                assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
            }
        }

        assertThat(pointBalance(memberId)).isEqualTo(40L);
        assertThat(pointLedgerCount(memberId)).isZero();
    }

    private static boolean spendWithRowLock(long memberId, long amount, String idempotencyKey) throws SQLException {
        try (Connection connection = newConnection()) {
            connection.setAutoCommit(false);
            try {
                long balance = balanceForUpdate(connection, memberId);
                if (balance < amount) {
                    connection.commit();
                    return false;
                }
                debit(connection, memberId, amount);
                insertLedger(connection, memberId, amount, idempotencyKey);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static long balanceForUpdate(Connection connection, long memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select balance
                from point_accounts
                where member_id = ?
                for update
                """)) {
            statement.setLong(1, memberId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static void debit(Connection connection, long memberId, long amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update point_accounts
                set balance = balance - ?,
                    version = version + 1,
                    updated_at = now()
                where member_id = ?
                """)) {
            statement.setLong(1, amount);
            statement.setLong(2, memberId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static void insertLedger(Connection connection, long memberId, long amount, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into point_ledger (event_id, member_id, amount, ledger_type, idempotency_key)
                values (?, ?, ?, 'SPEND', ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, memberId);
            statement.setLong(3, -amount);
            statement.setString(4, idempotencyKey);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
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

    private static void insertPointAccount(long memberId, long balance) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into point_accounts (member_id, balance)
                     values (?, ?)
                     """)) {
            statement.setLong(1, memberId);
            statement.setLong(2, balance);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static long pointBalance(long memberId) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select balance
                     from point_accounts
                     where member_id = ?
                     """)) {
            statement.setLong(1, memberId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static long pointLedgerCount(long memberId) throws SQLException {
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select count(*)
                     from point_ledger
                     where member_id = ?
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
}
