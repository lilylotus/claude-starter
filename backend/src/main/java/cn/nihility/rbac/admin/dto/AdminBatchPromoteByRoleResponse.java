package cn.nihility.rbac.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按角色批量设置管理员接口的统一响应外壳：{@code preview=true} 时只填充
 * {@code previewResult}，{@code executeResult} 为 {@code null}；{@code preview=false} 时
 * 只填充 {@code executeResult}，{@code previewResult} 为 {@code null}。两种模式共用同一个
 * 响应类型，便于单一接口在 OpenAPI 文档中保持一致的返回结构。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按角色批量设置管理员响应")
public class AdminBatchPromoteByRoleResponse {

    /** 本次请求是否为预览模式。 */
    @Schema(description = "本次请求是否为预览模式")
    private Boolean preview;

    /** 预览结果，仅 {@code preview=true} 时填充。 */
    @Schema(description = "预览结果，仅 preview=true 时填充")
    private AdminBatchPromoteByRolePreviewVO previewResult;

    /** 执行结果，仅 {@code preview=false} 时填充。 */
    @Schema(description = "执行结果，仅 preview=false 时填充")
    private AdminBatchPromoteByRoleResult executeResult;
}
