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
 * 流程模型主数据持久化实体，对应表 {@code tab_wf_process_model}。是一个流程的"身份"，
 * 可反复编辑草稿；设计器草稿/发布/下线能力属于后续批次范围，本次仅随建表预留该实体。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_process_model")
public class ProcessModelEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务侧流程编码，唯一，如 {@code MASTER_DATA_APPROVAL}。 */
    private String processCode;

    /** 流程名称。 */
    private String processName;

    /** 当前草稿 DSL（JSON），{@code status} 为 {@code PUBLISHED}/{@code DISABLED} 时仍可继续
     *  编辑覆盖，表示"下一次发布的候选内容"。 */
    private String modelJson;

    /** 草稿修订号，乐观锁，每次保存草稿自增。 */
    private Long draftRevision;

    /** 草稿状态：{@code EDITING}/{@code IN_REVIEW}/{@code APPROVED_FOR_RELEASE}。 */
    private String draftStatus;

    /** 状态：{@code DRAFT}/{@code PUBLISHED}/{@code DISABLED}。 */
    private String status;

    /** 当前生效的已发布版本，关联 {@code tab_wf_process_definition.id}；{@code DRAFT} 状态下
     *  为空或指向上一个仍在生效的版本。 */
    private Long currentDefinitionId;

    /** 是否接受新发起，与草稿编辑/发布态解耦，保存草稿不影响该开关。 */
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
