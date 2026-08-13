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
 * 上游数据同步执行记录持久化实体，对应表 {@code tab_upstream_sync_record}。粒度对齐
 * "数据源+数据域"：一次同步触发会为其下已启用的每个数据域各写一条记录（design.md
 * Decision 1）。仅用于展示排查，不驱动任何自动重试。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_upstream_sync_record")
public class UpstreamSyncRecordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属上游数据源 id，关联 {@code tab_upstream_source.id}。 */
    private Long sourceId;

    /** 数据域：ORG/USER/POSITION。 */
    private String dataType;

    /** 触发方式：SCHEDULE=定时触发，MANUAL=手动触发。 */
    private String triggerType;

    /** 本次同步开始时间。 */
    private LocalDateTime startTime;

    /** 本次同步结束时间。 */
    private LocalDateTime endTime;

    /** 执行状态：SUCCESS=全部成功，PARTIAL=部分失败，FAILED=全部失败或执行异常。 */
    private String status;

    /** 处理总行数。 */
    private Integer totalCount;

    /** 成功行数。 */
    private Integer successCount;

    /** 失败行数。 */
    private Integer failCount;

    /** 失败摘要文本，截断到合理长度，非完整堆栈。 */
    private String failSummary;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
