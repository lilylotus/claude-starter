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
}
