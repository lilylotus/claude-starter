package cn.nihility.rbac.chat.controller;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.chat.dto.AddConversationMemberRequest;
import cn.nihility.rbac.chat.dto.ChatMessageVO;
import cn.nihility.rbac.chat.dto.ConversationMemberVO;
import cn.nihility.rbac.chat.dto.ConversationVO;
import cn.nihility.rbac.chat.dto.CreateGroupConversationRequest;
import cn.nihility.rbac.chat.service.ChatMessageService;
import cn.nihility.rbac.chat.service.ConversationService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天会话管理接口：会话列表、历史消息分页查询、创建群聊、群成员管理。业务处理逻辑
 * 参见 {@link ConversationService}/{@link ChatMessageService}；单聊/群聊消息的实时收发
 * 走 Netty 聊天网关（独立端口），不经过本 Controller。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聊天会话管理", description = "会话列表、历史消息、群聊创建与成员管理接口")
public class ConversationController {

    /** 会话管理业务逻辑接口。 */
    private final ConversationService conversationService;

    /** 消息发送与历史查询业务逻辑接口。 */
    private final ChatMessageService chatMessageService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * 查询当前用户参与的会话列表。
     *
     * @return 会话列表（单聊+群聊），按最近消息时间倒序
     */
    @Operation(summary = "查询我的会话列表", description = "返回当前登录用户参与的全部单聊+群聊会话，按最近消息时间倒序")
    @GetMapping("/api/chat/conversations")
    public List<ConversationVO> myConversations() {
        return conversationService.listMyConversations(currentOperatorService.resolveUserId());
    }

    /**
     * 分页查询会话历史消息。
     *
     * @param conversationId 会话 id
     * @param beforeSeq      游标，不传表示查最新一页
     * @param pageSize       每页条数
     * @return 历史消息分页结果
     */
    @Operation(summary = "分页查询会话历史消息", description = "按 conversationSeq 游标向历史翻页，不传 beforeSeq 时"
            + "返回最新一页；返回结果按 conversationSeq 降序（最新在前），前端展示时需自行反转为时间正序")
    @GetMapping("/api/chat/conversations/{conversationId}/messages")
    public PageResult<ChatMessageVO> messages(
            @PathVariable Long conversationId,
            @Parameter(description = "游标：只返回 conversationSeq 小于该值的历史消息，不传表示查最新一页")
            @RequestParam(required = false) Long beforeSeq,
            @Parameter(description = "每页条数，默认 20，最大 100")
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return chatMessageService.listMessages(currentOperatorService.resolveUserId(), conversationId, beforeSeq,
                pageSize);
    }

    /**
     * 创建群聊。
     *
     * @param request 创建请求
     * @return 创建后的会话详情
     */
    @Operation(summary = "创建群聊", description = "创建者自动成为群主并计入初始成员")
    @PostMapping("/api/chat/conversations/group")
    public ConversationVO createGroup(@Valid @RequestBody CreateGroupConversationRequest request) {
        return conversationService.createGroupConversation(currentOperatorService.resolveUserId(), request);
    }

    /**
     * 查询群成员列表。
     *
     * @param conversationId 会话 id
     * @return 成员列表
     */
    @Operation(summary = "查询群成员列表", description = "调用方必须是该会话的当前成员")
    @GetMapping("/api/chat/conversations/{conversationId}/members")
    public List<ConversationMemberVO> members(@PathVariable Long conversationId) {
        return conversationService.listMembers(currentOperatorService.resolveUserId(), conversationId);
    }

    /**
     * 添加群成员。
     *
     * @param conversationId 会话 id
     * @param request        添加请求
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "添加群成员", description = "已在群内的成员按幂等处理，不重复添加")
    @PostMapping("/api/chat/conversations/{conversationId}/members")
    public Result<Void> addMembers(
            @PathVariable Long conversationId,
            @Valid @RequestBody AddConversationMemberRequest request) {
        conversationService.addMembers(currentOperatorService.resolveUserId(), conversationId, request.getUserIds());
        return Result.success();
    }

    /**
     * 移除群成员或主动退出群聊。
     *
     * @param conversationId 会话 id
     * @param targetUserId   目标用户 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "移除群成员/退出群聊", description = "targetUserId 等于当前用户即为主动退出群聊，"
            + "否则需群主权限，且不能移除群主自己")
    @DeleteMapping("/api/chat/conversations/{conversationId}/members/{targetUserId}")
    public Result<Void> removeMember(@PathVariable Long conversationId, @PathVariable Long targetUserId) {
        conversationService.removeMember(currentOperatorService.resolveUserId(), conversationId, targetUserId);
        return Result.success();
    }
}
