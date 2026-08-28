package cn.nihility.rbac.approval.mapstruct;

import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.entity.ApprovalSwitchEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 审批模块实体与视图对象转换器。
 */
@Mapper
public interface ApprovalConvert {

    /** 转换器静态单例。 */
    ApprovalConvert INSTANCE = Mappers.getMapper(ApprovalConvert.class);

    /**
     * 转换审批申请。
     *
     * @param entity 审批申请实体
     * @return 审批申请视图对象
     */
    @Mapping(target = "requestPayload", ignore = true)
    @Mapping(target = "approverName", ignore = true)
    @Mapping(target = "createByName", ignore = true)
    @Mapping(target = "targetSnapshot", ignore = true)
    ApprovalRequestVO toRequestVO(ApprovalRequestEntity entity);

    /**
     * 转换审批申请列表。
     *
     * @param entities 审批申请实体列表
     * @return 审批申请视图对象列表
     */
    List<ApprovalRequestVO> toRequestVOList(List<ApprovalRequestEntity> entities);

    /**
     * 转换审批开关。
     *
     * @param entity 审批开关实体
     * @return 审批开关视图对象
     */
    ApprovalSwitchVO toSwitchVO(ApprovalSwitchEntity entity);

    /**
     * 转换审批开关列表。
     *
     * @param entities 审批开关实体列表
     * @return 审批开关视图对象列表
     */
    List<ApprovalSwitchVO> toSwitchVOList(List<ApprovalSwitchEntity> entities);
}
