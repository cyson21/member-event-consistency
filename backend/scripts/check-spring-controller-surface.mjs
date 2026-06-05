import { readFileSync } from 'node:fs';

const source = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/ScenarioRunController.java', import.meta.url),
  'utf8',
);
const configuration = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/ScenarioRunConfiguration.java', import.meta.url),
  'utf8',
);
const sqlConfiguration = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/ScenarioSqlWiringConfiguration.java', import.meta.url),
  'utf8',
);
const campaignLockGateway = readFileSync(
  new URL('../src/main/java/com/example/consistency/coupon/LocalCouponCampaignLockGateway.java', import.meta.url),
  'utf8',
);
const redisRewardLockGateway = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/RedisRewardLockGateway.java', import.meta.url),
  'utf8',
);
const redisCouponCampaignLockGateway = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/RedisCouponCampaignLockGateway.java', import.meta.url),
  'utf8',
);
const springRewardFollowUpRecorder = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/SpringRewardFollowUpRecorder.java', import.meta.url),
  'utf8',
);
const rewardFollowUpOutboxListener = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/RewardFollowUpOutboxListener.java', import.meta.url),
  'utf8',
);
const transactionalFirstLoginRewardService = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/TransactionalFirstLoginRewardService.java', import.meta.url),
  'utf8',
);
const rewardFollowUpRequestedEvent = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/RewardFollowUpRequestedEvent.java', import.meta.url),
  'utf8',
);
const couponCampaignRabbitMqConfiguration = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/CouponCampaignRabbitMqConfiguration.java', import.meta.url),
  'utf8',
);
const couponCampaignRabbitMqCommand = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/CouponCampaignRabbitMqCommand.java', import.meta.url),
  'utf8',
);
const couponCampaignRabbitMqPublisher = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/CouponCampaignRabbitMqPublisher.java', import.meta.url),
  'utf8',
);
const couponCampaignRabbitMqRunTracker = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/CouponCampaignRabbitMqRunTracker.java', import.meta.url),
  'utf8',
);
const couponCampaignRabbitMqWorker = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/CouponCampaignRabbitMqWorker.java', import.meta.url),
  'utf8',
);
const rabbitMqCouponCampaignScenarioExecutor = readFileSync(
  new URL('../src/main/java/com/example/consistency/web/RabbitMqCouponCampaignScenarioExecutor.java', import.meta.url),
  'utf8',
);
const router = readFileSync(
  new URL('../src/main/java/com/example/consistency/api/ScenarioApiRouter.java', import.meta.url),
  'utf8',
);
const routerFactory = readFileSync(
  new URL('../src/main/java/com/example/consistency/api/ScenarioApiRouterFactory.java', import.meta.url),
  'utf8',
);

const requiredFragments = [
  '@RestController',
  '@RequestMapping("/api/scenarios")',
  'private final ScenarioApiRouter router;',
  'public ScenarioRunController(',
  'BatchExpirationApiHandler batchExpirationHandler',
  '@PostMapping(value = "/first-login-reward/runs"',
  '@PostMapping(value = "/coupon-campaign-issue/runs"',
  '@PostMapping(value = "/point-spend/runs"',
  '@PostMapping(value = "/coupon-redemption/runs"',
  '@PostMapping(value = "/batch-expiration/runs"',
  'ScenarioApiRouter',
  'CouponRedemptionApiHandler',
  'CouponRedemptionApiRequest',
  'CouponRedemptionApiResponse',
  'BatchExpirationApiHandler',
  'BatchExpirationApiRequest',
  'BatchExpirationApiResponse',
  '.status(response.statusCode())',
  'firstLoginReward',
  'couponCampaignIssue',
  'pointSpend',
  'couponRedemption',
  'batchExpiration',
  'int transientRetryCount',
  'int dlqCount',
  '"transientRetryCount", String.valueOf(request.transientRetryCount())',
  '"dlqCount", String.valueOf(request.dlqCount())',
  '"idempotencyKey", text(request.idempotencyKey())',
  'text(request.firstRequestHash())',
  'text(request.retryRequestHash())',
  'text(request.winner())',
  'private String text(String value)',
];

for (const fragment of requiredFragments) {
  if (!source.includes(fragment)) {
    throw new Error(`ScenarioRunController is missing required fragment: ${fragment}`);
  }
}

for (const forbidden of ['kafka', 'lock:member']) {
  if (source.includes(forbidden)) {
    throw new Error(`ScenarioRunController must not include out-of-scope fragment: ${forbidden}`);
  }
}

if (source.includes('new ScenarioApiRouter()')) {
  throw new Error('ScenarioRunController must receive ScenarioApiRouter through constructor injection');
}

for (const fragment of [
  '@Configuration',
  '@Bean',
  'public ScenarioRunReportRepository scenarioRunReportRepository(SqlExecutor sqlExecutor)',
  'return new SqlScenarioRunReportRepository(sqlExecutor);',
  'FirstLoginRewardService firstLoginRewardService,',
  'CouponCampaignService couponCampaignService,',
  'PointSpendService pointSpendService,',
  'ScenarioRunReportRepository scenarioRunReportRepository',
  'RabbitMqCouponCampaignScenarioExecutor rabbitMqCouponCampaignScenarioExecutor',
  'public CouponRedemptionApiHandler couponRedemptionApiHandler(',
  'return new CouponRedemptionApiHandler(',
  'new CouponRedemptionServiceScenarioExecutor(couponRedemptionService, scenarioRunReportRepository)',
  'public BatchExpirationApiHandler batchExpirationApiHandler(',
  'return new BatchExpirationApiHandler(',
  'new BatchExpirationServiceScenarioExecutor(batchExpirationService, scenarioRunReportRepository)',
  'public ScenarioApiRouter scenarioApiRouter(',
  'return ScenarioApiRouterFactory.executorBacked(',
  'firstLoginRewardService,',
  'rabbitMqCouponCampaignScenarioExecutor,',
  'pointSpendService,',
  'scenarioRunReportRepository',
]) {
  if (!configuration.includes(fragment)) {
    throw new Error(`ScenarioRunConfiguration is missing required fragment: ${fragment}`);
  }
}

if (configuration.includes('ScenarioApiRouterFactory.sqlBacked(')) {
  throw new Error('ScenarioRunConfiguration must use shared Spring service and report beans instead of creating a separate SQL-backed router');
}

for (const fragment of [
  'public final class ScenarioApiRouterFactory',
  'public static ScenarioApiRouter serviceBacked(',
  'FirstLoginRewardService firstLoginRewardService,',
  'CouponCampaignService couponCampaignService,',
  'PointSpendService pointSpendService,',
  'ScenarioRunReportRepository reportRepository',
  'new FirstLoginRewardServiceScenarioExecutor(firstLoginRewardService, reportRepository)',
  'new CouponCampaignServiceScenarioExecutor(couponCampaignService, reportRepository)',
  'new PointSpendServiceScenarioExecutor(pointSpendService, reportRepository)',
  'public static ScenarioApiRouter executorBacked(',
  'FirstLoginRewardScenarioExecution firstLoginRewardExecution,',
  'CouponCampaignScenarioExecution couponCampaignExecution,',
  'PointSpendScenarioExecution pointSpendExecution',
  'new CouponCampaignApiHandler(',
  'couponCampaignExecution',
  'public static ScenarioApiRouter sqlBacked(',
  'SqlExecutor sqlExecutor,',
  'RewardLockGateway rewardLockGateway,',
  'CouponCampaignLockGateway couponCampaignLockGateway',
  'FirstLoginRewardSqlWiring.service(sqlExecutor, rewardLockGateway)',
  'CouponCampaignSqlWiring.service(sqlExecutor, couponCampaignLockGateway)',
  'PointSpendSqlWiring.service(sqlExecutor)',
  'new SqlScenarioRunReportRepository(sqlExecutor)',
]) {
  if (!routerFactory.includes(fragment)) {
    throw new Error(`ScenarioApiRouterFactory is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'private final FirstLoginRewardApiHandler firstLoginRewardHandler;',
  'private final CouponCampaignApiHandler couponCampaignHandler;',
  'private final PointSpendApiHandler pointSpendHandler;',
  'public ScenarioApiRouter(',
  'this.firstLoginRewardHandler = Objects.requireNonNull(firstLoginRewardHandler);',
  'this.couponCampaignHandler = Objects.requireNonNull(couponCampaignHandler);',
  'this.pointSpendHandler = Objects.requireNonNull(pointSpendHandler);',
  'firstLoginRewardHandler.handle(new FirstLoginRewardApiRequest(',
  'couponCampaignHandler.handle(new CouponCampaignApiRequest(',
  'intValue(body, "transientRetryCount")',
  'intValue(body, "dlqCount")',
  'entry("queueRetryCount", response.queueRetryCount())',
  'entry("dlqCount", response.dlqCount())',
  'pointSpendHandler.handle(new PointSpendApiRequest(',
  'entry("idempotencyHashMismatchCount", response.idempotencyHashMismatchCount())',
]) {
  if (!router.includes(fragment)) {
    throw new Error(`ScenarioApiRouter is missing required injection fragment: ${fragment}`);
  }
}

for (const fragment of [
  '@Configuration',
  'RedissonClient redissonClient',
  'public SqlExecutor sqlExecutor(DataSource dataSource)',
  'return new JdbcSqlExecutor(dataSource);',
  'public RewardLockGateway rewardLockGateway(RedissonClient redissonClient)',
  'return new RedisRewardLockGateway(redissonClient);',
  'public CouponCampaignLockGateway couponCampaignLockGateway(RedissonClient redissonClient)',
  'return new RedisCouponCampaignLockGateway(redissonClient);',
  'public SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder(SqlExecutor sqlExecutor)',
  'return new SqlRewardFollowUpRecorder(sqlExecutor);',
  'public RewardFollowUpRecorder rewardFollowUpRecorder(',
  'ApplicationEventPublisher eventPublisher',
  'return new SpringRewardFollowUpRecorder(sqlRewardFollowUpRecorder, eventPublisher);',
  'public RewardFollowUpOutboxListener rewardFollowUpOutboxListener(SqlRewardFollowUpRecorder sqlRewardFollowUpRecorder)',
  'return new RewardFollowUpOutboxListener(sqlRewardFollowUpRecorder);',
  'public FirstLoginRewardService firstLoginRewardService(',
  'RewardFollowUpRecorder rewardFollowUpRecorder',
  'return new TransactionalFirstLoginRewardService(',
  'new SqlRewardIssueRepository(sqlExecutor),',
  'rewardFollowUpRecorder,',
  'public CouponCampaignService couponCampaignService(SqlExecutor sqlExecutor, CouponCampaignLockGateway couponCampaignLockGateway)',
  'return CouponCampaignSqlWiring.service(sqlExecutor, couponCampaignLockGateway);',
  'public PointSpendService pointSpendService(SqlExecutor sqlExecutor)',
  'return PointSpendSqlWiring.service(sqlExecutor);',
  'public CouponRedemptionService couponRedemptionService(SqlExecutor sqlExecutor)',
  'return CouponRedemptionSqlWiring.service(sqlExecutor);',
  'public BatchExpirationService batchExpirationService(SqlExecutor sqlExecutor)',
  'return BatchExpirationSqlWiring.service(sqlExecutor);',
]) {
  if (!sqlConfiguration.includes(fragment)) {
    throw new Error(`ScenarioSqlWiringConfiguration is missing required fragment: ${fragment}`);
  }
}

for (const forbidden of ['kafka', 'lock:member']) {
  if (
    sqlConfiguration.includes(forbidden)
    || campaignLockGateway.includes(forbidden)
    || redisRewardLockGateway.includes(forbidden)
    || redisCouponCampaignLockGateway.includes(forbidden)
    || springRewardFollowUpRecorder.includes(forbidden)
    || rewardFollowUpOutboxListener.includes(forbidden)
    || transactionalFirstLoginRewardService.includes(forbidden)
    || rewardFollowUpRequestedEvent.includes(forbidden)
  ) {
    throw new Error(`Spring SQL wiring surface must not include out-of-scope fragment: ${forbidden}`);
  }
}

for (const fragment of [
  'implements CouponCampaignLockGateway',
  'lock:coupon-campaign:',
  'withCampaignLock(long campaignId',
  'ReentrantLock',
]) {
  if (!campaignLockGateway.includes(fragment)) {
    throw new Error(`LocalCouponCampaignLockGateway is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'implements RewardLockGateway',
  'RedissonClient',
  'RLock',
  'lock:first-login-reward:',
  'tryLock(',
  'unlock();',
  'isHeldByCurrentThread()',
]) {
  if (!redisRewardLockGateway.includes(fragment)) {
    throw new Error(`RedisRewardLockGateway is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'implements CouponCampaignLockGateway',
  'RedissonClient',
  'RLock',
  'lock:coupon-campaign:',
  'tryLock(',
  'unlock();',
  'isHeldByCurrentThread()',
]) {
  if (!redisCouponCampaignLockGateway.includes(fragment)) {
    throw new Error(`RedisCouponCampaignLockGateway is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'implements RewardFollowUpRecorder',
  'ApplicationEventPublisher',
  'recordRewardIssued(memberId, rewardType)',
  'publishEvent(new RewardFollowUpRequestedEvent(memberId, rewardType))',
  'LOCAL_FAKE',
]) {
  if (!springRewardFollowUpRecorder.includes(fragment)) {
    throw new Error(`SpringRewardFollowUpRecorder is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  '@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)',
  '@Transactional(propagation = Propagation.REQUIRES_NEW)',
  'recordFakeAfterCommitNotification(event.memberId(), event.rewardType())',
]) {
  if (!rewardFollowUpOutboxListener.includes(fragment)) {
    throw new Error(`RewardFollowUpOutboxListener is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'extends FirstLoginRewardService',
  '@Transactional',
  'public FirstLoginRewardDecision issue(FirstLoginRewardCommand command)',
  'return super.issue(command);',
]) {
  if (!transactionalFirstLoginRewardService.includes(fragment)) {
    throw new Error(`TransactionalFirstLoginRewardService is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'public record RewardFollowUpRequestedEvent(',
  'long memberId',
  'RewardType rewardType',
]) {
  if (!rewardFollowUpRequestedEvent.includes(fragment)) {
    throw new Error(`RewardFollowUpRequestedEvent is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  '@Configuration',
  'public static final String COMMAND_QUEUE = "coupon-campaign-issue.commands";',
  'public static final String DLQ_QUEUE = "coupon-campaign-issue.dlq";',
  'QueueBuilder.durable(COMMAND_QUEUE)',
  'QueueBuilder.durable(DLQ_QUEUE)',
  'public Queue couponCampaignIssueCommandQueue()',
  'public Queue couponCampaignIssueDlqQueue()',
]) {
  if (!couponCampaignRabbitMqConfiguration.includes(fragment)) {
    throw new Error(`CouponCampaignRabbitMqConfiguration is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'public record CouponCampaignRabbitMqCommand(',
  'implements Serializable',
  'UUID operationId',
  'String messageId',
  'long campaignId',
  'long memberId',
  'String idempotencyKey',
  'long acceptedAtEpochMs',
  'int attempt',
  'int transientFailureBudget',
  'boolean dlq',
  'CouponCampaignRabbitMqCommand retriedCopy()',
]) {
  if (!couponCampaignRabbitMqCommand.includes(fragment)) {
    throw new Error(`CouponCampaignRabbitMqCommand is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'RabbitTemplate',
  'convertAndSend(',
  'CouponCampaignRabbitMqConfiguration.COMMAND_QUEUE',
  'CouponCampaignRabbitMqConfiguration.DLQ_QUEUE',
  'void publishDlq(CouponCampaignRabbitMqCommand command)',
]) {
  if (!couponCampaignRabbitMqPublisher.includes(fragment)) {
    throw new Error(`CouponCampaignRabbitMqPublisher is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'ConcurrentHashMap<UUID,',
  'void start(UUID operationId, int expectedCount)',
  'void recordAccepted(CouponCampaignRabbitMqCommand command)',
  'void recordCompleted(CouponCampaignRabbitMqCommand command, CouponCampaignDecision decision)',
  'RabbitMqRunSnapshot awaitCompletion(UUID operationId',
]) {
  if (!couponCampaignRabbitMqRunTracker.includes(fragment)) {
    throw new Error(`CouponCampaignRabbitMqRunTracker is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  '@RabbitListener(queues = CouponCampaignRabbitMqConfiguration.COMMAND_QUEUE, concurrency = "1")',
  'CouponCampaignService couponCampaignService',
  'StrategyType.RABBITMQ_DB_GUARD',
  'command.transientFailureBudget() > 0',
  'publisher.publish(command.retriedCopy());',
  'command.dlq()',
  'publisher.publishDlq(command);',
  'tracker.recordCompleted(command, decision);',
]) {
  if (!couponCampaignRabbitMqWorker.includes(fragment)) {
    throw new Error(`CouponCampaignRabbitMqWorker is missing required fragment: ${fragment}`);
  }
}

for (const fragment of [
  'implements CouponCampaignScenarioExecution',
  'StrategyType.RABBITMQ_DB_GUARD',
  'publisher.publish(command);',
  'tracker.awaitCompletion(',
  'operationId,',
  'reports.save(report)',
  'queueEvents.recordAccepted(runId',
  'queueEvents.recordCompleted(runId',
  'queueEvents.recordRetried(runId',
  'queueEvents.recordDlq(runId',
  'new ScenarioMetric(ScenarioMetricName.RABBITMQ_ACCEPTED_LATENCY_MS,',
  'new ScenarioMetric(ScenarioMetricName.RABBITMQ_COMPLETION_LATENCY_MS,',
]) {
  if (!rabbitMqCouponCampaignScenarioExecutor.includes(fragment)) {
    throw new Error(`RabbitMqCouponCampaignScenarioExecutor is missing required fragment: ${fragment}`);
  }
}

for (const forbidden of ['kafka', 'lock:member']) {
  if (
    couponCampaignRabbitMqConfiguration.includes(forbidden)
    || couponCampaignRabbitMqCommand.includes(forbidden)
    || couponCampaignRabbitMqPublisher.includes(forbidden)
    || couponCampaignRabbitMqRunTracker.includes(forbidden)
    || couponCampaignRabbitMqWorker.includes(forbidden)
    || rabbitMqCouponCampaignScenarioExecutor.includes(forbidden)
  ) {
    throw new Error(`RabbitMQ worker surface must not include out-of-scope fragment: ${forbidden}`);
  }
}
