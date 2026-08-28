package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.support.NotifySendCoordinator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 领域变更事件的真正处理逻辑：先调用 {@link DomainChangeRecorder#record}，在一个本地数据库
 * 事务内写入一条全局变更流水，并为全部候选应用（数据域启用+总开关开启+同步方式为通知+
 * 组织范围匹配）创建/复用一条 {@code PENDING} 通知任务，候选解析或任一任务插入失败均整体
 * 回滚（app-sync-changelog-pull change design.md Decision 6）；事务提交成功后，对每个新建
 * 的任务提交一次"即时发送优化"（异步、不阻塞本方法），实际发送与状态机流转由
 * {@link NotifySendCoordinator} 及独立的 {@code NotifyRetryScheduler} 负责，即使即时发送
 * 优化因进程崩溃等原因没有执行，任务已是 {@code PENDING} 落库状态，调度器的到期扫描仍能
 * 兜底捞到它。不依赖 Disruptor API，供 {@link DomainChangeEventHandler}（Disruptor 消费者）
 * 与未来外部 MQ 消费者共同调用，切换消息载体时本类逻辑可直接复用。同时承担
 * "组织/用户/任职变更后策略自动重新执行"这一独立副作用（close-sso-log-and-policy-gaps
 * change design.md Decision 2），与流水/通知逻辑各自独立 try/catch，互不影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainChangeEventProcessor {

    /** 触发策略自动重新执行的数据域：组织/用户/任职，任意操作类型均触发。 */
    private static final Set<String> POLICY_RE_EXECUTE_DATA_TYPES = Set.of(SyncDomain.ORG, SyncDomain.USER,
            SyncDomain.POSITION);

    /** 领域变更事件的落库编排组件："流水 + 全部候选应用 PENDING 通知任务"同事务落库。 */
    private final DomainChangeRecorder domainChangeRecorder;

    /** 通知任务"抢占 + 发送 + 状态流转"编排组件，负责即时发送优化。 */
    private final NotifySendCoordinator notifySendCoordinator;

    /** 策略规则数据访问接口，查询全部当前启用状态的策略 id。 */
    private final PolicyMapper policyMapper;

    /** 策略规则手动执行业务逻辑接口，事件驱动场景下复用其全量重建能力。 */
    private final PolicyExecutionService policyExecutionService;

    /**
     * 处理一条领域变更事件：先落库（流水 + 候选应用 PENDING 通知任务同事务），落库失败时
     * 记录 ERROR 日志并直接跳过通知分支；落库成功后，对每个新建任务提交一次即时发送优化，
     * 单个任务提交异常不影响其余任务（沿用既有 catch 风格）；随后独立触发
     * "组织/用户/任职变更后策略自动重新执行"副作用，不卷入落库的事务。
     *
     * @param event 领域变更事件
     */
    public void process(DomainChangeEvent event) {
        DomainChangeRecordResult result = null;
        try {
            result = domainChangeRecorder.record(event);
        } catch (Exception e) {
            log.error("写入全局变更流水与候选应用通知任务失败，跳过本次事件的通知分支：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
        }

        if (result != null) {
            for (AppNotifyRecordEntity task : result.tasks()) {
                try {
                    notifySendCoordinator.submitImmediateSend(task);
                } catch (Exception e) {
                    log.warn("提交应用[{}]变更通知即时发送任务失败，等待调度器下一轮到期扫描兜底：taskId={}, dataType={}, bizId={}",
                            task.getAppRefId(), task.getId(), event.getDataType(), event.getBizId(), e);
                }
            }
        }

        try {
            reExecutePoliciesIfNeeded(event);
        } catch (Exception e) {
            log.error("组织/用户/任职变更后策略自动重新执行失败：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
        }
    }

    /**
     * 当事件数据域属于组织/用户/任职时，对全部当前启用状态的策略各自重新执行一次，单条策略
     * 执行失败仅记录日志、不影响其余策略继续重算（close-sso-log-and-policy-gaps change
     * design.md Decision 2）。
     *
     * @param event 领域变更事件
     */
    private void reExecutePoliciesIfNeeded(DomainChangeEvent event) {
        if (!POLICY_RE_EXECUTE_DATA_TYPES.contains(event.getDataType())) {
            return;
        }
        List<PolicyEntity> enabledPolicies = policyMapper.selectList(new LambdaQueryWrapper<PolicyEntity>()
                .eq(PolicyEntity::getStatus, PolicyStatus.ENABLED));
        for (PolicyEntity policy : enabledPolicies) {
            try {
                // 不能调用 execute(Long)：该重载从 CurrentOperatorService 解析当前登录用户，
                // 而本方法运行在 Disruptor 消费者线程上，不处于任何 HTTP 请求上下文中，一定
                // 会抛 IllegalStateException 并被下面的 catch 悄悄吞掉，导致自动重新执行
                // 表面正常、实际每次都失败（close-sso-log-and-policy-gaps change design.md
                // Decision 6）。改用 execute(Long, String) 显式传入触发本次事件的操作人。
                policyExecutionService.execute(policy.getId(), event.getOperator());
            } catch (Exception e) {
                log.error("策略[{}]自动重新执行失败，跳过并继续处理其余策略：dataType={}, bizId={}", policy.getId(),
                        event.getDataType(), event.getBizId(), e);
            }
        }
    }
}
