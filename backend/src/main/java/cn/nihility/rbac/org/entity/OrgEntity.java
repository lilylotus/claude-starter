package cn.nihility.rbac.org.entity;

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
 * 组织机构持久化实体，对应表 {@code tab_org}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_org")
public class OrgEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组织名称。 */
    private String name;

    /** 组织编码。 */
    private String code;

    /** 上级组织 id，0 表示顶级/根节点。 */
    private Long parentId;

    /** 上级组织编码，恒等于 parentId 对应父组织当前的 code，顶级组织为空。 */
    private String parentCode;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    private Integer status;

    /** 显示序号，值越大越靠前。 */
    private Integer showOrder;

    /** 备注。 */
    private String remark;

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
