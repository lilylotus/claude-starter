package cn.nihility.rbac.dict.entity;

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
 * 字典项持久化实体，对应表 {@code tab_dict_item}，通过 {@code dictTypeId} 关联
 * {@code tab_dict_type.id}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_dict_item")
public class DictItemEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典类型 id，关联 {@code tab_dict_type.id}。 */
    private Long dictTypeId;

    /** 字典项标签（展示文案）。 */
    private String label;

    /** 字典项编码，在同一 {@code dictTypeId} 下唯一（不同类型下可重复）。 */
    private String code;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    private Integer status;

    /** 面向外部同步消费者的实体版本。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long version;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
