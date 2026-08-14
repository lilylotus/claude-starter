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
 * 上游数据同步执行记录明细持久化实体，对应表 {@code tab_upstream_sync_record_detail}。
 * 粒度对齐"一次执行记录下的一行原始上游数据"，成功/失败均记录
 * （upstream-sync-record-improvements change design.md Decision 2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_upstream_sync_record_detail")
public class UpstreamSyncRecordDetailEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属同步执行记录 id，关联 {@code tab_upstream_sync_record.id}。 */
    private Long syncRecordId;

    /** 所属上游数据源 id，冗余自所属执行记录，供按数据源级联删除，不需要联表。 */
    private Long sourceId;

    /** 本次执行内该行的序号，从 1 开始。 */
    private Integer rowNo;

    /** 该行的原始上游数据（取数阶段的原始行，JSON 文本）。 */
    private String rowData;

    /** 该行处理状态：SUCCESS=成功，FAILED=失败。 */
    private String status;

    /** 失败原因，仅 {@code status=FAILED} 时有值。 */
    private String failReason;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
