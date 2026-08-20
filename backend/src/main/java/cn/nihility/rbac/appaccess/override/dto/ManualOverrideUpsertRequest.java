package cn.nihility.rbac.appaccess.override.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 人工例外新增/更新请求：按 {@code userId+appId} upsert 语义，已存在则更新
 * {@code overrideType}/{@code remark}，不新增一行（spec.md"人工例外的维护"需求）。
 */
@Getter
@Setter
@Schema(description = "人工例外新增/更新请求")
public class ManualOverrideUpsertRequest {

    /** 用户 id，必填。 */
    @NotNull(message = "用户不能为空")
    @Schema(description = "用户 id")
    private Long userId;

    /** 应用 id，必填。 */
    @NotNull(message = "应用不能为空")
    @Schema(description = "应用 id")
    private Long appId;

    /** 例外类型：GRANT/DENY，必填。 */
    @NotBlank(message = "例外类型不能为空")
    @Schema(description = "例外类型：GRANT=手动追加授权，DENY=手动收回授权")
    private String overrideType;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注")
    private String remark;
}
