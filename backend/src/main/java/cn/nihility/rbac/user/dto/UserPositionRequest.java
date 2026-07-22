package cn.nihility.rbac.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户任职记录的请求参数，随用户创建/更新接口整体提交。{@code id} 为空表示新增，
 * 非空表示更新既有任职记录（若不属于当前用户则整个更新请求会被拒绝）。
 */
@Getter
@Setter
@Schema(description = "用户任职记录请求参数")
public class UserPositionRequest {

    /** 任职记录 id，为空表示新增，非空表示更新既有记录。 */
    @Schema(description = "任职记录 id，为空表示新增")
    private Long id;

    /** 所属组织 id，必填，不允许"任职但不挂靠任何组织"。 */
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

    /** 预留扩展字段 1，是否展示/必填/正则/唯一由"表单字段定义"配置驱动。 */
    @Size(max = 255, message = "扩展字段 1 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 1")
    private String ext1;

    /** 预留扩展字段 2。 */
    @Size(max = 255, message = "扩展字段 2 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 2")
    private String ext2;

    /** 预留扩展字段 3。 */
    @Size(max = 255, message = "扩展字段 3 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 3")
    private String ext3;

    /** 预留扩展字段 4。 */
    @Size(max = 255, message = "扩展字段 4 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 4")
    private String ext4;

    /** 预留扩展字段 5。 */
    @Size(max = 255, message = "扩展字段 5 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 5")
    private String ext5;

    /** 预留扩展字段 6。 */
    @Size(max = 255, message = "扩展字段 6 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 6")
    private String ext6;

    /** 预留扩展字段 7。 */
    @Size(max = 255, message = "扩展字段 7 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 7")
    private String ext7;

    /** 预留扩展字段 8。 */
    @Size(max = 255, message = "扩展字段 8 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 8")
    private String ext8;

    /** 预留扩展字段 9。 */
    @Size(max = 255, message = "扩展字段 9 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 9")
    private String ext9;

    /** 预留扩展字段 10。 */
    @Size(max = 255, message = "扩展字段 10 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 10")
    private String ext10;
}
