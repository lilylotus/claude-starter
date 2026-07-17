package cn.nihility.rbac.admin.dto;

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
 * 管理员详情/列表行视图对象。{@code roles}/{@code orgScopes} 仅在详情查询接口中填充，
 * 分页列表查询为避免 N+1 不会填充这两个字段。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员详情")
public class AdminVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 管理员名称。 */
    @Schema(description = "管理员名称")
    private String name;

    /** 管理员编码。 */
    @Schema(description = "管理员编码")
    private String code;

    /** 关联用户 id。 */
    @Schema(description = "关联用户 id")
    private Long userId;

    /** 关联用户姓名，供前端展示。 */
    @Schema(description = "关联用户姓名")
    private String userName;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 该管理员关联的全部角色，仅详情查询接口填充。 */
    @Builder.Default
    @Schema(description = "角色列表，仅详情查询接口填充")
    private List<AdminRoleVO> roles = new ArrayList<>();

    /** 该管理员的全部管辖组织范围，仅详情查询接口填充。 */
    @Builder.Default
    @Schema(description = "管辖组织范围列表，仅详情查询接口填充")
    private List<AdminOrgScopeVO> orgScopes = new ArrayList<>();

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
