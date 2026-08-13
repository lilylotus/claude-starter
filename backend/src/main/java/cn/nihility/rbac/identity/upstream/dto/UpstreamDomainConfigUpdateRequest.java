package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新上游数据源单个数据域配置的请求参数。按数据源 {@code syncType} 二选一生效
 * （服务层校验）：{@code API} 时使用 {@link #apiUrl}/{@link #apiMethod}，
 * {@code DB_TABLE} 时使用 {@link #dbSql}（须以 {@code SELECT}/{@code WITH} 开头，
 * spec.md Requirement：数据域配置）。任职（POSITION）数据域额外要求取数结果包含两个
 * 固定编码列 {@code __userIdentifier}/{@code __orgCode}（用于落库时解析所属人员/组织，
 * 见 {@code UpstreamPositionPseudoFieldCode}），不经过字段映射表配置。
 */
@Getter
@Setter
@Schema(description = "更新上游数据源数据域配置请求参数")
public class UpstreamDomainConfigUpdateRequest {

    /** 是否启用该数据域，必填。 */
    @NotNull(message = "是否启用不能为空")
    @Schema(description = "是否启用该数据域")
    private Boolean enabled;

    /** 接口模式该数据域的请求地址，syncType=API 时使用。 */
    @Size(max = 500, message = "请求地址长度不能超过 500 个字符")
    @Schema(description = "接口模式该数据域的请求地址")
    private String apiUrl;

    /** 接口模式请求方式：GET/POST，syncType=API 时使用。 */
    @Pattern(regexp = "^$|^(GET|POST)$", message = "请求方式只能是 GET 或 POST")
    @Schema(description = "接口模式请求方式：GET/POST")
    private String apiMethod;

    /** 数据库模式该数据域的只读查询 SQL，须以 SELECT/WITH 开头，syncType=DB_TABLE 时使用。 */
    @Size(max = 4000, message = "查询 SQL 长度不能超过 4000 个字符")
    @Schema(description = "数据库模式该数据域的只读查询 SQL，须以 SELECT/WITH 开头")
    private String dbSql;
}
