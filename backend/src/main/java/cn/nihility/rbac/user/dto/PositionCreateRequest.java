package cn.nihility.rbac.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 独立创建任职记录的请求参数（任职管理入口），须指定一个已存在的用户。
 */
@Getter
@Setter
@Schema(description = "创建任职记录请求参数")
public class PositionCreateRequest {

    /** 所属用户 id，必填，须为已存在的用户。 */
    @NotNull(message = "所属用户不能为空")
    @Schema(description = "所属用户 id")
    private Long userId;

    /** 所属组织 id，必填。 */
    @NotNull(message = "所属组织不能为空")
    @Schema(description = "所属组织 id")
    private Long orgId;

    /** 认证类型编码，取自字典类型 {@code position_type} 下的字典项编码（如 primary/part_time/temporary）。 */
    @NotBlank(message = "认证类型不能为空")
    @Size(max = 64, message = "认证类型编码长度不能超过 64 个字符")
    @Schema(description = "认证类型编码")
    private String positionType;

    /** 任职地址，可选。 */
    @Size(max = 255, message = "任职地址长度不能超过 255 个字符")
    @Schema(description = "任职地址")
    private String positionAddress;

    /** 任职电话，可选。 */
    @Size(max = 32, message = "任职电话长度不能超过 32 个字符")
    @Schema(description = "任职电话")
    private String positionPhone;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;
}
