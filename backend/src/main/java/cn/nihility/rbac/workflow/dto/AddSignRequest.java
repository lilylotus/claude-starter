package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 加签请求体。
 */
@Getter
@Setter
@Schema(description = "加签请求")
public class AddSignRequest {

    /** 新增的候选审批人用户 id 列表，必填且不能为空。 */
    @NotEmpty(message = "加签用户不能为空")
    @Schema(description = "新增候选审批人用户 id 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> addUserIds;

    /** 加签说明，可为空。 */
    @Size(max = 500, message = "加签说明长度不能超过 500 个字符")
    @Schema(description = "加签说明")
    private String remark;
}
