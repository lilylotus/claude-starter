package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.notify.support.NotifyCandidateResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 领域变更事件的真正处理逻辑："直接判定当前哪些应用是本次事件的候选应用（数据域启用+总
 * 开关开启+同步方式为通知+组织范围匹配），对每个匹配应用各自触发一次通知"，不再经过任何
 * 持久化中转（app-sync-drop-changelog change design.md Decision 6）。不依赖 Disruptor
 * API，供 {@link DomainChangeEventHandler}（Disruptor 消费者）与未来外部 MQ 消费者共同调用，
 * 切换消息载体时本类逻辑可直接复用。同时承担"组织/用户/任职变更后策略自动重新执行"这一
 * 独立副作用（close-sso-log-and-policy-gaps change design.md Decision 2），与通知候选逻辑
 * 各自独立 try/catch，互不影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainChangeEventProcessor {

    /** 触发策略自动重新执行的数据域：组织/用户/任职，任意操作类型均触发。 */
    private static final Set<String> POLICY_RE_EXECUTE_DATA_TYPES = Set.of(SyncDomain.ORG, SyncDomain.USER,
            SyncDomain.POSITION);

    /** 通知候选应用判定组件。 */
    private final NotifyCandidateResolver notifyCandidateResolver;

    /** 应用通知发送业务逻辑接口。 */
    private final AppNotifyService appNotifyService;

    /** 策略规则数据访问接口，查询全部当前启用状态的策略 id。 */
    private final PolicyMapper policyMapper;

    /** 策略规则手动执行业务逻辑接口，事件驱动场景下复用其全量重建能力。 */
    private final PolicyExecutionService policyExecutionService;

    /**
     * 处理一条领域变更事件：判定当前匹配的候选应用列表，逐个应用触发"该应用当前是否为
     * NOTIFY 模式"的通知判断，单个应用通知异常不影响其余应用（沿用既有 catch 风格）；随后
     * 独立触发"组织/用户/任职变更后策略自动重新执行"副作用。两段逻辑各自整体 catch 所有
     * 异常，避免消费者线程因单条事件处理异常而中断，导致 RingBuffer 阻塞、后续事件无法消费。
     *
     * @param event 领域变更事件
     */
    public void process(DomainChangeEvent event) {
        try {
            List<Long> matchedAppRefIds = notifyCandidateResolver.resolveCandidateAppRefIds(event);
            for (Long appRefId : matchedAppRefIds) {
                try {
                    appNotifyService.notifyIfConfigured(event, appRefId);
                } catch (Exception e) {
                    log.warn("向应用[{}]触发变更通知失败，跳过并继续处理其余应用：dataType={}, bizId={}", appRefId,
                            event.getDataType(), event.getBizId(), e);
                }
            }
        } catch (Exception e) {
            log.error("处理领域变更事件失败：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
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
