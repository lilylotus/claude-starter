package cn.nihility.rbac.workflow.dslv2.binding;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingRequest;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingVO;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 业务绑定生命周期管理服务：新建/切换版本/查询/列表（production-approval-lifecycle change
 * design.md Decision 4/12）。绑定保存 explicit definitionId、executionMode、revision、
 * enabled；全局绑定是管理员明确配置，不是引擎隐式回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowProcessBindingService {

    /** 业务绑定数据访问接口。 */
    private final ProcessBindingMapper processBindingMapper;

    /** 流程定义数据访问接口，校验绑定的 definitionId 有效且已发布。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /**
     * 查询业务绑定列表。
     *
     * @return 全部业务绑定
     */
    public List<ProcessBindingVO> listBindings() {
        return processBindingMapper.selectList(new LambdaQueryWrapper<ProcessBindingEntity>()
                        .orderByDesc(ProcessBindingEntity::getUpdateTime))
                .stream().map(this::toVO).toList();
    }

    /**
     * 查询单条业务绑定。
     *
     * @param bindingId 绑定 id
     * @return 业务绑定
     */
    public ProcessBindingVO getBinding(Long bindingId) {
        return toVO(requireBinding(bindingId));
    }

    /**
     * 新建业务绑定。
     *
     * @param request    请求体
     * @param operatorId 操作人 id
     * @return 新建的业务绑定
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ProcessBindingVO createBinding(ProcessBindingRequest request, Long operatorId) {
        ProcessDefinitionEntity definition = requirePublishedDefinition(request.getDefinitionId());
        long scopeId = "GLOBAL".equals(request.getScopeType())
                ? ProcessBindingResolutionService.GLOBAL_SCOPE_ID
                : requireScopeId(request);
        if (processBindingMapper.selectCount(dimensionQuery(request.getBizType(), request.getOperationType(),
                request.getScopeType(), scopeId)) > 0) {
            throw new BusinessException("该绑定维度已存在，请使用切换版本接口");
        }

        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        ProcessBindingEntity entity = ProcessBindingEntity.builder()
                .bizType(request.getBizType())
                .operationType(request.getOperationType())
                .scopeType(request.getScopeType())
                .scopeId(scopeId)
                .definitionId(definition.getId())
                .executionMode(StringUtils.hasText(request.getExecutionMode())
                        ? request.getExecutionMode() : ExecutionMode.LEGACY_SYNC)
                .revision(1L)
                .enabled(true)
                .createBy(operatorText).createTime(now).updateBy(operatorText).updateTime(now)
                .build();
        processBindingMapper.insert(entity);
        return toVO(entity);
    }

    /**
     * 切换业务绑定指向的流程定义版本（含显式回滚到验证过的旧 definitionId），启动时事务内
     * 读取并锁定所选绑定，与提交发起使用相同锁顺序（design.md Decision 4）。不拆分独立的
     * "回滚"接口——目标版本号小于当前绑定版本号即语义等价于回滚，仅在日志/更新备注中区分
     * 两种场景，供审计排查（production-approval-lifecycle change tasks.md 4.6"本轮不新增
     * 代码，只需确认现有方法确实可以把绑定切回任意历史 definitionId"）。
     *
     * @param bindingId  绑定 id
     * @param request    请求体，须携带 {@code expectedRevision}
     * @param operatorId 操作人 id
     * @return 更新后的业务绑定
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ProcessBindingVO switchDefinition(Long bindingId, ProcessBindingRequest request, Long operatorId) {
        if (request.getExpectedRevision() == null) {
            throw new BusinessException("切换绑定版本必须携带 expectedRevision");
        }
        ProcessDefinitionEntity definition = requirePublishedDefinition(request.getDefinitionId());
        ProcessBindingEntity entity = requireBinding(bindingId);
        ProcessDefinitionEntity previousDefinition = processDefinitionMapper.selectById(entity.getDefinitionId());
        if (!definition.getProcessModelId().equals(previousDefinition.getProcessModelId())) {
            throw new BusinessException("切换目标流程定义必须与当前绑定属于同一流程模型");
        }
        boolean isRollback = previousDefinition.getVersion() != null && definition.getVersion() != null
                && definition.getVersion() < previousDefinition.getVersion();
        log.info("业务绑定 {} 切换流程版本：{} -> {}（{}），操作人={}", bindingId, previousDefinition.getVersion(),
                definition.getVersion(), isRollback ? "显式回滚到历史版本" : "切换到更新版本", operatorId);

        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        int updated = processBindingMapper.update(null, new LambdaUpdateWrapper<ProcessBindingEntity>()
                .eq(ProcessBindingEntity::getId, bindingId)
                .eq(ProcessBindingEntity::getRevision, request.getExpectedRevision())
                .set(ProcessBindingEntity::getDefinitionId, definition.getId())
                .set(StringUtils.hasText(request.getExecutionMode()), ProcessBindingEntity::getExecutionMode, request.getExecutionMode())
                .set(ProcessBindingEntity::getRevision, request.getExpectedRevision() + 1)
                .set(ProcessBindingEntity::getUpdateBy, operatorText)
                .set(ProcessBindingEntity::getUpdateTime, now));
        if (updated == 0) {
            throw new BusinessException("绑定已被他人修改（revision 冲突），请刷新后重试");
        }
        return toVO(requireBinding(bindingId));
    }

    /**
     * 启停业务绑定（禁用后该维度拒绝新发起，不影响运行中实例）。
     *
     * @param bindingId 绑定 id
     * @param enabled   目标启用状态
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void setEnabled(Long bindingId, boolean enabled, Long operatorId) {
        requireBinding(bindingId);
        processBindingMapper.update(null, new LambdaUpdateWrapper<ProcessBindingEntity>()
                .eq(ProcessBindingEntity::getId, bindingId)
                .set(ProcessBindingEntity::getEnabled, enabled)
                .set(ProcessBindingEntity::getUpdateBy, operatorId == null ? null : operatorId.toString())
                .set(ProcessBindingEntity::getUpdateTime, LocalDateTime.now()));
    }

    private LambdaQueryWrapper<ProcessBindingEntity> dimensionQuery(
            String bizType, String operationType, String scopeType, Long scopeId) {
        return new LambdaQueryWrapper<ProcessBindingEntity>()
                .eq(ProcessBindingEntity::getBizType, bizType)
                .eq(ProcessBindingEntity::getOperationType, operationType)
                .eq(ProcessBindingEntity::getScopeType, scopeType)
                .eq(ProcessBindingEntity::getScopeId, scopeId);
    }

    private long requireScopeId(ProcessBindingRequest request) {
        if (request.getScopeId() == null) {
            throw new BusinessException("scopeType=ORG 时 scopeId 必填");
        }
        return request.getScopeId();
    }

    private ProcessDefinitionEntity requirePublishedDefinition(Long definitionId) {
        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        if (!ProcessModelStatus.PUBLISHED.equals(definition.getStatus())) {
            throw new BusinessException("流程定义当前不是已发布状态，不能绑定");
        }
        return definition;
    }

    private ProcessBindingEntity requireBinding(Long bindingId) {
        ProcessBindingEntity entity = processBindingMapper.selectById(bindingId);
        if (entity == null) {
            throw new BusinessException("业务绑定不存在");
        }
        return entity;
    }

    private ProcessBindingVO toVO(ProcessBindingEntity entity) {
        return ProcessBindingVO.builder()
                .id(entity.getId())
                .bizType(entity.getBizType())
                .operationType(entity.getOperationType())
                .scopeType(entity.getScopeType())
                .scopeId(entity.getScopeId())
                .definitionId(entity.getDefinitionId())
                .executionMode(entity.getExecutionMode())
                .revision(entity.getRevision())
                .enabled(entity.getEnabled())
                .createBy(entity.getCreateBy())
                .createTime(entity.getCreateTime())
                .updateBy(entity.getUpdateBy())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
