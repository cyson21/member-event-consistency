package com.example.consistency.web;

import com.example.consistency.reward.RewardType;
import com.example.consistency.reward.SqlRewardFollowUpRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(RewardFollowUpOutboxListenerTransactionPhaseTest.TestConfig.class)
class RewardFollowUpOutboxListenerTransactionPhaseTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private SqlRewardFollowUpRecorder recorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetRecorder() {
        clearInvocations(recorder);
    }

    @Test
    void directListenerInvocationDelegatesToRecorder() {
        RewardFollowUpOutboxListener rawListener = new RewardFollowUpOutboxListener(recorder);

        rawListener.recordFakeNotification(new RewardFollowUpRequestedEvent(701L, RewardType.FIRST_LOGIN));

        verify(recorder).recordFakeAfterCommitNotification(701L, RewardType.FIRST_LOGIN);
    }

    @Test
    void committedTransactionDispatchesListenerAfterCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new RewardFollowUpRequestedEvent(702L, RewardType.FIRST_LOGIN));
            verify(recorder, never()).recordFakeAfterCommitNotification(702L, RewardType.FIRST_LOGIN);
        });

        verify(recorder).recordFakeAfterCommitNotification(702L, RewardType.FIRST_LOGIN);
    }

    @Test
    void rolledBackTransactionDoesNotDispatchListener() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new RewardFollowUpRequestedEvent(703L, RewardType.FIRST_LOGIN));
            verify(recorder, never()).recordFakeAfterCommitNotification(703L, RewardType.FIRST_LOGIN);
            status.setRollbackOnly();
        });

        verify(recorder, never()).recordFakeAfterCommitNotification(703L, RewardType.FIRST_LOGIN);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource(RecordingJdbcState jdbcState) throws SQLException {
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(jdbcState.connection());
            return dataSource;
        }

        @Bean
        Connection jdbcConnection() {
            return mock(Connection.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        RecordingJdbcState recordingJdbcState(Connection connection) {
            return new RecordingJdbcState(connection);
        }

        @Bean
        SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder() {
            return mock(SqlRewardFollowUpRecorder.class);
        }

        @Bean
        RewardFollowUpOutboxListener rewardFollowUpOutboxListener(SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder) {
            return new RewardFollowUpOutboxListener(sqlRewardFollowUpRecorder);
        }
    }

    static final class RecordingJdbcState {
        private final Connection connection;

        RecordingJdbcState(Connection connection) {
            this.connection = connection;
            whenNoCheckedExceptions();
        }

        private void whenNoCheckedExceptions() {
            try {
                when(connection.isClosed()).thenReturn(false);
                when(connection.getAutoCommit()).thenReturn(false);
            } catch (SQLException throwable) {
                throw new IllegalStateException(throwable);
            }
        }

        Connection connection() {
            return connection;
        }
    }

}
