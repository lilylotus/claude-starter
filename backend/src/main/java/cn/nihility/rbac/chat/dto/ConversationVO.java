package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话列表视图对象：单聊展示对方用户的展示名，群聊展示群名称，附带最近一条消息摘要，
 * 供前端会话列表界面渲染使用。
 */
@Getter
@Setter
@Schema(description = "会话列表项")
public class ConversationVO {

    /** 会话 id。 */
    @Schema(description = "会话 id")
    private Long id;

    /** 会话类型：1=单聊，2=群聊。 */
    @Schema(description = "会话类型：1=单聊，2=群聊")
    private Integer conversationType;

    /** 展示名称：群聊为群名称，单聊为对方用户展示名（姓名（账号编码））。 */
    @Schema(description = "展示名称：群聊为群名称，单聊为对方用户展示名")
    private String name;

    /** 当前在会话中的成员数量。 */
    @Schema(description = "当前在会话中的成员数量")
    private Integer memberCount;

    /** 最近一条消息内容（敏感词过滤后），无消息时为空。 */
    @Schema(description = "最近一条消息内容")
    private String lastMessageContent;

    /** 最近一条消息发送者用户 id。 */
    @Schema(description = "最近一条消息发送者用户 id")
    private Long lastMessageSenderId;

    /** 最近一条消息发送时间。 */
    @Schema(description = "最近一条消息发送时间")
    private LocalDateTime lastMessageSendTime;

    /** 会话创建时间。 */
    @Schema(description = "会话创建时间")
    private LocalDateTime createTime;
}
