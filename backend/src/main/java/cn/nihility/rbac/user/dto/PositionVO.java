package cn.nihility.rbac.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 任职管理详情/列表行视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任职记录详情")
public class PositionVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 所属用户 id。 */
    @Schema(description = "所属用户 id")
    private Long userId;

    /** 所属用户姓名，供前端展示。 */
    @Schema(description = "所属用户姓名")
    private String userName;

    /** 所属组织 id。 */
    @Schema(description = "所属组织 id")
    private Long orgId;

    /** 所属组织名称，供前端展示。 */
    @Schema(description = "所属组织名称")
    private String orgName;

    /** 认证类型编码。 */
    @Schema(description = "认证类型编码")
    private String positionType;

    /** 任职地址。 */
    @Schema(description = "任职地址")
    private String positionAddress;

    /** 任职电话。 */
    @Schema(description = "任职电话")
    private String positionPhone;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

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
