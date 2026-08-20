package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略请求控制条件-浏览器白名单条目视图对象（app-access-request-control change
 * design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略浏览器白名单条目")
public class PolicyBrowserRuleVO {

    /** 浏览器编码：CHROME/FIREFOX/SAFARI/EDGE/OPERA/IE。 */
    @Schema(description = "浏览器编码")
    private String browserCode;

    /** 浏览器展示名称。 */
    @Schema(description = "浏览器展示名称")
    private String browserLabel;
}
