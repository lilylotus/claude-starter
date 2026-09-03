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
 * 流程模型一次不可变发布快照持久化实体，对应表 {@code tab_wf_process_definition}。节点
 * 审批人规则（{@code tab_wf_node_assignee_rule}）与流程实例（{@code tab_wf_process_instance}）
 * 均关联本表主键而非可变的 {@code flowableDefinitionKey}，确保旧版本运行中实例不受新版本
 * 发布影响。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_process_definition")
public class ProcessDefinitionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程模型 id，关联 {@code tab_wf_process_model.id}。 */
    private Long processModelId;

    /** 业务侧流程编码，冗余自流程模型，避免联表查询。 */
    private String processCode;

    /** 同一流程模型下的版本号，自增。 */
    private Integer version;

    /** DSL schemaVersion，历史 v1 定义为 1，DSL v2 为 2。 */
    private Integer schemaVersion;

    /** 编译该版本时使用的编译器版本号。 */
    private String compilerVersion;

    /** DSL 快照摘要（如 SHA-256），供试运行报告/审核记录比对是否失效。 */
    private String modelDigest;

    /** 编译产物 BPMN XML 快照，只读导出用。 */
    private String xmlSnapshot;

    /** BPMN XML 快照摘要。 */
    private String xmlDigest;

    /** 节点 id 到 BPMN activityId 的映射快照（JSON）。 */
    private String nodeMappingJson;

    /** 发布时刻节点审批人规则的完整快照（JSON），审计与试运行报告比对用。 */
    private String ruleSnapshotJson;

    /** 绑定的表单版本 id，关联 {@code tab_wf_form_version.id}。 */
    private Long formVersionId;

    /** Flowable 流程定义 key（BPMN {@code process} 的 {@code id}）。 */
    private String flowableDefinitionKey;

    /** Flowable 部署后生成的流程定义 id；默认两级流程随 Flyway 预置的种子数据行在应用启动
     *  完成部署前该字段为空，由启动阶段的回填逻辑写入。 */
    private String flowableDefinitionId;

    /** 发布时刻的 DSL 快照（JSON），只读，不随后续草稿编辑变化。 */
    private String modelJsonSnapshot;

    /** 状态：{@code PUBLISHED}/{@code DISABLED}；{@code DISABLED} 表示该版本已被挂起，不再
     *  接受新发起，但不影响其运行中的流程实例。 */
    private String status;

    /** 发布人。 */
    private String publishedBy;

    /** 发布时间。 */
    private LocalDateTime publishedTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
