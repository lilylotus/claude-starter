package cn.nihility.rbac.appaccess.policy.entity;

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
 * 策略用户属性条件持久化实体，对应表 {@code tab_app_access_policy_user_attr}。
 * {@code metadataFieldId} 关联 {@code tab_metadata_field}（{@code biz_type=USER}）的
 * 某个已登记字段，不新增专用字段目录（design.md Decision 1）；{@code operator} 仅
 * {@code EQ}/{@code NE}/{@code IN} 三种取值，{@code attrValue} 在 {@code IN} 时为逗号
 * 分隔的多个值。无独立 {@code status}，随所属策略保存时整体替换（先删后插），物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy_user_attr")
public class PolicyUserAttrEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_app_access_policy.id}。 */
    private Long policyId;

    /** 关联的元数据字段 id，关联 {@code tab_metadata_field.id}（{@code biz_type=USER}）。 */
    private Long metadataFieldId;

    /** 运算符：{@code EQ}=等于，{@code NE}=不等于，{@code IN}=属于多值。 */
    private String operator;

    /** 比较值：{@code EQ}/{@code NE} 为单个值，{@code IN} 为逗号分隔的多个值。 */
    private String attrValue;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
