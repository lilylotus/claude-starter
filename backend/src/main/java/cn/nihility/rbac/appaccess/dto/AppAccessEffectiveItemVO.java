package cn.nihility.rbac.appaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 最终生效权限查询结果条目，同时供"按用户查询"（{@code appId}/{@code appName} 随条目
 * 变化，{@code userId}/{@code userName} 固定为查询参数）与"按应用查询"（反之）两个接口
 * 复用（spec.md"最终生效权限查询"需求）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "最终生效权限查询结果条目")
public class AppAccessEffectiveItemVO {

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

    /** 判定依据：{@code MANUAL_GRANT}=人工追加授权，{@code POLICY}=策略授权，
     *  {@code REVOKED}=曾经可能可访问但被人工收回（仅 {@code includeRevoked=true} 时出现）。 */
    @Schema(description = "判定依据：MANUAL_GRANT/POLICY/REVOKED")
    private String sourceType;

    /** 命中的策略名称列表，{@code sourceType=POLICY} 或 {@code REVOKED} 且存在策略命中时
     *  有值，其余场景为空列表。 */
    @Schema(description = "命中的策略名称列表")
    private List<String> policyNames;

    /** {@code sourceType=POLICY} 时，命中的策略中是否存在任意一条配置了请求控制条件
     *  （浏览器白名单或 IP 白名单）；其余场景恒为 {@code false}。提示该条最终生效权限是
     *  "身份层面"判定，实际访问时仍需满足对应策略的请求控制才能通过（app-access-request-
     *  control change design.md Decision 5）。 */
    @Schema(description = "命中策略中是否存在配置了请求控制条件的（仅 sourceType=POLICY 时可能为 true）")
    private boolean hasRequestControl;
}
