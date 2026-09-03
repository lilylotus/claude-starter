package cn.nihility.rbac.workflow.designer.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 流程模型列表和详情返回对象。 */
@Getter
@Builder
public class ProcessModelVO {

    /** 流程模型主键。 */
    private final Long id;
    /** 流程编码。 */
    private final String processCode;
    /** 流程名称。 */
    private final String processName;
    /** 当前草稿 DSL。 */
    private final String modelJson;
    /** 生命周期状态。 */
    private final String status;
    /** 当前生效定义主键。 */
    private final Long currentDefinitionId;
    /** 创建人。 */
    private final String createBy;
    /** 创建时间。 */
    private final LocalDateTime createTime;
    /** 更新人。 */
    private final String updateBy;
    /** 更新时间。 */
    private final LocalDateTime updateTime;
}
