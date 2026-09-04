package cn.nihility.rbac.workflow.dslv2.binding;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 业务绑定确定性解析服务：精确组织 → 最近祖先组织 → 全局，同层同类型唯一，找不到拒绝发起，
 * 不偷偷用默认 key（production-approval-lifecycle change design.md Decision 4）。
 */
@Service
@RequiredArgsConstructor
public class ProcessBindingResolutionService {

    /** {@code scopeType=GLOBAL} 时固定使用的 {@code scopeId} 哨兵值。 */
    public static final long GLOBAL_SCOPE_ID = 0L;

    /** 业务绑定数据访问接口。 */
    private final ProcessBindingMapper processBindingMapper;

    /** 组织数据访问接口，用于解析组织祖先路径。 */
    private final OrgMapper orgMapper;

    /** 流程定义数据访问接口，校验命中绑定指向的版本是否仍处于已发布状态。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 流程模型数据访问接口，校验模型是否接受新发起（{@code enabled}）。 */
    private final ProcessModelMapper processModelMapper;

    /**
     * 按 {@code (bizType, operationType, 目标组织)} 解析出唯一命中的、已启用的业务绑定：
     * 依次尝试目标组织本身、其祖先组织（由近到远）、最后是全局绑定；均未命中或命中的绑定
     * 未启用时拒绝，不返回任何默认/兜底值。不加锁，供只读查询（如列表展示）使用；实际发起
     * 流程请改用 {@link #resolveForStart}。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param orgId         目标组织 id，可为空（为空时直接尝试全局绑定）
     * @return 命中的启用状态业务绑定
     * @throws BusinessException 未找到任何已启用的匹配绑定
     */
    public ProcessBindingEntity resolve(String bizType, String operationType, Long orgId) {
        return doResolve(bizType, operationType, orgId, false);
    }

    /**
     * 与 {@link #resolve} 语义一致，但对命中的绑定行加 {@code SELECT ... FOR UPDATE} 行锁，
     * 供发起流程时在同一事务内防止绑定切换（{@link WorkflowProcessBindingService#switchDefinition}
     * 与本方法采用相同的锁顺序——先锁绑定行）与并发发起读到不一致的 {@code definitionId}
     * （design.md Decision 4"启动时事务内读取并锁定所选绑定"）。必须在已有事务内调用。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param orgId         目标组织 id，可为空
     * @return 命中并已加锁的启用状态业务绑定
     * @throws BusinessException 未找到任何已启用的匹配绑定
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ProcessBindingEntity resolveForUpdate(String bizType, String operationType, Long orgId) {
        return doResolve(bizType, operationType, orgId, true);
    }

    /**
     * 供实际发起流程使用的一站式解析：加锁解析绑定 + 校验绑定指向的流程定义仍处于已发布
     * 状态 + 校验所属流程模型接受新发起（{@code tab_wf_process_model.enabled}）+ 拒绝
     * {@code executionMode=RELIABLE_ASYNC}（本轮未实现可靠异步执行器，见
     * {@link ExecutionMode} 注释）。必须在已有事务内调用，调用方（
     * {@code ApprovalProcessServiceImpl.start}）负责保证本方法与后续
     * {@code WorkflowService.start} 处于同一事务边界，让绑定行锁一直持有到流程实例真正
     * 创建完成。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param orgId         目标组织 id，可为空
     * @return 解析结果：命中的绑定 + 其指向的已发布流程定义
     * @throws BusinessException 未命中任何启用绑定、绑定指向的版本已下线、所属模型不接受新
     *                           发起，或绑定执行模式为尚未实现的 {@code RELIABLE_ASYNC}
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ResolvedProcessBinding resolveForStart(String bizType, String operationType, Long orgId) {
        ProcessBindingEntity binding = resolveForUpdate(bizType, operationType, orgId);
        ProcessDefinitionEntity definition = requirePublishedDefinition(binding.getDefinitionId());
        requireEnabledModel(definition.getProcessModelId());
        if (ExecutionMode.RELIABLE_ASYNC.equals(binding.getExecutionMode())) {
            throw new BusinessException("可靠异步执行尚未实现，请使用 LEGACY_SYNC 模式绑定");
        }
        return new ResolvedProcessBinding(binding, definition);
    }

    /**
     * {@link #resolve}/{@link #resolveForUpdate} 共用的三层回退查询逻辑，仅最终 SQL 是否携带
     * {@code FOR UPDATE} 不同，避免重复整段回退代码。
     */
    private ProcessBindingEntity doResolve(String bizType, String operationType, Long orgId, boolean forUpdate) {
        for (Long scopeId : candidateScopeIds(orgId)) {
            ProcessBindingEntity binding = findEnabled(bizType, operationType, "ORG", scopeId, forUpdate);
            if (binding != null) {
                return binding;
            }
        }
        ProcessBindingEntity global = findEnabled(bizType, operationType, "GLOBAL", GLOBAL_SCOPE_ID, forUpdate);
        if (global != null) {
            return global;
        }
        throw new BusinessException("业务类型 " + bizType + " 操作 " + operationType
                + " 未配置任何已启用的流程绑定（精确组织/祖先组织/全局均未命中），拒绝发起");
    }

    /**
     * 按由近到远的顺序列出目标组织自身及其全部祖先组织 id。
     */
    private List<Long> candidateScopeIds(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        OrgEntity org = orgMapper.selectById(orgId);
        if (org == null || !StringUtils.hasText(org.getOrgPath())) {
            return List.of(orgId);
        }
        List<Long> pathIds = Arrays.stream(org.getOrgPath().split("/"))
                .filter(StringUtils::hasText)
                .map(text -> {
                    try {
                        return Long.valueOf(text);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Long> candidates = new ArrayList<>(pathIds);
        // orgPath 按根到叶顺序存储，含自身；由近到远即反转后的顺序。若路径解析失败或不含
        // 自身，兜底仅返回自身，不假装解析出了不存在的祖先。
        java.util.Collections.reverse(candidates);
        if (candidates.isEmpty() || !candidates.get(0).equals(orgId)) {
            candidates = new ArrayList<>();
            candidates.add(orgId);
        }
        return candidates;
    }

    /**
     * 查询指定维度的启用状态绑定，同维度理论上唯一（表唯一约束保证）；{@code forUpdate=true}
     * 时对命中行追加 {@code FOR UPDATE} 行锁。
     */
    private ProcessBindingEntity findEnabled(
            String bizType, String operationType, String scopeType, Long scopeId, boolean forUpdate) {
        LambdaQueryWrapper<ProcessBindingEntity> wrapper = new LambdaQueryWrapper<ProcessBindingEntity>()
                .eq(ProcessBindingEntity::getBizType, bizType)
                .eq(ProcessBindingEntity::getOperationType, operationType)
                .eq(ProcessBindingEntity::getScopeType, scopeType)
                .eq(ProcessBindingEntity::getScopeId, scopeId)
                .eq(ProcessBindingEntity::getEnabled, true);
        wrapper.last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return processBindingMapper.selectOne(wrapper);
    }

    /**
     * 校验绑定指向的流程定义存在且仍处于已发布状态：绑定可能指向历史版本（显式回滚场景），
     * 不能假定其等同于所属模型的"当前"版本，因此不复用
     * {@code WorkflowProcessModelServiceImpl} 里针对 {@code currentDefinitionId} 的校验，
     * 而是直接对绑定携带的 {@code definitionId} 校验。
     */
    private ProcessDefinitionEntity requirePublishedDefinition(Long definitionId) {
        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(definitionId);
        if (definition == null || !ProcessModelStatus.PUBLISHED.equals(definition.getStatus())) {
            throw new BusinessException("绑定指向的流程版本不存在或已下线，无法发起");
        }
        return definition;
    }

    /**
     * 校验流程模型接受新发起（{@code tab_wf_process_model.enabled}，
     * production-approval-lifecycle change tasks.md 4.6"模型级启停"）。
     */
    private void requireEnabledModel(Long modelId) {
        ProcessModelEntity model = processModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("流程模型不存在，无法发起");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new BusinessException("流程模型当前已停止接受新发起");
        }
    }
}
