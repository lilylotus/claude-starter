package cn.nihility.rbac.workflow.dslv2.binding;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    /**
     * 按 {@code (bizType, operationType, 目标组织)} 解析出唯一命中的、已启用的业务绑定：
     * 依次尝试目标组织本身、其祖先组织（由近到远）、最后是全局绑定；均未命中或命中的绑定
     * 未启用时拒绝，不返回任何默认/兜底值。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param orgId         目标组织 id，可为空（为空时直接尝试全局绑定）
     * @return 命中的启用状态业务绑定
     * @throws BusinessException 未找到任何已启用的匹配绑定
     */
    public ProcessBindingEntity resolve(String bizType, String operationType, Long orgId) {
        for (Long scopeId : candidateScopeIds(orgId)) {
            ProcessBindingEntity binding = findEnabled(bizType, operationType, "ORG", scopeId);
            if (binding != null) {
                return binding;
            }
        }
        ProcessBindingEntity global = findEnabled(bizType, operationType, "GLOBAL", GLOBAL_SCOPE_ID);
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
     * 查询指定维度的启用状态绑定，同维度理论上唯一（表唯一约束保证）。
     */
    private ProcessBindingEntity findEnabled(String bizType, String operationType, String scopeType, Long scopeId) {
        return processBindingMapper.selectOne(new LambdaQueryWrapper<ProcessBindingEntity>()
                .eq(ProcessBindingEntity::getBizType, bizType)
                .eq(ProcessBindingEntity::getOperationType, operationType)
                .eq(ProcessBindingEntity::getScopeType, scopeType)
                .eq(ProcessBindingEntity::getScopeId, scopeId)
                .eq(ProcessBindingEntity::getEnabled, true)
                .last("LIMIT 1"));
    }
}
