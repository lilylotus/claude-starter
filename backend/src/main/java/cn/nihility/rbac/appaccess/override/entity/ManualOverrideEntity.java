package cn.nihility.rbac.appaccess.override.entity;

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
 * 人工例外持久化实体，对应表 {@code tab_app_access_manual_override}。每个
 * {@code userId+appId} 组合同一时刻至多一条记录（{@code UNIQUE KEY(user_id, app_id)}），
 * 重复提交是更新已有记录（{@code overrideType}/{@code remark}），不是新增一行，与
 * {@code tab_app_access_policy_grant} 物理隔离（design.md Decision 2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_manual_override")
public class ManualOverrideEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，关联 {@code tab_user.id}。 */
    private Long userId;

    /** 应用 id，关联 {@code tab_app.id}。 */
    private Long appId;

    /** 例外类型：{@code GRANT}=手动追加授权，{@code DENY}=手动收回授权。 */
    private String overrideType;

    /** 备注。 */
    private String remark;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
