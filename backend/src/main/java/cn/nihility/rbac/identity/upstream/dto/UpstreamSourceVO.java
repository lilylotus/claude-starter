package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游数据源视图对象。出于安全考虑，接口模式的自定义请求头只返回 key 列表（不返回
 * 明文取值），数据库模式只返回 JDBC 地址与用户名（不返回明文密码），与
 * {@code AppConfigVO} 不返回 SecretKey 明文的既有安全策略一致（spec.md Requirement：
 * 接口方式连接配置/数据库表方式连接配置）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上游数据源")
public class UpstreamSourceVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 数据源名称。 */
    @Schema(description = "数据源名称")
    private String name;

    /** 同步方式：API（接口）/DB_TABLE（数据库表）。 */
    @Schema(description = "同步方式：API（接口）/DB_TABLE（数据库表）")
    private String syncType;

    /** 是否启用。 */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /** 调度方式：INTERVAL（按间隔）/FIXED_TIME（按每日固定时间点）。 */
    @Schema(description = "调度方式：INTERVAL（按间隔）/FIXED_TIME（按每日固定时间点）")
    private String scheduleType;

    /** 间隔单位：MINUTE/HOUR。 */
    @Schema(description = "间隔单位：MINUTE/HOUR")
    private String intervalUnit;

    /** 间隔取值。 */
    @Schema(description = "间隔取值")
    private Integer intervalValue;

    /** 每日固定时间点，HH:mm 文本。 */
    @Schema(description = "每日固定时间点，HH:mm 文本")
    private String fixedTime;

    /** 上次定时触发时间。 */
    @Schema(description = "上次定时触发时间")
    private LocalDateTime lastTriggerTime;

    /** 接口模式自定义请求头的 key 列表，只读，不返回明文取值。 */
    @Schema(description = "接口模式自定义请求头的 key 列表，不返回明文取值")
    private List<String> apiAuthHeaderKeys;

    /** 数据库模式 JDBC 连接地址。 */
    @Schema(description = "数据库模式 JDBC 连接地址")
    private String dbJdbcUrl;

    /** 数据库模式连接用户名。 */
    @Schema(description = "数据库模式连接用户名")
    private String dbUsername;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
