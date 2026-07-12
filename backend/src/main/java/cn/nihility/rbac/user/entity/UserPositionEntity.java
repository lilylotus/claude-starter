package cn.nihility.rbac.user.entity;

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
 * 用户任职记录持久化实体，对应表 {@code tab_user_position}，通过 {@code userId} 关联
 * {@code tab_user.id}、{@code orgId} 关联 {@code tab_org.id}（不建物理外键）。任职记录
 * 随用户创建/更新接口整体提交并做物理删除，不像 {@link UserEntity} 那样存在独立的
 * {@code status} 列表达启停用/逻辑删除语义。
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

    /** 认证类型编码，取自字典类型 {@code position_type} 下的字典项编码（如 primary/part_time/temporary）。 */
    private String positionType;

    /** 任职地址。 */
    private String positionAddress;

    /** 任职电话。 */
    private String positionPhone;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
