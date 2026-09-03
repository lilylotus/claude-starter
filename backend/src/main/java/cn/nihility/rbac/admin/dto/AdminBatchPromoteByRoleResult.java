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
 * 按角色批量设置管理员执行结果：本次实际新建管理员数量、补充角色数量，以及因管理员编码
 * 冲突被跳过的用户明细（不中断整批操作）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按角色批量设置管理员执行结果")
public class AdminBatchPromoteByRoleResult {

    /** 本次实际新建的管理员数量。 */
    @Schema(description = "本次实际新建的管理员数量")
    private Integer newAdminCount;

    /** 本次实际补充角色的管理员数量。 */
    @Schema(description = "本次实际补充角色的管理员数量")
    private Integer appendRoleCount;

    /** 因管理员编码冲突被跳过的用户明细。 */
    @Builder.Default
    @Schema(description = "因管理员编码冲突被跳过的用户明细")
    private List<AdminBatchPromoteSkippedVO> skipped = new ArrayList<>();
}
