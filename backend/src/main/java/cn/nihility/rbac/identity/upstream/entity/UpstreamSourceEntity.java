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
 * 上游数据源持久化实体，对应表 {@code tab_upstream_source}：一套连接信息（接口自定义
 * 请求头 / 数据库 JDBC 连接）+ 一套调度配置（design.md Decision 1）。接口模式的请求头
 * 取值、数据库模式的密码均以 SM4 加密文本落库，复用 {@code AppSecretProperties} 的同一把
 * 主密钥。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_upstream_source")
public class UpstreamSourceEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源名称。 */
    private String name;

    /** 同步方式：API=接口，DB_TABLE=数据库表。 */
    private String syncType;

    /** 是否启用，创建时默认 false。 */
    private Boolean enabled;

    /** 调度方式：INTERVAL=按间隔，FIXED_TIME=按每日固定时间点。 */
    private String scheduleType;

    /** 间隔单位：MINUTE/HOUR，scheduleType=INTERVAL 时使用。 */
    private String intervalUnit;

    /** 间隔取值（正整数），scheduleType=INTERVAL 时使用。 */
    private Integer intervalValue;

    /** 每日固定时间点，HH:mm 文本，scheduleType=FIXED_TIME 时使用。 */
    private String fixedTime;

    /** 上次定时触发时间，供轮询任务判断是否到点，手动触发不更新该列。 */
    private LocalDateTime lastTriggerTime;

    /** 接口模式自定义请求头，JSON 文本（{@code {key: SM4密文}}），syncType=API 时使用。 */
    private String apiAuthHeaders;

    /** 数据库模式 JDBC 连接地址，仅支持 {@code jdbc:mysql://} 前缀，syncType=DB_TABLE 时使用。 */
    private String dbJdbcUrl;

    /** 数据库模式连接用户名，syncType=DB_TABLE 时使用。 */
    private String dbUsername;

    /** 数据库模式连接密码，SM4 加密文本，syncType=DB_TABLE 时使用。 */
    private String dbPassword;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
