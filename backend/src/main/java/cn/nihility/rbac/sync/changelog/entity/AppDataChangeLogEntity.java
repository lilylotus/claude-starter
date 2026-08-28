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

/** 全局应用数据变更流水实体。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_data_change_log")
public class AppDataChangeLogEntity {

    /** 数据库自增的严格递增游标。 */
    @TableId(type = IdType.AUTO)
    private Long changeSeq;
    /** 雪花事件标识。 */
    private Long eventId;
    /** 同步实体类型。 */
    private String entityType;
    /** 实体 id。 */
    private Long entityId;
    /** 操作类型。 */
    private String operationType;
    /** 实体结果版本。 */
    private Long entityVersion;
    /** 变更前组织范围路径。 */
    private String orgScopePathBefore;
    /** 变更后组织范围路径。 */
    private String orgScopePathAfter;
    /** 业务变更时间。 */
    private LocalDateTime changeTime;
    /** 创建人。 */
    private String createBy;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新人。 */
    private String updateBy;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
