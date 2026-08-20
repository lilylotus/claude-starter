package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略用户属性条件视图对象，{@code fieldName}/{@code fieldCode} 关联
 * {@code tab_metadata_field} 实时查询回填，便于前端展示。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略用户属性条件")
public class PolicyUserAttrVO {

    /** 关联的元数据字段 id。 */
    @Schema(description = "元数据字段 id")
    private Long metadataFieldId;

    /** 关联的元数据字段名称。 */
    @Schema(description = "元数据字段名称")
    private String fieldName;

    /** 关联的元数据字段编码。 */
    @Schema(description = "元数据字段编码")
    private String fieldCode;

    /** 运算符：EQ/NE/IN。 */
    @Schema(description = "运算符：EQ=等于，NE=不等于，IN=属于多值")
    private String operator;

    /** 比较值：EQ/NE 为单个元素的列表，IN 为一组值。 */
    @Schema(description = "比较值")
    private List<String> values;

    /** 本条记录最近一次更新时间，供服务层计算"配置是否已变更待重新执行"标记使用。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
