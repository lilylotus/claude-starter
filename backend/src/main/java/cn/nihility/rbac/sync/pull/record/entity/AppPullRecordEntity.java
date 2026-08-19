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
 * （add-app-sync-notify-pull-logs change design.md Decision 2）。记录外部应用调用现有
 * 两个拉取接口（按数据类型+id 拉取、按序列号批量拉取）的请求，仅用于问题排查/展示，不驱动
 * 任何业务逻辑。
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

    /** 拉取方式：BY_ID=按id拉取，BY_SEQUENCE=按序列号拉取。 */
    private String pullMode;

    /** 请求的数据类型，按序列号拉取未传时为空。 */
    private String dataType;

    /** 请求参数摘要：按id拉取记bizId个数，按序列号拉取记fromSequence/limit。 */
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
