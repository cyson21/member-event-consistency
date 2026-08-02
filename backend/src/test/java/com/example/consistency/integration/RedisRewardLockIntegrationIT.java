package com.example.consistency.integration;

import com.example.consistency.web.RedisRewardLockGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Redis (Testcontainers) lock contracts for the Redisson reward gateway path.
 * <p>
 * Scope is multi-client contention and lease/watchdog behavior against one Redis
 * instance. It does not claim multi-host crash failover, and PostgreSQL remains
 * the final invariant boundary (see {@code FirstLoginRewardDbConcurrencyIT}).
 * Docker is required; absence must fail rather than skip-as-success.
 */
@Testcontainers
class RedisRewardLockIntegrationIT {

    private static final long MEMBER_A = 940_101L;
    private static final long MEMBER_B = 940_102L;
    private static final long WATCHDOG_MS = 3_000L;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedissonClient clientOne;
    private static RedissonClient clientTwo;
    private static RedisRewardLockGateway gatewayOne;
    private static RedisRewardLockGateway gatewayTwo;

    @BeforeAll
    static void startClients() {
        clientOne = createClient(WATCHDOG_MS);
        clientTwo = createClient(WATCHDOG_MS);
        gatewayOne = new RedisRewardLockGateway(clientOne);
        gatewayTwo = new RedisRewardLockGateway(clientTwo);
    }

    @AfterAll
    static void stopClients() {
        shutdownQuietly(clientOne);
        shutdownQuietly(clientTwo);
    }

    @Test
    void distinctClientsCannotHoldTheSameRewardKeyConcurrently() throws Exception {
        CountDownLatch ownerReady = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicBoolean ownerHolds = new AtomicBoolean(false);
        AtomicInteger ownerRuns = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> owner = executor.submit(() -> gatewayOne.withRewardLock(MEMBER_A, () -> {
                ownerRuns.incrementAndGet();
                ownerHolds.set(true);
                ownerReady.countDown();
                awaitLatch(releaseOwner, Duration.ofSeconds(15), "owner release signal");
                ownerHolds.set(false);
                return "owner";
            }));

            awaitLatch(ownerReady, Duration.ofSeconds(10), "owner lock acquisition");
            assertThat(ownerHolds).isTrue();

            assertThatThrownBy(() -> gatewayTwo.withRewardLock(MEMBER_A, () -> "intruder"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Redis reward lock was not acquired before DB guard");

            releaseOwner.countDown();
            assertThat(owner.get(10, TimeUnit.SECONDS)).isEqualTo("owner");
            assertThat(ownerRuns).hasValue(1);
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentRewardKeysAreAcquiredIndependentlyBySeparateClients() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch releaseBoth = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> gatewayOne.withRewardLock(MEMBER_A, () -> {
                entered.incrementAndGet();
                bothEntered.countDown();
                awaitLatch(releaseBoth, Duration.ofSeconds(15), "independent-key release");
                return "A";
            }));
            Future<String> second = executor.submit(() -> gatewayTwo.withRewardLock(MEMBER_B, () -> {
                entered.incrementAndGet();
                bothEntered.countDown();
                awaitLatch(releaseBoth, Duration.ofSeconds(15), "independent-key release");
                return "B";
            }));

            awaitLatch(bothEntered, Duration.ofSeconds(10), "independent keys held together");
            assertThat(entered).hasValue(2);
            releaseBoth.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("A");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("B");
        } finally {
            releaseBoth.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unlockAllowsAnotherClientToReacquireTheSameKey() {
        String first = gatewayOne.withRewardLock(MEMBER_A, () -> "first-holder");
        assertThat(first).isEqualTo("first-holder");

        String second = gatewayTwo.withRewardLock(MEMBER_A, () -> "second-holder");
        assertThat(second).isEqualTo("second-holder");
        assertThat(gatewayTwo.lastLockKey()).isEqualTo("lock:first-login-reward:" + MEMBER_A);
    }

    @Test
    void operationExceptionReleasesLockSoAnotherClientCanAcquire() {
        assertThatThrownBy(() -> gatewayOne.withRewardLock(MEMBER_A, () -> {
            throw new IllegalStateException("simulated work failure");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated work failure");

        String recovered = gatewayTwo.withRewardLock(MEMBER_A, () -> "recovered-after-exception");
        assertThat(recovered).isEqualTo("recovered-after-exception");
    }

    @Test
    void fixedLeaseExpiryDoesNotLeaveAPermanentLockAfterOwnerLeaves() throws Exception {
        String key = "lock:first-login-reward:" + MEMBER_A;
        RedissonClient shortLived = createClient(WATCHDOG_MS);
        try {
            RLock ownerLock = shortLived.getLock(key);
            assertThat(ownerLock.tryLock(200, 1_000, TimeUnit.MILLISECONDS)).isTrue();
            shortLived.shutdown();
            shortLived = null;

            awaitTrue(
                    () -> tryAcquireAndRelease(clientTwo, key, 100),
                    POLL_TIMEOUT,
                    "contender should acquire after fixed lease ends without permanent residue"
            );
        } finally {
            shutdownQuietly(shortLived);
            forceUnlockIfPresent(clientTwo, key);
        }
    }

    @Test
    void watchdogPathKeepsLockAliveLongerThanWatchdogTimeoutDuringHeldWork() throws Exception {
        CountDownLatch ownerReady = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicInteger blockedAttempts = new AtomicInteger();
        AtomicLong heldStartedAtMs = new AtomicLong();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> owner = executor.submit(() -> gatewayOne.withRewardLock(MEMBER_A, () -> {
                heldStartedAtMs.set(System.nanoTime());
                ownerReady.countDown();
                awaitLatch(releaseOwner, Duration.ofSeconds(30), "watchdog owner release");
                return "extended";
            }));

            awaitLatch(ownerReady, Duration.ofSeconds(10), "watchdog owner acquisition");

            // Contender stays blocked across at least one watchdog renewal window.
            awaitTrue(
                    () -> {
                        long heldMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heldStartedAtMs.get());
                        try {
                            gatewayTwo.withRewardLock(MEMBER_A, () -> "should-not-pass");
                            return false;
                        } catch (IllegalStateException expected) {
                            blockedAttempts.incrementAndGet();
                            return blockedAttempts.get() >= 2 && heldMs >= WATCHDOG_MS + 500L;
                        }
                    },
                    Duration.ofMillis(WATCHDOG_MS * 3),
                    "contender must stay blocked across watchdog renewal window"
            );

            releaseOwner.countDown();
            assertThat(owner.get(10, TimeUnit.SECONDS)).isEqualTo("extended");
            assertThat(blockedAttempts.get()).isGreaterThanOrEqualTo(2);

            String afterRelease = gatewayTwo.withRewardLock(MEMBER_A, () -> "after-watchdog");
            assertThat(afterRelease).isEqualTo("after-watchdog");
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void redisFrontContentionDoesNotReplaceDbFinalBoundary() throws Exception {
        // Redis rejects concurrent same-key holders here; DB unique/check ITs remain the
        // final invariant proofs and are intentionally out of this Redis-only suite.
        int attempts = 6;
        CountDownLatch ownerReady = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicInteger redisRejected = new AtomicInteger();
        AtomicInteger redisAccepted = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(attempts + 1);
        try {
            Future<String> owner = executor.submit(() -> gatewayOne.withRewardLock(MEMBER_A, () -> {
                ownerReady.countDown();
                awaitLatch(releaseOwner, Duration.ofSeconds(15), "boundary owner hold");
                return "owner";
            }));

            awaitLatch(ownerReady, Duration.ofSeconds(10), "boundary owner acquisition");

            List<Callable<String>> contenders = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                contenders.add(() -> {
                    try {
                        return gatewayTwo.withRewardLock(MEMBER_A, () -> {
                            redisAccepted.incrementAndGet();
                            return "accepted";
                        });
                    } catch (IllegalStateException rejected) {
                        redisRejected.incrementAndGet();
                        return "rejected";
                    }
                });
            }

            List<Future<String>> futures = contenders.stream().map(executor::submit).toList();
            for (Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("rejected");
            }

            releaseOwner.countDown();
            assertThat(owner.get(10, TimeUnit.SECONDS)).isEqualTo("owner");
            assertThat(redisRejected.get()).isEqualTo(attempts);
            assertThat(redisAccepted.get()).isZero();
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
        }
    }

    private static RedissonClient createClient(long lockWatchdogTimeoutMs) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379))
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        config.setLockWatchdogTimeout(lockWatchdogTimeoutMs);
        config.setThreads(2);
        config.setNettyThreads(2);
        return Redisson.create(config);
    }

    private static void shutdownQuietly(RedissonClient client) {
        if (client != null && !client.isShutdown()) {
            client.shutdown();
        }
    }

    private static void forceUnlockIfPresent(RedissonClient client, String key) {
        if (client == null || client.isShutdown()) {
            return;
        }
        RLock lock = client.getLock(key);
        if (lock.isLocked()) {
            lock.forceUnlock();
        }
    }

    private static boolean tryAcquireAndRelease(RedissonClient client, String key, long waitMs) {
        RLock contender = client.getLock(key);
        try {
            boolean acquired = contender.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (acquired) {
                contender.unlock();
            }
            return acquired;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while probing lock expiry for " + key, interrupted);
        }
    }

    private static void awaitLatch(CountDownLatch latch, Duration timeout, String label) {
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("timed out waiting for: " + label);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for: " + label, interrupted);
        }
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout, String message) {
        long deadline = System.nanoTime() + timeout.toNanos();
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (AssertionError failure) {
                lastFailure = failure;
            } catch (RuntimeException failure) {
                lastFailure = new AssertionError(failure);
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while polling: " + message, interrupted);
            }
        }
        if (lastFailure != null) {
            throw new AssertionError(message, lastFailure);
        }
        throw new AssertionError(message);
    }
}
