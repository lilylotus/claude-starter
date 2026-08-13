package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新上游数据源基础信息（名称、同步方式）的请求参数，创建后允许修改同步方式
 * （spec.md Requirement：上游数据源基础配置管理）。状态、连接配置、调度配置均不通过
 * 本接口修改。
 */
@Getter
@Setter
@Schema(description = "更新上游数据源基础信息请求参数")
public class UpstreamSourceUpdateRequest {

    /** 数据源名称，必填。 */
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称长度不能超过 128 个字符")
    @Schema(description = "数据源名称")
    private String name;

    /** 同步方式，必填，只能是 API/DB_TABLE。 */
    @NotBlank(message = "同步方式不能为空")
    @Pattern(regexp = "^(API|DB_TABLE)$", message = "同步方式只能是 API 或 DB_TABLE")
    @Schema(description = "同步方式：API（接口）/DB_TABLE（数据库表）")
    private String syncType;
}
