package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按角色批量设置管理员执行结果中被跳过的一行：通常是"将新建管理员"分组中某用户的编号已被
 * 另一个未删除管理员占用为其管理员编码，导致该用户被跳过、不创建。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量执行时被跳过的用户明细")
public class AdminBatchPromoteSkippedVO {

    /** 被跳过的用户 id。 */
    @Schema(description = "用户 id")
    private Long userId;

    /** 被跳过的用户姓名。 */
    @Schema(description = "用户姓名")
    private String userName;

    /** 被跳过的用户编号。 */
    @Schema(description = "用户编号")
    private String userCode;

    /** 跳过原因。 */
    @Schema(description = "跳过原因")
    private String reason;
}
