package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用管理详情/列表行视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用详情")
public class AppVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 应用名称。 */
    @Schema(description = "应用名称")
    private String name;

    /** 应用编码。 */
    @Schema(description = "应用编码")
    private String code;

    /** 负责人用户 id。 */
    @Schema(description = "负责人用户 id")
    private Long ownerId;

    /** 负责人姓名，供前端展示。 */
    @Schema(description = "负责人姓名")
    private String ownerName;

    /** 所属组织 id。 */
    @Schema(description = "所属组织 id")
    private Long orgId;

    /** 所属组织名称，供前端展示。 */
    @Schema(description = "所属组织名称")
    private String orgName;

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
