package cn.nihility.rbac.sync.changelog.entity;

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
 * 应用数据变更记录持久化实体，对应表 {@code tab_app_data_change_log}
 * （app-sync-notify-pull-api change design.md Decision 6）。{@code id} 自增列全局单调
 * 递增，直接对外充当序列号（{@code sequence}），不额外维护一份计数器；只追加不更新不删除。
 * 每次业务变更事件按候选应用（及组织/用户/任职三个数据域的按应用同步范围过滤后）各物化
 * 一条记录，{@code appRefId} 即该记录归属的应用（app-sync-org-scope-and-app-change-log
 * change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_data_change_log")
public class AppDataChangeLogEntity {

    /** 主键 id，全局单调递增，直接对外充当序列号。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 该记录归属的目标应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE（不含 DICT）。 */
    private String dataType;

    /** 变更对象主键 id。 */
    private Long bizId;

    /** 操作类型码值，复用 {@code OperationType} 的 1/2/3/4/5。 */
    private Integer operationType;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
