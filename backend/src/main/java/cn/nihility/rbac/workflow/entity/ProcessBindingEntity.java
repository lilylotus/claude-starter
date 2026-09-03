package cn.nihility.rbac.workflow.entity;

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
 * 业务绑定持久化实体，对应表 {@code tab_wf_process_binding}。绑定维度
 * {@code (bizType, operationType, scopeType, scopeId)} 唯一，确定性选择：精确组织 →
 * 最近祖先组织 → 全局；{@code scopeType=GLOBAL} 时 {@code scopeId} 固定为 0 哨兵值，不用
 * {@code NULL} 规避唯一性约束（production-approval-lifecycle change design.md
 * Decision 4）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_process_binding")
public class ProcessBindingEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    private String bizType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    private String operationType;

    /** 绑定范围类型：ORG（精确组织）/GLOBAL（全局兜底）。 */
    private String scopeType;

    /** 范围内组织 id，{@code scopeType=GLOBAL} 时固定为 0。 */
    private Long scopeId;

    /** 绑定的流程定义 id，关联 {@code tab_wf_process_definition.id}，显式版本。 */
    private Long definitionId;

    /** 该绑定下发起申请使用的执行模式：LEGACY_SYNC/RELIABLE_ASYNC。 */
    private String executionMode;

    /** 乐观锁修订号，切换绑定版本时自增。 */
    private Long revision;

    /** 是否启用。 */
    private Boolean enabled;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
