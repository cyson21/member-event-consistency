package com.example.consistency.scenario;

import java.util.ArrayList;
import java.util.List;

public final class InvariantChecker {

    private InvariantChecker() {
    }

    public static InvariantResult evaluate(InvariantResult input) {
        List<String> failures = new ArrayList<>();

        if (input.duplicateCount() > 0) {
            failures.add("duplicate reward count must stay zero");
        }
        if (input.overIssueCount() > 0) {
            failures.add("over issue count must stay zero");
        }
        if (input.negativeBalanceCount() > 0) {
            failures.add("negative balance count must stay zero");
        }
        if (input.doubleUseCount() > 0) {
            failures.add("double use count must stay zero");
        }
        if (input.terminalStateConflictCount() > 0) {
            failures.add("terminal state conflict count must stay zero");
        }
        if (input.strategy().isAsyncAccepted() && input.completedCount() > input.acceptedCount()) {
            failures.add("completed count cannot exceed accepted count");
        }

        if (failures.isEmpty()) {
            String message = "invariant passed".equals(input.message()) || input.message().isBlank()
                    ? "invariant passed"
                    : input.message();
            return new InvariantResult(
                    input.scenario(),
                    input.strategy(),
                    true,
                    input.duplicateCount(),
                    input.overIssueCount(),
                    input.negativeBalanceCount(),
                    input.doubleUseCount(),
                    input.terminalStateConflictCount(),
                    input.timeoutCount(),
                    input.acceptedCount(),
                    input.completedCount(),
                    input.dbWaitMsP95(),
                    input.redisWaitMsP95(),
                    input.queueLagMsP95(),
                    message
            );
        }

        return new InvariantResult(
                input.scenario(),
                input.strategy(),
                false,
                input.duplicateCount(),
                input.overIssueCount(),
                input.negativeBalanceCount(),
                input.doubleUseCount(),
                input.terminalStateConflictCount(),
                input.timeoutCount(),
                input.acceptedCount(),
                input.completedCount(),
                input.dbWaitMsP95(),
                input.redisWaitMsP95(),
                input.queueLagMsP95(),
                String.join("; ", failures)
        );
    }
}
