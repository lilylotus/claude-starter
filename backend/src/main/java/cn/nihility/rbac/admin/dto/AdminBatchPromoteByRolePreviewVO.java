package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按角色批量设置管理员预览结果：当前持有目标角色、状态启用的用户，按是否已关联未删除
 * 管理员记录分为"将新建管理员"、"将补充角色"两个分组；已是管理员且角色列表已包含目标
 * 角色的用户不出现在任一分组中（add-user-role-batch-assignment change design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按角色批量设置管理员预览结果")
public class AdminBatchPromoteByRolePreviewVO {

    /** "将新建管理员"分组，执行时批量创建管理员记录。 */
    @Builder.Default
    @Schema(description = "将新建管理员分组")
    private List<AdminBatchPromoteCandidateVO> newAdmins = new ArrayList<>();

    /** "将新建管理员"分组数量。 */
    @Schema(description = "将新建管理员分组数量")
    private Integer newAdminCount;

    /** "将补充角色"分组，执行时仅追加角色到既有管理员的角色列表。 */
    @Builder.Default
    @Schema(description = "将补充角色分组")
    private List<AdminAppendRoleCandidateVO> appendRoleAdmins = new ArrayList<>();

    /** "将补充角色"分组数量。 */
    @Schema(description = "将补充角色分组数量")
    private Integer appendRoleCount;
}
