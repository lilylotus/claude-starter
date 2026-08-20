package cn.nihility.rbac.appaccess.override.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 人工例外视图对象，{@code userName}/{@code appName} 关联 {@code tab_user}/{@code tab_app}
 * 实时查询回填。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "人工例外")
public class ManualOverrideVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 用户 id。 */
    @Schema(description = "用户 id")
    private Long userId;

    /** 用户姓名。 */
    @Schema(description = "用户姓名")
    private String userName;

    /** 应用 id。 */
    @Schema(description = "应用 id")
    private Long appId;

    /** 应用名称。 */
    @Schema(description = "应用名称")
    private String appName;

    /** 例外类型：GRANT/DENY。 */
    @Schema(description = "例外类型：GRANT=手动追加授权，DENY=手动收回授权")
    private String overrideType;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
