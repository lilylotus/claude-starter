package cn.nihility.rbac.org.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 组织树节点视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组织树节点")
public class OrgTreeNodeVO {

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

    /** 上级组织编码，顶级组织为 null。 */
    @Schema(description = "上级组织编码，顶级组织为 null")
    private String parentCode;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 子组织节点列表。 */
    @Builder.Default
    @Schema(description = "子组织节点列表")
    private List<OrgTreeNodeVO> children = new ArrayList<>();
}
