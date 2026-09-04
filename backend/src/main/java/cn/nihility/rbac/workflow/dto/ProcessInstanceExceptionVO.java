package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 运维异常流程实例视图对象：当前处于 {@code exceptionCode=ASSIGNEE_EMPTY} 状态（空审批人
 * 待分配）的流程实例列表行，供运维排查（production-approval-lifecycle change tasks.md
 * 5.4"空人可见"）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "运维异常流程实例")
public class ProcessInstanceExceptionVO {

    /** 流程实例 id。 */
    @Schema(description = "流程实例 id")
    private Long id;

    /** 业务对象类型。 */
    @Schema(description = "业务对象类型")
    private String businessType;

    /** 业务对象 id。 */
    @Schema(description = "业务对象 id")
    private Long businessId;

    /** 发起人用户 id。 */
    @Schema(description = "发起人用户 id")
    private Long applicantId;

    /** 当前所在节点 id。 */
    @Schema(description = "当前所在节点 id")
    private String currentNodeId;

    /** 当前所在节点名称。 */
    @Schema(description = "当前所在节点名称")
    private String currentNodeName;

    /** 运维阻塞原因码。 */
    @Schema(description = "运维阻塞原因码")
    private String exceptionCode;

    /** 启动时间。 */
    @Schema(description = "启动时间")
    private LocalDateTime startedTime;
}
