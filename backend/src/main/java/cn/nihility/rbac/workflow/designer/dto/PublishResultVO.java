package cn.nihility.rbac.workflow.designer.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 发布流程模型的结果视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResultVO {

    /** 新生成的流程定义版本行 id（{@code tab_wf_process_definition.id}）。 */
    private Long processDefinitionId;

    /** 版本号，同一流程模型下自增。 */
    private Integer version;

    /** Flowable 部署产生的流程定义 key。 */
    private String flowableDefinitionKey;

    /** Flowable 部署产生的流程定义 id。 */
    private String flowableDefinitionId;

    /** 发布时间。 */
    private LocalDateTime publishedTime;
}
