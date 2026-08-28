package cn.nihility.rbac.sync.cursor.entity;

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
 * 应用同步服务端投递水位持久化实体，对应表 {@code tab_app_sync_cursor}：按
 * {@code (appRefId, entityType)} 记录服务端最近一次 {@code /changes} 成功响应的
 * {@code nextSeq}（{@code lastDeliveredSeq}）。该值只表示"服务端已返回到哪里"，不代表调用方
 * 消费确认（app-sync-changelog-pull change design.md Decision 9）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_sync_cursor")
public class AppSyncCursorEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 同步实体类型：ORG/USER/POSITION/APP/ROLE。 */
    private String entityType;

    /** 最近一次成功响应返回的 {@code nextSeq}，只增不减。 */
    private Long lastDeliveredSeq;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
