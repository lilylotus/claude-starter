package cn.nihility.rbac.workflow.outbox.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.outbox.constant.EventConsumeResult;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus;
import cn.nihility.rbac.workflow.outbox.consumer.RecordingOutboxEventConsumer;
import cn.nihility.rbac.workflow.outbox.consumer.StubBusinessExecutionEntity;
import cn.nihility.rbac.workflow.outbox.consumer.StubBusinessExecutionMapper;
import cn.nihility.rbac.workflow.outbox.entity.EventConsumeEntity;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.EventConsumeMapper;
import cn.nihility.rbac.workflow.outbox.mapper.OutboxEventMapper;
import cn.nihility.rbac.workflow.outbox.service.EventConsumeService;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link OutboxEventConsumptionCoordinator} 真实数据库端到端集成测试
 * （production-approval-lifecycle change design.md 第9/10节，tasks.md 7.2）。不使用测试专用
 * 回滚事务、不 mock DB 层：{@link OutboxEventService}/{@link EventConsumeService} 各方法自身已
 * 声明 {@code @Transactional(REQUIRED)}，测试直接调用注入的代理 Bean 即可各自拥有独立的真实
 * 物理事务，理由与 {@code OutboxEventServiceImplIntegrationTest}/
 * {@code OutboxEventServiceImplConcurrencyIntegrationTest} 一致。
 * <p>
 * 没有任何真实业务消费者可用（7.3/7.4 尚未实现），本类全程使用测试专用的
 * {@link RecordingOutboxEventConsumer} 桩消费者验证编排机制本身，不伪造真实业务执行逻辑；
 * {@link OutboxEventConsumptionCoordinator} 本身虽然是生产 {@code @Component}，但本类不通过
 * Spring 容器自动装配它（容器里此时没有任何真实消费者 bean），而是每个测试方法手动 {@code new}
 * 一份、显式传入本次场景需要的桩消费者列表，避免测试专用的桩消费者被注册为 Spring bean 泄漏进
 * 生产上下文。
 */
@SpringBootTest
class OutboxEventConsumptionCoordinatorIntegrationTest {

    /** 测试专用事件类型，避免与真实业务事件混淆。 */
    private static final String TEST_EVENT_TYPE = "TEST_CONSUME_EVENT_TYPE";

    /** Outbox 事件生产/领取服务。 */
    @Autowired
    private OutboxEventService outboxEventService;

    /** Outbox 事件数据访问接口，用于测试断言、清理与人为制造租约过期的测试夹具操作。 */
    @Autowired
    private OutboxEventMapper outboxEventMapper;

    /** 单消费者维度"去重 + 消费"原子服务。 */
    @Autowired
    private EventConsumeService eventConsumeService;

    /** Outbox 事件消费去重记录数据访问接口，用于测试断言。 */
    @Autowired
    private EventConsumeMapper eventConsumeMapper;

    /** 业务结果代表数据的测试专用数据访问接口。 */
    @Autowired
    private StubBusinessExecutionMapper stubBusinessExecutionMapper;

    /** 本方法使用过的 eventId，测试结束后据此清理三张表的关联行，避免在共享开发库残留。 */
    private final List<String> eventIdsToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String eventId : eventIdsToCleanup) {
            OutboxEventEntity row = outboxEventMapper.selectOne(
                    new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventId));
            if (row != null) {
                stubBusinessExecutionMapper.delete(new LambdaQueryWrapper<StubBusinessExecutionEntity>()
                        .eq(StubBusinessExecutionEntity::getRequestId, row.getId()));
            }
            eventConsumeMapper.delete(
                    new LambdaQueryWrapper<EventConsumeEntity>().eq(EventConsumeEntity::getEventId, eventId));
            outboxEventMapper.delete(
                    new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventId));
        }
    }

    /** 首次消费：消费者被真正调用一次，消费标记与业务结果均落库，事件最终标记 SUCCEEDED。 */
    @Test
    void consume_shouldInvokeConsumer_andMarkEventSucceeded_onFirstConsumption() {
        String eventId = "IT-CONSUME-FIRST-" + UUID.randomUUID();
        eventIdsToCleanup.add(eventId);
        OutboxEventEntity claimed = publishAndClaim(eventId);
        RecordingOutboxEventConsumer consumer =
                new RecordingOutboxEventConsumer("TEST_CONSUMER_A", TEST_EVENT_TYPE, stubBusinessExecutionMapper);
        OutboxEventConsumptionCoordinator coordinator =
                new OutboxEventConsumptionCoordinator(List.of(consumer), eventConsumeService, outboxEventService);

        coordinator.consume(claimed);

        assertThat(consumer.invocationCount()).isEqualTo(1);
        EventConsumeEntity consumeRow = eventConsumeMapper.selectOne(new LambdaQueryWrapper<EventConsumeEntity>()
                .eq(EventConsumeEntity::getEventId, eventId)
                .eq(EventConsumeEntity::getConsumerCode, "TEST_CONSUMER_A"));
        assertThat(consumeRow).isNotNull();
        assertThat(consumeRow.getResult()).isEqualTo(EventConsumeResult.SUCCEEDED);
        List<StubBusinessExecutionEntity> executionRows = stubBusinessExecutionMapper.selectList(
                new LambdaQueryWrapper<StubBusinessExecutionEntity>()
                        .eq(StubBusinessExecutionEntity::getRequestId, claimed.getId()));
        assertThat(executionRows).as("consume() 应写入代表业务结果的一条记录").hasSize(1);
        OutboxEventEntity row = outboxEventMapper.selectById(claimed.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
    }

    /**
     * 重复消费同一 (eventId, consumerCode)：模拟"崩溃后重放"，直接第二次调用编排逻辑处理同一
     * 事件同一消费者，消费者的业务逻辑不应被再次真正执行，但整体流程仍然正常完成、不报错。
     */
    @Test
    void consume_shouldSkipConsumerInvocation_onDuplicateReplay() {
        String eventId = "IT-CONSUME-DUP-" + UUID.randomUUID();
        eventIdsToCleanup.add(eventId);
        OutboxEventEntity claimed = publishAndClaim(eventId);
        RecordingOutboxEventConsumer consumer =
                new RecordingOutboxEventConsumer("TEST_CONSUMER_B", TEST_EVENT_TYPE, stubBusinessExecutionMapper);
        OutboxEventConsumptionCoordinator coordinator =
                new OutboxEventConsumptionCoordinator(List.of(consumer), eventConsumeService, outboxEventService);

        coordinator.consume(claimed);
        coordinator.consume(claimed);

        assertThat(consumer.invocationCount()).as("消费唯一键去重应阻止业务逻辑被再次真正执行").isEqualTo(1);
        List<EventConsumeEntity> consumeRows = eventConsumeMapper.selectList(new LambdaQueryWrapper<
                        EventConsumeEntity>()
                .eq(EventConsumeEntity::getEventId, eventId)
                .eq(EventConsumeEntity::getConsumerCode, "TEST_CONSUMER_B"));
        assertThat(consumeRows).hasSize(1);
        OutboxEventEntity row = outboxEventMapper.selectById(claimed.getId());
        assertThat(row.getStatus()).as("重放不应把已成功的事件状态搅乱").isEqualTo(OutboxEventStatus.SUCCEEDED);
    }

    /**
     * 消费者抛异常：消费标记未留下记录（事务回滚），outbox 事件被 markFailed 走向退避重试。
     */
    @Test
    void consume_shouldRollbackConsumeMark_andRetryEvent_whenConsumerThrows() {
        String eventId = "IT-CONSUME-FAIL-" + UUID.randomUUID();
        eventIdsToCleanup.add(eventId);
        OutboxEventEntity claimed = publishAndClaim(eventId);
        RecordingOutboxEventConsumer consumer =
                new RecordingOutboxEventConsumer("TEST_CONSUMER_C", TEST_EVENT_TYPE, stubBusinessExecutionMapper);
        consumer.setFailing(true);
        OutboxEventConsumptionCoordinator coordinator =
                new OutboxEventConsumptionCoordinator(List.of(consumer), eventConsumeService, outboxEventService);

        coordinator.consume(claimed);

        assertThat(consumer.invocationCount()).isEqualTo(1);
        List<EventConsumeEntity> consumeRows = eventConsumeMapper.selectList(new LambdaQueryWrapper<
                        EventConsumeEntity>()
                .eq(EventConsumeEntity::getEventId, eventId)
                .eq(EventConsumeEntity::getConsumerCode, "TEST_CONSUMER_C"));
        assertThat(consumeRows).as("消费者抛异常应回滚消费标记 INSERT，不留下记录").isEmpty();
        List<StubBusinessExecutionEntity> executionRows = stubBusinessExecutionMapper.selectList(
                new LambdaQueryWrapper<StubBusinessExecutionEntity>()
                        .eq(StubBusinessExecutionEntity::getRequestId, claimed.getId()));
        assertThat(executionRows).as("消费者抛异常前写入的业务结果也应随事务一并回滚").isEmpty();
        OutboxEventEntity row = outboxEventMapper.selectById(claimed.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getNextRetryTime()).isAfter(LocalDateTime.now());
    }

    /**
     * fencing 端到端：worker A 领取事件后持有 token A，人为让其租约过期，worker B 重新领取拿到
     * token B 并真正完成消费、标记 SUCCEEDED；此后 A 才迟来地跑完自己的消费编排流程并尝试用
     * token A 收尾，断言 A 的收尾不会把已经是 SUCCEEDED 的事件状态搅乱，也不会导致消费者业务
     * 逻辑被重复执行（消费唯一键去重在两个不同 token 的调用之间同样生效）。
     */
    @Test
    void consume_shouldNotClobberAlreadySucceededEvent_whenLeaseReclaimedByAnotherWorker() {
        String eventId = "IT-CONSUME-FENCE-" + UUID.randomUUID();
        eventIdsToCleanup.add(eventId);
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        OutboxEventEntity claimedByA = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();

        // 测试夹具操作：直接把租约到期时间改到过去，模拟"系统认为其租约已过期"（与
        // OutboxEventServiceImplConcurrencyIntegrationTest 同样的手段），不经过 CAS。
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, claimedByA.getId())
                .set(OutboxEventEntity::getLeaseUntil, LocalDateTime.now().minusSeconds(2).withNano(0)));

        OutboxEventEntity claimedByB = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();
        assertThat(claimedByB.getLeaseToken()).isNotEqualTo(claimedByA.getLeaseToken());

        RecordingOutboxEventConsumer consumer =
                new RecordingOutboxEventConsumer("TEST_CONSUMER_D", TEST_EVENT_TYPE, stubBusinessExecutionMapper);
        OutboxEventConsumptionCoordinator coordinator =
                new OutboxEventConsumptionCoordinator(List.of(consumer), eventConsumeService, outboxEventService);

        // B 先完整走完消费编排，真正执行消费并把事件标记为终态成功。
        coordinator.consume(claimedByB);
        assertThat(consumer.invocationCount()).isEqualTo(1);
        assertThat(outboxEventMapper.selectById(claimedByA.getId()).getStatus())
                .isEqualTo(OutboxEventStatus.SUCCEEDED);

        // A 迟来地用自己手里的旧快照（旧 token）跑完消费编排：消费唯一键去重应命中（B 已消费
        // 过），consumer 不应被再次真正调用；随后 A 用旧 token 收尾时 markSucceeded 的 CAS
        // 应因事件已不是 LEASED 状态而失效，不能把 B 的处理结果搅乱。
        coordinator.consume(claimedByA);

        assertThat(consumer.invocationCount()).as("A 的迟来重放不应导致消费者再次真正执行").isEqualTo(1);
        OutboxEventEntity finalRow = outboxEventMapper.selectById(claimedByA.getId());
        assertThat(finalRow.getStatus())
                .as("A 用旧 token 的收尾不应把已 SUCCEEDED 的事件状态搅乱回 PENDING/FAILED")
                .isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(finalRow.getLeaseToken()).isNull();
        List<EventConsumeEntity> consumeRows = eventConsumeMapper.selectList(
                new LambdaQueryWrapper<EventConsumeEntity>().eq(EventConsumeEntity::getEventId, eventId));
        assertThat(consumeRows).hasSize(1);
    }

    /** 多个消费者其中一个没有 supports 命中：断言未命中的消费者完全不产生记录、不被调用。 */
    @Test
    void consume_shouldSkipConsumer_whenEventTypeNotSupported() {
        String eventId = "IT-CONSUME-UNSUPPORTED-" + UUID.randomUUID();
        eventIdsToCleanup.add(eventId);
        OutboxEventEntity claimed = publishAndClaim(eventId);
        RecordingOutboxEventConsumer matching =
                new RecordingOutboxEventConsumer("TEST_CONSUMER_E1", TEST_EVENT_TYPE, stubBusinessExecutionMapper);
        RecordingOutboxEventConsumer nonMatching = new RecordingOutboxEventConsumer(
                "TEST_CONSUMER_E2", "OTHER_EVENT_TYPE", stubBusinessExecutionMapper);
        OutboxEventConsumptionCoordinator coordinator = new OutboxEventConsumptionCoordinator(
                List.of(matching, nonMatching), eventConsumeService, outboxEventService);

        coordinator.consume(claimed);

        assertThat(matching.invocationCount()).isEqualTo(1);
        assertThat(nonMatching.invocationCount()).as("不支持该事件类型的消费者不应被调用").isZero();
        List<EventConsumeEntity> nonMatchingRows = eventConsumeMapper.selectList(new LambdaQueryWrapper<
                        EventConsumeEntity>()
                .eq(EventConsumeEntity::getEventId, eventId)
                .eq(EventConsumeEntity::getConsumerCode, "TEST_CONSUMER_E2"));
        assertThat(nonMatchingRows).isEmpty();
        OutboxEventEntity row = outboxEventMapper.selectById(claimed.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
    }

    /** 发布一条测试事件并立即领取，断言恰好命中一条并返回。 */
    private OutboxEventEntity publishAndClaim(String eventId) {
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        return outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();
    }
}
