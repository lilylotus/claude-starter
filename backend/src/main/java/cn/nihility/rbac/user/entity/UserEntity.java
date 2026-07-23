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
 * 用户持久化实体，对应表 {@code tab_user}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_user")
public class UserEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户姓名。 */
    private String name;

    /** 用户编号，未删除范围内唯一。 */
    private String code;

    /** 性别，取自字典类型 gender 下的字典项编码。 */
    private String gender;

    /** 手机号，不做唯一性约束。 */
    private String mobile;

    /** 身份证号，若提供需在未删除范围内唯一。 */
    private String idCard;

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
