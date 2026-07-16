package cn.nihility.rbac.permission.entity;

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
 * 权限点持久化实体，对应表 {@code tab_permission}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_permission")
public class PermissionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限名称。 */
    private String name;

    /** 权限编码，需在未删除的权限点中保持唯一。 */
    private String code;

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
