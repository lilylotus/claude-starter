package cn.nihility.rbac.workflow.outbox.service.impl;

import cn.nihility.rbac.workflow.outbox.constant.EventConsumeResult;
import cn.nihility.rbac.workflow.outbox.consumer.OutboxEventConsumer;
import cn.nihility.rbac.workflow.outbox.entity.EventConsumeEntity;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.EventConsumeMapper;
import cn.nihility.rbac.workflow.outbox.service.EventConsumeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单消费者维度"消费唯一键去重 + 消费执行"原子服务实现（production-approval-lifecycle change
 * design.md 第9/10节，tasks.md 7.2）。
 * <p>
 * 加锁/判重顺序刻意先尝试 {@code INSERT}、命中唯一键冲突后才判定"已消费过"，而不是反过来
 * "先 {@code SELECT} 判断不存在再 {@code INSERT}"——对一个尚不存在的键做
 * {@code SELECT ... FOR UPDATE} 会在 MySQL InnoDB 下于该键的间隙加"间隙锁"，两个并发事务同时
 * 判定"不存在"后各自尝试 {@code INSERT} 会形成对称等待触发真实死锁，这是
 * {@code BusinessLockServiceImpl}/{@code IdempotencyServiceImpl} 已经在真实并发测试中验证过的
 * 问题，本类复用同一套安全写法规避。
 * <p>
 * {@code consumeOnce} 声明 {@link Propagation#REQUIRED}：不单独另开物理事务，插入消费标记与
 * 调用 {@link OutboxEventConsumer#consume} 处于同一个事务——{@code consume} 内部任何写操作
 * 都会自然加入这同一个事务（Spring 事务同步绑定当前线程连接），其抛出的异常会让整个事务（含
 * 刚插入的消费标记）一并回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventConsumeServiceImpl implements EventConsumeService {

    /** 消费标记落库场景下审计字段的固定操作人标识：多数调用发生在无登录用户上下文的后台流程。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** Outbox 事件消费去重记录数据访问接口。 */
    private final EventConsumeMapper eventConsumeMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean consumeOnce(OutboxEventEntity event, OutboxEventConsumer consumer) {
        LocalDateTime now = LocalDateTime.now();
        EventConsumeEntity record = EventConsumeEntity.builder()
                .eventId(event.getEventId())
                .consumerCode(consumer.consumerCode())
                .result(EventConsumeResult.SUCCEEDED)
                .processedTime(now)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
        try {
            eventConsumeMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.info("Outbox 事件[{}]已被消费者[{}]消费过，跳过本次消费逻辑（消费唯一键去重生效）",
                    event.getEventId(), consumer.consumerCode());
            return false;
        }
        consumer.consume(event);
        return true;
    }
}
