package cn.nihility.rbac.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 字典项详情/列表行视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典项详情")
public class DictItemVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 所属字典类型 id。 */
    @Schema(description = "所属字典类型 id")
    private Long dictTypeId;

    /** 所属字典类型名称，供前端展示。 */
    @Schema(description = "所属字典类型名称")
    private String dictTypeName;

    /** 字典项标签（展示文案）。 */
    @Schema(description = "字典项标签")
    private String label;

    /** 字典项编码。 */
    @Schema(description = "字典项编码")
    private String code;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
