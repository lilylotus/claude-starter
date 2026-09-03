package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 委派请求体。
 */
@Getter
@Setter
@Schema(description = "委派请求")
public class DelegateRequest {

    /** 受托人用户 id，必填。 */
    @NotNull(message = "受托人不能为空")
    @Schema(description = "受托人用户 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetUserId;

    /** 委派原因，可为空。 */
    @Size(max = 500, message = "委派原因长度不能超过 500 个字符")
    @Schema(description = "委派原因")
    private String remark;
}
