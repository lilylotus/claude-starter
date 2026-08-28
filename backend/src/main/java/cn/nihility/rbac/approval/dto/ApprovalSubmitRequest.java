package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用审批申请提交请求。
 */
@Getter
@Setter
@Schema(description = "通用审批申请提交请求")
public class ApprovalSubmitRequest {

    /** 业务对象类型。 */
    @NotBlank(message = "业务对象类型不能为空")
    private String bizType;

    /** 操作类型。 */
    @NotBlank(message = "操作类型不能为空")
    private String operationType;

    /** 目标记录 id。 */
    private Long targetId;

    /** 创建或更新请求体。 */
    private Map<String, Object> requestPayload;
}
