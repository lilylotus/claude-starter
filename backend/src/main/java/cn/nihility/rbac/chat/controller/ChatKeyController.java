package cn.nihility.rbac.chat.controller;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.chat.dto.ChatKeyRegisterRequest;
import cn.nihility.rbac.chat.dto.ChatUserKeyVO;
import cn.nihility.rbac.chat.service.ChatUserKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天密钥目录接口：单聊端到端加密的身份公钥注册/更新与查询（design.md Decision 4）。
 * 业务处理逻辑参见 {@link ChatUserKeyService}；私钥全程只保存在客户端浏览器本地，
 * 本接口只收发公钥与其留痕用指纹，服务端不参与任何加解密运算。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聊天密钥目录", description = "单聊端到端加密身份公钥的注册与查询接口")
public class ChatKeyController {

    /** 聊天密钥目录业务逻辑接口。 */
    private final ChatUserKeyService chatUserKeyService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * 注册或更新本人聊天身份公钥。
     *
     * @param request 注册/更新请求
     * @return 注册/更新后的密钥目录记录
     */
    @Operation(summary = "注册/更新本人聊天身份公钥",
            description = "只能写入当前登录用户自己的记录（用户 id 从登录态解析）；若此前已注册过则覆盖更新")
    @PostMapping("/api/v1/chat/keys/me")
    public ChatUserKeyVO registerMyKey(@Valid @RequestBody ChatKeyRegisterRequest request) {
        return chatUserKeyService.registerMyKey(currentOperatorService.resolveUserId(), request);
    }

    /**
     * 查询目标用户的聊天身份公钥。
     *
     * @param userId 目标用户 id
     * @return 密钥目录记录
     */
    @Operation(summary = "查询目标用户聊天身份公钥",
            description = "发起单聊/首次建立加密会话前调用；目标用户尚未注册时返回业务错误")
    @GetMapping("/api/v1/chat/keys/{userId}")
    public ChatUserKeyVO getUserKey(
            @Parameter(description = "目标用户 id", required = true)
            @PathVariable Long userId) {
        return chatUserKeyService.getUserKey(userId);
    }
}
