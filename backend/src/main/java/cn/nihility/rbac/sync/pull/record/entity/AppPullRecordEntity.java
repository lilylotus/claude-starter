package cn.nihility.rbac.sync.pull.record.entity;

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
 * 应用拉取变更数据请求记录持久化实体，对应表 {@code tab_app_pull_record}
 * （add-app-sync-notify-pull-logs change design.md Decision 2）。记录外部应用调用分页拉取
 * 接口的请求，仅用于问题排查/展示，不驱动任何业务逻辑。{@code pullMode} 字段随拉取接口
 * 合并为一个统一的分页拉取接口而删除，只剩一种拉取方式，该字段失去意义
 * （app-sync-drop-changelog change design.md）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_pull_record")
public class AppPullRecordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起拉取的应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 请求的数据类型。 */
    private String dataType;

    /** 请求参数摘要：页码、每页大小，以及本次传入的过滤条件概要。 */
    private String requestSummary;

    /** 本次返回的记录条数。 */
    private Integer resultCount;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
