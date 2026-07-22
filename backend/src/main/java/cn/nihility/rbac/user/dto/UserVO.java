package cn.nihility.rbac.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户详情/列表行视图对象。{@code positions} 仅在详情查询接口中填充，
 * 分页列表查询为避免 N+1 不会填充该字段。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户详情")
public class UserVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 用户姓名。 */
    @Schema(description = "用户姓名")
    private String name;

    /** 用户编号。 */
    @Schema(description = "用户编号")
    private String code;

    /** 性别：0=未知，1=男，2=女。 */
    @Schema(description = "性别：0=未知，1=男，2=女")
    private Integer gender;

    /** 手机号。 */
    @Schema(description = "手机号")
    private String mobile;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    private String idCard;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 该用户名下的全部任职记录，仅详情查询接口填充。 */
    @Builder.Default
    @Schema(description = "任职记录列表，仅详情查询接口填充")
    private List<UserPositionVO> positions = new ArrayList<>();

    /** 预留扩展字段 1。 */
    @Schema(description = "预留扩展字段 1")
    private String ext1;

    /** 预留扩展字段 2。 */
    @Schema(description = "预留扩展字段 2")
    private String ext2;

    /** 预留扩展字段 3。 */
    @Schema(description = "预留扩展字段 3")
    private String ext3;

    /** 预留扩展字段 4。 */
    @Schema(description = "预留扩展字段 4")
    private String ext4;

    /** 预留扩展字段 5。 */
    @Schema(description = "预留扩展字段 5")
    private String ext5;

    /** 预留扩展字段 6。 */
    @Schema(description = "预留扩展字段 6")
    private String ext6;

    /** 预留扩展字段 7。 */
    @Schema(description = "预留扩展字段 7")
    private String ext7;

    /** 预留扩展字段 8。 */
    @Schema(description = "预留扩展字段 8")
    private String ext8;

    /** 预留扩展字段 9。 */
    @Schema(description = "预留扩展字段 9")
    private String ext9;

    /** 预留扩展字段 10。 */
    @Schema(description = "预留扩展字段 10")
    private String ext10;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
