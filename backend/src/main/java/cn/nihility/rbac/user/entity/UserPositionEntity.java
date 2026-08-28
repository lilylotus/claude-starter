package cn.nihility.rbac.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户任职记录持久化实体，对应表 {@code tab_user_position}，通过 {@code userId} 关联
 * {@code tab_user.id}、{@code orgId} 关联 {@code tab_org.id}（不建物理外键）。该实体同时
 * 被两个入口复用：用户管理内嵌子表单随用户创建/更新接口整体提交、按行 diff（未出现在
 * 请求列表中的既有记录物理删除），以及独立的任职管理入口（按 {@code orgId} 查询、
 * 单条增删改）。与 {@link UserEntity} 一致，拥有独立的 {@code status} 列表达
 * 启用/停用/逻辑删除语义（逻辑删除不做物理删除）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user_position")
public class UserPositionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 id，关联 {@code tab_user.id}。 */
    private Long userId;

    /** 所属组织 id，关联 {@code tab_org.id}，必填。 */
    private Long orgId;

    /** 任职类型编码，取自字典类型 {@code position_type} 下的字典项编码（如 primary/part_time/temporary）。 */
    private String positionType;

    /** 任职地址。 */
    private String positionAddress;

    /** 任职电话。 */
    private String positionPhone;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）。 */
    private Integer status;

    /** 面向外部同步消费者的实体版本。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long version;

    /** 预留扩展字段 1。 */
    private String ext1;

    /** 预留扩展字段 2。 */
    private String ext2;

    /** 预留扩展字段 3。 */
    private String ext3;

    /** 预留扩展字段 4。 */
    private String ext4;

    /** 预留扩展字段 5。 */
    private String ext5;

    /** 预留扩展字段 6。 */
    private String ext6;

    /** 预留扩展字段 7。 */
    private String ext7;

    /** 预留扩展字段 8。 */
    private String ext8;

    /** 预留扩展字段 9。 */
    private String ext9;

    /** 预留扩展字段 10。 */
    private String ext10;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
