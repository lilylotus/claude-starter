package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游数据源数据域配置视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上游数据源数据域配置")
public class UpstreamDomainConfigVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 所属上游数据源 id。 */
    @Schema(description = "所属上游数据源 id")
    private Long sourceId;

    /** 数据域：ORG/USER/POSITION。 */
    @Schema(description = "数据域：ORG/USER/POSITION")
    private String dataType;

    /** 是否启用该数据域。 */
    @Schema(description = "是否启用该数据域")
    private Boolean enabled;

    /** 接口模式该数据域的请求地址。 */
    @Schema(description = "接口模式该数据域的请求地址")
    private String apiUrl;

    /** 接口模式请求方式：GET/POST。 */
    @Schema(description = "接口模式请求方式：GET/POST")
    private String apiMethod;

    /** 数据库模式该数据域的只读查询 SQL。 */
    @Schema(description = "数据库模式该数据域的只读查询 SQL")
    private String dbSql;

    /** 该数据域上次同步完成时间，仅展示用途。 */
    @Schema(description = "该数据域上次同步完成时间，仅展示用途")
    private LocalDateTime lastSyncTime;
}
