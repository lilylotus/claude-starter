package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import cn.nihility.rbac.approval.entity.ApprovalSwitchEntity;
import cn.nihility.rbac.approval.mapper.ApprovalSwitchMapper;
import cn.nihility.rbac.approval.mapstruct.ApprovalConvert;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主数据审批开关业务实现。
 */
@Service
@RequiredArgsConstructor
public class ApprovalSwitchServiceImpl implements ApprovalSwitchService {

    /** 合法业务对象类型。 */
    private static final Set<String> SUPPORTED_BIZ_TYPES = Set.of(
            FormFieldBizType.ORG,
            FormFieldBizType.USER,
            FormFieldBizType.POSITION,
            FormFieldBizType.APP);

    /** 审批开关数据访问接口。 */
    private final ApprovalSwitchMapper approvalSwitchMapper;

    /** 操作日志记录器。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalSwitchVO> listAll() {
        List<ApprovalSwitchEntity> entities = approvalSwitchMapper.selectList(
                new LambdaQueryWrapper<ApprovalSwitchEntity>().orderByAsc(ApprovalSwitchEntity::getId));
        return ApprovalConvert.INSTANCE.toSwitchVOList(entities);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(String bizType) {
        return getExisting(bizType).getEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ApprovalSwitchVO update(String bizType, boolean enabled) {
        ApprovalSwitchEntity entity = getExisting(bizType);
        Map<String, Object> before = snapshot(entity);
        entity.setEnabled(enabled);
        entity.setUpdateBy(currentUserIdText());
        entity.setUpdateTime(LocalDateTime.now());
        approvalSwitchMapper.updateById(entity);
        operationLogRecorder.recordUpdate(
                OperationLogResourceType.APPROVAL_SWITCH,
                entity.getId(),
                entity.getBizType(),
                before,
                snapshot(entity));
        return ApprovalConvert.INSTANCE.toSwitchVO(entity);
    }

    /**
     * 查询指定业务对象的审批开关。
     *
     * @param bizType 业务对象类型
     * @return 审批开关实体
     */
    private ApprovalSwitchEntity getExisting(String bizType) {
        if (!SUPPORTED_BIZ_TYPES.contains(bizType)) {
            throw new BusinessException("不支持的业务对象类型：" + bizType);
        }
        ApprovalSwitchEntity entity = approvalSwitchMapper.selectOne(
                new LambdaQueryWrapper<ApprovalSwitchEntity>()
                        .eq(ApprovalSwitchEntity::getBizType, bizType));
        if (entity == null) {
            throw new BusinessException("审批开关不存在：" + bizType);
        }
        return entity;
    }

    /**
     * 构造审批开关操作日志快照。
     *
     * @param entity 审批开关实体
     * @return 操作日志快照
     */
    private Map<String, Object> snapshot(ApprovalSwitchEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("业务对象类型", entity.getBizType());
        snapshot.put("审批状态", Boolean.TRUE.equals(entity.getEnabled()) ? "开启" : "关闭");
        return snapshot;
    }

    /**
     * 读取当前用户 id 文本。
     *
     * @return 当前用户 id 文本
     */
    private String currentUserIdText() {
        Long userId = CurrentUserContext.getUserId();
        return userId == null ? null : userId.toString();
    }
}
