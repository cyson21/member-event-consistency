package com.example.consistency.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesFoundationSchema() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                     select count(*)
                     from information_schema.tables
                     where table_schema = 'public'
                       and table_name in (
                         'members',
                         'point_accounts',
                         'point_ledger',
                         'reward_issues',
                         'coupon_campaigns',
                         'coupon_issues',
                         'idempotency_records',
                         'outbox_events',
                         'lock_attempts',
                         'queue_events',
                         'scenario_runs',
                         'scenario_metrics'
                       )
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(12);
        }
    }
}
