package cn.nihility.rbac.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 资源树节点视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资源树节点")
public class MenuTreeNodeVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 资源名称。 */
    @Schema(description = "资源名称")
    private String name;

    /** 资源编码。 */
    @Schema(description = "资源编码")
    private String code;

    /** 上级资源 id，0 表示顶级/根节点。 */
    @Schema(description = "上级资源 id，0 表示顶级")
    private Long parentId;

    /** 资源类型：1=菜单，2=按钮，3=API。 */
    @Schema(description = "资源类型：1=菜单，2=按钮，3=API")
    private Integer resourceType;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 子资源节点列表。 */
    @Builder.Default
    @Schema(description = "子资源节点列表")
    private List<MenuTreeNodeVO> children = new ArrayList<>();
}
