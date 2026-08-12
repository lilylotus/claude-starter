package cn.nihility.rbac.app.sync.entity;

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
 * 应用同步数据域配置持久化实体，对应表 {@code tab_app_sync_domain_config}。每个应用固定
 * 5 行，分别对应组织/用户/应用/角色/字典五个数据域（{@link cn.nihility.rbac.app.sync.constant.SyncDomain}），
 * 每行携带"是否启用"与"每次拉取分页大小"两个属性（app-sync-field-mapping change design.md
 * Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_sync_domain_config")
public class AppSyncDomainConfigEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 数据域：ORG/USER/APP/ROLE/DICT。 */
    private String syncDomain;

    /** 是否允许同步该数据域。 */
    private Boolean syncEnabled;

    /** 每次拉取分页大小。 */
    private Integer pageSize;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
