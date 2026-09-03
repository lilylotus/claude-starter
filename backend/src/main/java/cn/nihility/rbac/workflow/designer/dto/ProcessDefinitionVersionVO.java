package cn.nihility.rbac.workflow.designer.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 流程定义版本历史视图对象，对应一行不可变的 {@code tab_wf_process_definition} 发布快照。
 * 历史版本不提供编辑入口，{@code modelJsonSnapshot} 只读展示。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionVersionVO {

    /** 流程定义版本行 id。 */
    private Long id;

    /** 版本号。 */
    private Integer version;

    /** 状态：{@code PUBLISHED}/{@code DISABLED}。 */
    private String status;

    /** 发布人。 */
    private String publishedBy;

    /** 发布时间。 */
    private LocalDateTime publishedTime;

    /** 发布时刻的 DSL 快照（只读）。 */
    private String modelJsonSnapshot;
}
