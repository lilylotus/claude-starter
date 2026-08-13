package cn.nihility.rbac.identity.upstream.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游数据源数据域配置持久化实体，对应表 {@code tab_upstream_domain_config}。每个数据源
 * 固定 3 行，分别对应组织/用户/任职三个数据域（{@link cn.nihility.rbac.identity.upstream.constant.UpstreamDataType}），
 * 每行携带"是否启用"与各自独立的取数来源（接口模式的请求地址+方式，或数据库模式的只读
 * 查询 SQL）（design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_upstream_domain_config")
public class UpstreamDomainConfigEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属上游数据源 id，关联 {@code tab_upstream_source.id}。 */
    private Long sourceId;

    /** 数据域：ORG/USER/POSITION。 */
    private String dataType;

    /** 是否启用该数据域。 */
    private Boolean enabled;

    /** 接口模式该数据域的请求地址，syncType=API 时使用。 */
    private String apiUrl;

    /** 接口模式请求方式：GET/POST，syncType=API 时使用。 */
    private String apiMethod;

    /** 数据库模式该数据域的只读查询 SQL（列别名对应上游字段编码），syncType=DB_TABLE 时使用。 */
    private String dbSql;

    /** 该数据域上次同步完成时间，仅展示用途，不驱动增量逻辑。 */
    private LocalDateTime lastSyncTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
