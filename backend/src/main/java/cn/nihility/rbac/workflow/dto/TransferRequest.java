package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 转办请求体。
 */
@Getter
@Setter
@Schema(description = "转办请求")
public class TransferRequest {

    /** 新处理人用户 id，必填。 */
    @NotNull(message = "新处理人不能为空")
    @Schema(description = "新处理人用户 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetUserId;

    /** 转办原因，可为空。 */
    @Size(max = 500, message = "转办原因长度不能超过 500 个字符")
    @Schema(description = "转办原因")
    private String remark;
}
