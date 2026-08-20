package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 策略规则分页查询参数，全部字段均可选。
 */
@Getter
@Setter
@Schema(description = "策略规则分页查询参数")
public class PolicyQueryRequest {

    /** 策略名称，模糊匹配，可选。 */
    @Schema(description = "策略名称，模糊匹配")
    private String name;

    /** 状态，精确匹配，可选。 */
    @Schema(description = "状态：2000=启用，3000=停用")
    private Integer status;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
