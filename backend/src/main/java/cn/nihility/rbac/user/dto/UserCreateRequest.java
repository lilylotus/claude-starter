package cn.nihility.rbac.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建用户的请求参数，可同时提交 0 到多条任职记录一并创建（数组项均不应携带 {@code id}）。
 */
@Getter
@Setter
@Schema(description = "创建用户请求参数")
public class UserCreateRequest {

    /** 用户姓名。 */
    @NotBlank(message = "用户姓名不能为空")
    @Size(max = 64, message = "用户姓名长度不能超过 64 个字符")
    @Schema(description = "用户姓名")
    private String name;

    /** 用户编号，需在未删除的用户中保持唯一。 */
    @NotBlank(message = "用户编号不能为空")
    @Size(max = 64, message = "用户编号长度不能超过 64 个字符")
    @Schema(description = "用户编号")
    private String code;

    /** 性别，取自字典类型 gender 下的字典项编码。 */
    @Size(max = 64, message = "性别长度不能超过 64 个字符")
    @Schema(description = "性别，取自字典类型 gender 下的字典项编码", defaultValue = "unknown")
    private String gender = "unknown";

    /** 手机号，可选，仅做基础格式校验，不做唯一性约束。 */
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String mobile;

    /** 身份证号，可选，若提供需在未删除的用户中保持唯一，仅做基础格式校验。 */
    @Pattern(regexp = "^$|^\\d{15}$|^\\d{17}[0-9Xx]$", message = "身份证号格式不正确")
    @Schema(description = "身份证号")
    private String idCard;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;

    /** 一并创建的任职记录列表，可为空，数组项均不应携带 {@code id}。 */
    @Valid
    @Schema(description = "一并创建的任职记录列表")
    private List<UserPositionRequest> positions = new ArrayList<>();

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
