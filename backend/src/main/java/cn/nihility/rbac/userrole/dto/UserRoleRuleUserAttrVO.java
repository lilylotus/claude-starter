package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色规则用户属性条件视图对象，{@code fieldName}/{@code fieldCode}/{@code bizType}
 * 关联 {@code tab_metadata_field} 实时查询回填，便于前端展示（{@code bizType} 用于渲染
 * "用户-性别"/"任职-任职类型"这类带域前缀的展示名）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户角色规则用户属性条件")
public class UserRoleRuleUserAttrVO {

    /** 关联的元数据字段 id。 */
    @Schema(description = "元数据字段 id")
    private Long metadataFieldId;

    /** 关联的元数据字段名称。 */
    @Schema(description = "元数据字段名称")
    private String fieldName;

    /** 关联的元数据字段编码。 */
    @Schema(description = "元数据字段编码")
    private String fieldCode;

    /** 关联的元数据字段业务域：{@code USER}=用户主表字段，{@code POSITION}=任职记录字段。 */
    @Schema(description = "元数据字段业务域：USER=用户主表字段，POSITION=任职记录字段")
    private String bizType;

    /** 运算符：EQ/NE/IN。 */
    @Schema(description = "运算符：EQ=等于，NE=不等于，IN=属于多值")
    private String operator;

    /** 比较值：EQ/NE 为单个元素的列表，IN 为一组值。 */
    @Schema(description = "比较值")
    private List<String> values;
}
