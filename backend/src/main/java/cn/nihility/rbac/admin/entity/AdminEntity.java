package cn.nihility.rbac.admin.entity;

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
 * 管理员主数据持久化实体，对应表 {@code tab_admin}，通过 {@code userId} 关联
 * {@code tab_user.id}（不建物理外键）。角色关联（{@code tab_admin_role}）与组织管辖范围
 * （{@code tab_admin_org_scope}）随本实体创建/更新时整体同步（先删后插），不在本实体内
 * 携带这两类关联数据。{@code autoCreatedRoleId} 非空时表示该管理员记录是通过"按角色批量
 * 设置管理员"为这个角色 id 自动创建的，供"角色收回联动停用自动创建的管理员"规则读取
 * （add-user-role-batch-assignment change design.md Decision 7）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_admin")
public class AdminEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员名称。 */
    private String name;

    /** 管理员编码，需在未删除的管理员中保持唯一。 */
    private String code;

    /** 关联用户 id，关联 {@code tab_user.id}，需在未删除的管理员中保持唯一。 */
    private Long userId;

    /** 若非空，表示该管理员是通过"按角色批量设置管理员"为这个角色 id 自动创建的；人工新增
     *  管理员、或通过"补充角色"方式获得角色的管理员，本字段为 {@code null}。 */
    private Long autoCreatedRoleId;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）。 */
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
