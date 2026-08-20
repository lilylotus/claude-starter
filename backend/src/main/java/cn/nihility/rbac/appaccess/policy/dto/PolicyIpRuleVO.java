package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略请求控制条件-IP/网段白名单条目视图对象（app-access-request-control change
 * design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略 IP/网段白名单条目")
public class PolicyIpRuleVO {

    /** 单个 IP 地址或 CIDR 网段。 */
    @Schema(description = "单个 IP 地址或 CIDR 网段")
    private String ipCidr;
}
