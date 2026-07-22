package cn.nihility.rbac.app.entity;

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
 * 应用持久化实体，对应表 {@code tab_app}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app")
public class AppEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用名称。 */
    private String name;

    /** 应用编码，需在未删除的应用中保持唯一。 */
    private String code;

    /** 负责人用户 id。 */
    private Long ownerId;

    /** 所属组织 id。 */
    private Long orgId;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    private Integer status;

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
