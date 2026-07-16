package cn.nihility.rbac.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 菜单/按钮/API 资源持久化实体，对应表 {@code tab_menu}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_menu")
public class MenuEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源名称。 */
    private String name;

    /** 资源编码，需在未删除的资源中保持唯一。 */
    private String code;

    /** 上级资源 id，0 表示顶级/根节点。 */
    private Long parentId;

    /** 资源类型：1=菜单，2=按钮，3=API。 */
    private Integer resourceType;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
