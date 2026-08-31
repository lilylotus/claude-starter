package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 添加群成员的请求参数。
 */
@Getter
@Setter
@Schema(description = "添加群成员请求参数")
public class AddConversationMemberRequest {

    /** 待添加的成员用户 id 列表；已在群内的成员会被忽略（幂等）。 */
    @NotEmpty(message = "待添加成员不能为空")
    @Schema(description = "待添加的成员用户 id 列表")
    private List<Long> userIds;
}
