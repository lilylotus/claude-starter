package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建群聊的请求参数。当前登录用户自动成为群主并计入初始成员，不需要在
 * {@code memberUserIds} 中重复携带自己。
 */
@Getter
@Setter
@Schema(description = "创建群聊请求参数")
public class CreateGroupConversationRequest {

    /** 群聊名称。 */
    @NotBlank(message = "群聊名称不能为空")
    @Size(max = 128, message = "群聊名称长度不能超过 128 个字符")
    @Schema(description = "群聊名称")
    private String name;

    /** 初始成员用户 id 列表（不含创建者自己，创建者自动加入）。 */
    @NotEmpty(message = "初始成员不能为空")
    @Schema(description = "初始成员用户 id 列表（不含创建者自己）")
    private List<Long> memberUserIds;
}
