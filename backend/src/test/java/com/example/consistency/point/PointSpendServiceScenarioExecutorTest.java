package com.example.consistency.point;

import com.example.consistency.scenario.InMemoryScenarioRunReportRepository;
import com.example.consistency.scenario.ScenarioMetricName;
import com.example.consistency.scenario.ScenarioRunRecord;
import com.example.consistency.scenario.StrategyType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PointSpendServiceScenarioExecutorTest {

    public static void main(String[] args) {
        rowLockPathPersistsServiceDecisionMetrics();
        conditionalUpdatePathPersistsRejectionMetricsWithoutRowLockWait();
        idempotencyReplayPathPersistsSingleLedgerAndReplayCount();
        idempotencyHashMismatchPathPersistsMismatchMetric();
        naivePathKeepsHarnessFailureEvidence();
    }

    private static void rowLockPathPersistsServiceDecisionMetrics() {
        BalancePointSpendRepository points = new BalancePointSpendRepository(1000L);
        PointSpendService service = new PointSpendService(points);
        InMemoryScenarioRunReportRepository reports = new InMemoryScenarioRunReportRepository();
        PointSpendServiceScenarioExecutor executor = new PointSpendServiceScenarioExecutor(service, reports);

        ScenarioRunRecord record = executor.execute(StrategyType.DB_ROW_LOCK, 93001L, 1000L, 700L, 2);

        assertEquals(true, record.report().invariant().passed(), "row-lock invariant");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.ACCEPTED_COUNT), "accepted count");
        assertEquals(2L, record.report().metricValue(ScenarioMetricName.COMPLETED_COUNT), "completed count");
        assertEquals(300L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "final balance");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "negative balance count");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "ledger entry count");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "rejected count");
        assertEquals(true, record.report().metricValue(ScenarioMetricName.DB_WAIT_MS_P95) > 0, "row-lock wait evidence");
        assertEquals(1L, reports.count(), "report persisted once");
    }

    private static void conditionalUpdatePathPersistsRejectionMetricsWithoutRowLockWait() {
        BalancePointSpendRepository points = new BalancePointSpendRepository(1000L);
        PointSpendService service = new PointSpendService(points);
        PointSpendServiceScenarioExecutor executor = new PointSpendServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.CONDITIONAL_UPDATE, 93002L, 1000L, 700L, 2);

        assertEquals(true, record.report().invariant().passed(), "conditional update invariant");
        assertEquals(300L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "final balance");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "ledger entry count");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "rejected count");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.DB_WAIT_MS_P95), "no row-lock wait evidence");
    }

    private static void idempotencyReplayPathPersistsSingleLedgerAndReplayCount() {
        BalancePointSpendRepository points = new BalancePointSpendRepository(1000L);
        PointSpendService service = new PointSpendService(points);
        PointSpendServiceScenarioExecutor executor = new PointSpendServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.executeWithIdempotencyKey(
                StrategyType.IDEMPOTENCY_REPLAY,
                93003L,
                1000L,
                700L,
                2,
                "spend-93003-001"
        );

        assertEquals(true, record.report().invariant().passed(), "idempotency invariant");
        assertEquals(300L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "final balance");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "single ledger entry");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "replay count");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "replay is not rejection");
    }

    private static void idempotencyHashMismatchPathPersistsMismatchMetric() {
        BalancePointSpendRepository points = new BalancePointSpendRepository(1000L);
        points.recordExistingIdempotency("spend-93005-001", "different-request-hash");
        PointSpendService service = new PointSpendService(points);
        PointSpendServiceScenarioExecutor executor = new PointSpendServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.executeWithIdempotencyKey(
                StrategyType.IDEMPOTENCY_REPLAY,
                93005L,
                1000L,
                700L,
                1,
                "spend-93005-001"
        );

        assertEquals(true, record.report().invariant().passed(), "hash mismatch does not break balance invariant");
        assertEquals(1000L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "balance is unchanged");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.POINT_LEDGER_ENTRY_COUNT), "no ledger entry is written");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.REJECTED_COUNT), "mismatch is rejected");
        assertEquals(0L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_REPLAY_COUNT), "mismatch is not replay");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.IDEMPOTENCY_HASH_MISMATCH_COUNT), "mismatch metric is persisted");
    }

    private static void naivePathKeepsHarnessFailureEvidence() {
        PointSpendService service = new PointSpendService(new BalancePointSpendRepository(1000L));
        PointSpendServiceScenarioExecutor executor = new PointSpendServiceScenarioExecutor(
                service,
                new InMemoryScenarioRunReportRepository()
        );

        ScenarioRunRecord record = executor.execute(StrategyType.NAIVE, 93004L, 1000L, 700L, 2);

        assertEquals(false, record.report().invariant().passed(), "naive invariant failure remains visible");
        assertEquals(-400L, record.report().metricValue(ScenarioMetricName.FINAL_POINT_BALANCE), "naive final balance");
        assertEquals(1L, record.report().metricValue(ScenarioMetricName.NEGATIVE_BALANCE_COUNT), "naive negative balance count");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static final class BalancePointSpendRepository implements PointSpendRepository {
        private long balance;
        private final Map<String, String> idempotencyHashes = new HashMap<>();

        private BalancePointSpendRepository(long balance) {
            this.balance = balance;
        }

        private void recordExistingIdempotency(String idempotencyKey, String requestHash) {
            idempotencyHashes.put(idempotencyKey, requestHash);
        }

        @Override
        public long balanceForUpdate(long memberId) {
            return balance;
        }

        @Override
        public boolean tryDebit(long memberId, long spendAmount) {
            if (balance < spendAmount) {
                return false;
            }
            balance -= spendAmount;
            return true;
        }

        @Override
        public boolean insertLedger(UUID eventId, long memberId, long amount, String idempotencyKey) {
            return true;
        }

        @Override
        public boolean insertIdempotencyRecord(String idempotencyKey, String requestHash, String responseRef) {
            boolean inserted = !idempotencyHashes.containsKey(idempotencyKey);
            idempotencyHashes.put(idempotencyKey, requestHash);
            return inserted;
        }

        @Override
        public long replayCount(String idempotencyKey) {
            return idempotencyHashes.containsKey(idempotencyKey) ? 1L : 0L;
        }

        @Override
        public long requestHashMismatchCount(String idempotencyKey, String requestHash) {
            String storedHash = idempotencyHashes.get(idempotencyKey);
            return storedHash != null && !storedHash.equals(requestHash) ? 1L : 0L;
        }
    }
}
