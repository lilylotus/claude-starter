package cn.nihility.rbac.workflow.outbox.consumer;

import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试专用桩消费者（production-approval-lifecycle change tasks.md 7.2）：只在测试代码里使用，
 * 不标注 {@code @Component}，不会被生产 Spring 容器扫描到——7.3/7.4 的真实业务消费者尚未实现，
 * 本轮只能用这个桩验证"消费编排"这层通用基础设施本身，不伪造 ORG/USER 等真实业务执行逻辑。
 * {@link #consume} 里对 {@code tab_wf_business_execution} 表做一次真实的、可断言的写操作，
 * 代表消费产生的"业务结果"（该表已由 7.1 同一迁移脚本建好且无外键约束，测试写入不会影响其他
 * 数据）；{@link #invocationCount()} 用于断言消费唯一键去重是否真正阻止了业务逻辑被重复执行。
 */
public class RecordingOutboxEventConsumer implements OutboxEventConsumer {

    /** 消费者唯一标识。 */
    private final String consumerCode;

    /** 本消费者关心的事件类型。 */
    private final String supportedEventType;

    /** 用于写入"业务结果"代表数据的测试专用数据访问接口。 */
    private final StubBusinessExecutionMapper stubBusinessExecutionMapper;

    /** {@link #consume} 被真正调用的次数，供测试断言去重是否生效。 */
    private final AtomicInteger invocationCount = new AtomicInteger();

    /** 是否在本次消费时模拟失败（抛异常），默认 {@code false}。 */
    private volatile boolean failing;

    /**
     * @param consumerCode                消费者唯一标识
     * @param supportedEventType          本消费者关心的事件类型
     * @param stubBusinessExecutionMapper 用于写入"业务结果"代表数据的测试专用数据访问接口
     */
    public RecordingOutboxEventConsumer(
            String consumerCode, String supportedEventType, StubBusinessExecutionMapper stubBusinessExecutionMapper) {
        this.consumerCode = consumerCode;
        this.supportedEventType = supportedEventType;
        this.stubBusinessExecutionMapper = stubBusinessExecutionMapper;
    }

    @Override
    public String consumerCode() {
        return consumerCode;
    }

    @Override
    public boolean supports(String eventType) {
        return supportedEventType.equals(eventType);
    }

    @Override
    public void consume(OutboxEventEntity event) {
        invocationCount.incrementAndGet();
        if (failing) {
            throw new RuntimeException("模拟消费者[" + consumerCode + "]消费失败，用于测试事务回滚与退避重试");
        }
        LocalDateTime now = LocalDateTime.now();
        stubBusinessExecutionMapper.insert(StubBusinessExecutionEntity.builder()
                .requestId(event.getId())
                .attemptNo(1)
                .leaseToken(event.getLeaseToken())
                .executionStatus("SUCCEEDED")
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * @return {@link #consume} 被真正调用的次数
     */
    public int invocationCount() {
        return invocationCount.get();
    }

    /**
     * 设置本消费者下一次（及之后）消费时是否模拟失败。
     *
     * @param failing 是否模拟失败
     */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }
}
