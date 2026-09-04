package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.entity.BusinessLockEntity;
import cn.nihility.rbac.workflow.mapper.BusinessLockMapper;
import cn.nihility.rbac.workflow.service.BusinessLockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务活动申请锁服务实现：不使用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 等厂商专属 upsert
 * 写法（design.md 第9节明确禁止）。
 * <p>
 * 加锁顺序刻意先尝试 {@code INSERT}、失败（唯一键冲突）后才对已存在行加
 * {@code SELECT ... FOR UPDATE}，而不是反过来"先 {@code SELECT ... FOR UPDATE} 判断不存在再
 * INSERT"——这是本轮真实并发测试
 * （{@code BusinessLockServiceImplTest#acquire_concurrentCalls_onlyOneShouldSucceed}）暴露出的
 * 真实问题：对一个尚不存在的键做 {@code SELECT ... FOR UPDATE} 时，MySQL InnoDB 会在该键的
 * 间隙上加"间隙锁"，间隙锁彼此不互斥（两个并发事务可以同时持有同一间隙的锁），但随后两边各自
 * 尝试 {@code INSERT} 都需要获取"插入意向锁"并等待对方的间隙锁释放，形成对称等待，被数据库
 * 判定为真实死锁（{@code Deadlock found when trying to get lock}），其中一个事务会被强制回滚
 * ——这不是理论推演，是本类早期实现（先 {@code SELECT ... FOR UPDATE} 再按需 INSERT）在真实
 * MySQL 5.7 并发测试下必现的问题。改为 INSERT 优先后，两个并发事务对同一全新键的 INSERT 只会
 * 是"一个成功、一个因唯一键冲突短暂等待后报错"的正常竞争，不会出现对称的间隙锁互相等待。
 * {@link DuplicateKeyException} 分支命中后才对已经确定存在的行做
 * {@code SELECT ... FOR UPDATE}，此时目标行是真实存在的记录，不再有间隙锁参与，只是普通的
 * 行锁排队，不会重现上述死锁模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessLockServiceImpl implements BusinessLockService {

    /** 提示信息：目标已有进行中的审批。 */
    private static final String CONFLICT_MESSAGE = "该目标已有进行中的审批";

    /** 业务活动申请锁数据访问接口。 */
    private final BusinessLockMapper businessLockMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void acquire(String bizType, String targetKey, Long requestId, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        BusinessLockEntity row = BusinessLockEntity.builder()
                .bizType(bizType)
                .targetKey(targetKey)
                .activeRequestId(requestId)
                .revision(1L)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build();
        try {
            businessLockMapper.insert(row);
            return;
        } catch (DuplicateKeyException ex) {
            log.info("业务活动锁行 {}/{} 已存在，按已存在行处理（并发新建冲突或历史行复用）",
                    bizType, targetKey);
        }

        BusinessLockEntity existing = selectForUpdate(bizType, targetKey);
        if (existing == null) {
            // 理论上不会发生：刚刚触发唯一键冲突说明该 (bizType, targetKey) 必然已存在于数据库。
            throw new BusinessException("业务活动锁状态异常，请重试");
        }
        occupyExisting(existing, bizType, targetKey, requestId, operatorText, now);
    }

    /**
     * 复用一条已存在的空闲锁行；若已被占用则拒绝。
     */
    private void occupyExisting(
            BusinessLockEntity existing,
            String bizType,
            String targetKey,
            Long requestId,
            String operatorText,
            LocalDateTime now) {
        if (existing.getActiveRequestId() != null) {
            throw new BusinessException(CONFLICT_MESSAGE);
        }
        int updated = businessLockMapper.update(null, new LambdaUpdateWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, bizType)
                .eq(BusinessLockEntity::getTargetKey, targetKey)
                .isNull(BusinessLockEntity::getActiveRequestId)
                .set(BusinessLockEntity::getActiveRequestId, requestId)
                .set(BusinessLockEntity::getRevision, existing.getRevision() + 1)
                .set(BusinessLockEntity::getUpdateBy, operatorText)
                .set(BusinessLockEntity::getUpdateTime, now));
        if (updated != 1) {
            // 已持有行锁的前提下理论上不会发生，保留兜底防御。
            throw new BusinessException(CONFLICT_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void release(String bizType, String targetKey, Long requestId, Long operatorId) {
        BusinessLockEntity existing = selectForUpdate(bizType, targetKey);
        if (existing == null || !Objects.equals(existing.getActiveRequestId(), requestId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        businessLockMapper.update(null, new LambdaUpdateWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, bizType)
                .eq(BusinessLockEntity::getTargetKey, targetKey)
                .set(BusinessLockEntity::getActiveRequestId, null)
                .set(BusinessLockEntity::getRevision, existing.getRevision() + 1)
                .set(BusinessLockEntity::getUpdateBy, operatorText)
                .set(BusinessLockEntity::getUpdateTime, now));
    }

    /**
     * 对 {@code (bizType, targetKey)} 加 {@code SELECT ... FOR UPDATE} 行锁并查询，锁行不
     * 存在时返回 {@code null}。调用方须已通过其他方式（如刚触发的唯一键冲突、或
     * {@link #release} 的释放场景）确认目标行大概率存在，避免对不存在的键做加锁读取——那正是
     * 触发 InnoDB 间隙锁死锁的根源（见类注释）。
     */
    private BusinessLockEntity selectForUpdate(String bizType, String targetKey) {
        return businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, bizType)
                .eq(BusinessLockEntity::getTargetKey, targetKey)
                .last("FOR UPDATE"));
    }
}
