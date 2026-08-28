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
 * 应用同步全局元数据持久化实体，对应表 {@code tab_app_sync_metadata}：全局键值表，当前唯一
 * 用途是保存变更流水保留窗口下界游标 {@code CHANGE_LOG_RETENTION_FLOOR_SEQ}（十进制字符串，
 * 初始为 {@code "0"}），供增量拉取接口判断调用方传入的 {@code sinceSeq} 是否已早于保留窗口
 * （app-sync-changelog-pull change design.md Decision 8/9）。主键 {@code metadataKey} 为
 * 业务侧手工指定的字符串（非自增），{@link IdType#INPUT} 保证 MyBatis-Plus 不为其生成值。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_sync_metadata")
public class AppSyncMetadataEntity {

    /** 元数据键，主键，业务侧手工指定（如 {@code CHANGE_LOG_RETENTION_FLOOR_SEQ}）。 */
    @TableId(value = "metadata_key", type = IdType.INPUT)
    private String metadataKey;

    /** 元数据值。 */
    private String metadataValue;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
