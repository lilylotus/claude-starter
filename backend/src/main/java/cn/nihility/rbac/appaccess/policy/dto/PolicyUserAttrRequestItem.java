package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 策略用户属性条件请求项：{@code metadataFieldId} 关联"元数据字段管理"目录
 * （{@code biz_type=USER}）的字段，{@code operator} 仅 {@code EQ}/{@code NE}/{@code IN}
 * 三种取值，{@code values} 在 {@code EQ}/{@code NE} 时只取第一个元素，{@code IN} 时为
 * 一组比较值（落库前序列化为逗号分隔字符串，见 design.md Decision 1）。
 */
@Getter
@Setter
@Schema(description = "策略用户属性条件")
public class PolicyUserAttrRequestItem {

    /** 关联的元数据字段 id（{@code biz_type=USER}），必填。 */
    @NotNull(message = "属性字段不能为空")
    @Schema(description = "元数据字段 id")
    private Long metadataFieldId;

    /** 运算符：EQ/NE/IN，必填。 */
    @NotBlank(message = "运算符不能为空")
    @Schema(description = "运算符：EQ=等于，NE=不等于，IN=属于多值")
    private String operator;

    /** 比较值：EQ/NE 只取第一个元素，IN 为一组值，至少一个。 */
    @NotEmpty(message = "比较值不能为空")
    @Schema(description = "比较值：EQ/NE 只取第一个元素，IN 为一组值")
    private List<String> values;
}
