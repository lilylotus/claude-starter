package cn.nihility.rbac.workflow.mapstruct;

import cn.nihility.rbac.workflow.dto.ApprovalRecordVO;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Workflow 引擎实体与视图对象转换器。不注册为 Spring bean，接口内声明静态单例，调用方直接
 * {@code WorkflowConvert.INSTANCE.xxx(...)}。展示名等需要跨模块解析的字段由 Service 层在
 * 转换后补填，本转换器只做同名字段映射。
 */
@Mapper
public interface WorkflowConvert {

    /** 转换器静态单例。 */
    WorkflowConvert INSTANCE = Mappers.getMapper(WorkflowConvert.class);

    /**
     * 转换审批任务。
     *
     * @param entity 审批任务实体
     * @return 审批任务视图对象
     */
    @Mapping(target = "businessType", ignore = true)
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "assigneeName", ignore = true)
    @Mapping(target = "applicantId", ignore = true)
    @Mapping(target = "applicantName", ignore = true)
    ApprovalTaskVO toTaskVO(ApprovalTaskEntity entity);

    /**
     * 转换审批任务列表。
     *
     * @param entities 审批任务实体列表
     * @return 审批任务视图对象列表
     */
    List<ApprovalTaskVO> toTaskVOList(List<ApprovalTaskEntity> entities);

    /**
     * 转换审批轨迹。
     *
     * @param entity 审批轨迹实体
     * @return 审批轨迹视图对象
     */
    @Mapping(target = "operatorName", ignore = true)
    @Mapping(target = "fromUserName", ignore = true)
    @Mapping(target = "toUserName", ignore = true)
    ApprovalRecordVO toRecordVO(ApprovalRecordEntity entity);

    /**
     * 转换审批轨迹列表。
     *
     * @param entities 审批轨迹实体列表
     * @return 审批轨迹视图对象列表
     */
    List<ApprovalRecordVO> toRecordVOList(List<ApprovalRecordEntity> entities);
}
