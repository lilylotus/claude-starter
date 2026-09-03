package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按角色批量设置管理员预览结果中"将新建管理员"分组的一行：当前持有目标角色、状态启用、
 * 但尚未关联任何未删除管理员记录的用户。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "将新建管理员分组候选行")
public class AdminBatchPromoteCandidateVO {

    /** 用户 id。 */
    @Schema(description = "用户 id")
    private Long userId;

    /** 用户姓名，执行时将作为新建管理员的名称。 */
    @Schema(description = "用户姓名，执行时将作为新建管理员的名称")
    private String userName;

    /** 用户编号，执行时将作为新建管理员的编码。 */
    @Schema(description = "用户编号，执行时将作为新建管理员的编码")
    private String userCode;
}
