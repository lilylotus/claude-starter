package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色规则"预览"命中用户行视图对象。{@code orgName} 取该用户满足组织范围条件的其中
 * 一条任职记录的组织名称；若只配置了用户属性条件、未配置组织范围条件，则取该用户任意一条
 * 未删除任职记录的组织名称；没有任职记录时留空。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户角色规则命中用户预览行")
public class UserRoleMatchedUserVO {

    /** 用户 id。 */
    @Schema(description = "用户 id")
    private Long id;

    /** 用户姓名。 */
    @Schema(description = "用户姓名")
    private String name;

    /** 用户编号。 */
    @Schema(description = "用户编号")
    private String code;

    /** 所属组织名称，没有可参考的任职记录时为空字符串。 */
    @Schema(description = "所属组织名称，没有可参考的任职记录时为空字符串")
    private String orgName;
}
