package cn.nihility.rbac.org.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 组织详情/列表行视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组织详情")
public class OrgVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 组织名称。 */
    @Schema(description = "组织名称")
    private String name;

    /** 组织编码。 */
    @Schema(description = "组织编码")
    private String code;

    /** 上级组织 id，0 表示顶级/根节点。 */
    @Schema(description = "上级组织 id，0 表示顶级")
    private Long parentId;

    /** 上级组织名称，供前端展示，0 表示顶级时为 null。 */
    @Schema(description = "上级组织名称，顶级组织为 null")
    private String parentName;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

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
