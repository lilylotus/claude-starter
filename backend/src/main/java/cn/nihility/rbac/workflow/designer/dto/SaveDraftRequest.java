package cn.nihility.rbac.workflow.designer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存流程模型草稿请求体。
 */
@Getter
@Setter
public class SaveDraftRequest {

    /** 当前草稿 Workflow JSON DSL 文本。 */
    @Schema(description = "Workflow JSON DSL 文本")
    @NotBlank(message = "流程草稿 DSL 不能为空")
    private String modelJson;

    /** 期望的当前草稿修订号，用于乐观锁冲突检测；不传则不做检测（兼容旧客户端）。 */
    @Schema(description = "期望的当前草稿修订号，乐观锁冲突检测用")
    private Long expectedRevision;
}
