package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新上游数据源连接配置的请求参数，按 {@code syncType} 二选一生效（服务层按数据源当前
 * {@code syncType} 校验，未涉及的一侧字段允许为空）：
 * <ul>
 *     <li>{@code syncType=API} 时使用 {@link #apiAuthHeaders}，整体替换语义——本次提交的
 *     完整请求头集合会整体替换已保存的集合（不支持只改其中一个 key 的取值而保留其余
 *     未提交的 key，管理端不回显明文，天然无法做"部分修改"）。</li>
 *     <li>{@code syncType=DB_TABLE} 时使用 {@link #dbJdbcUrl}/{@link #dbUsername}/
 *     {@link #dbPassword}，{@code dbPassword} 留空表示不修改已保存的密码。</li>
 * </ul>
 */
@Getter
@Setter
@Schema(description = "更新上游数据源连接配置请求参数")
public class UpstreamConnectionConfigRequest {

    /** 接口模式自定义请求头（key-value，明文），syncType=API 时使用，整体替换。 */
    @Schema(description = "接口模式自定义请求头（key-value，明文），仅 syncType=API 时生效，整体替换")
    private Map<String, String> apiAuthHeaders = new LinkedHashMap<>();

    /** 数据库模式 JDBC 连接地址，仅支持 {@code jdbc:mysql://} 前缀，syncType=DB_TABLE 时使用。 */
    @Size(max = 500, message = "JDBC 连接地址长度不能超过 500 个字符")
    @Schema(description = "数据库模式 JDBC 连接地址，仅支持 jdbc:mysql:// 前缀")
    private String dbJdbcUrl;

    /** 数据库模式连接用户名，syncType=DB_TABLE 时使用。 */
    @Size(max = 128, message = "数据库用户名长度不能超过 128 个字符")
    @Schema(description = "数据库模式连接用户名")
    private String dbUsername;

    /** 数据库模式连接密码（明文），留空表示不修改已保存的密码，syncType=DB_TABLE 时使用。 */
    @Size(max = 255, message = "数据库密码长度不能超过 255 个字符")
    @Schema(description = "数据库模式连接密码（明文），留空表示不修改已保存的密码")
    private String dbPassword;
}
