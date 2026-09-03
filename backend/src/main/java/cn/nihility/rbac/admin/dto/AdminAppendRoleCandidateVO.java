package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按角色批量设置管理员预览结果中"将补充角色"分组的一行：当前持有目标角色、状态启用、且
 * 已关联一个未删除管理员记录，但该管理员的角色列表尚未包含目标角色的用户。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "将补充角色分组候选行")
public class AdminAppendRoleCandidateVO {

    /** 既有管理员 id，执行时仅向其追加一条角色关联，不改动其他字段。 */
    @Schema(description = "既有管理员 id")
    private Long adminId;

    /** 既有管理员名称。 */
    @Schema(description = "既有管理员名称")
    private String adminName;

    /** 既有管理员编码。 */
    @Schema(description = "既有管理员编码")
    private String adminCode;

    /** 关联用户 id。 */
    @Schema(description = "关联用户 id")
    private Long userId;

    /** 关联用户姓名。 */
    @Schema(description = "关联用户姓名")
    private String userName;
}
