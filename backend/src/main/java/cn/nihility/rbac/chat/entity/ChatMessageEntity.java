package cn.nihility.rbac.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天消息持久化实体，对应表 {@code tab_chat_message}。{@code msgId} 是客户端生成的幂等
 * 标识，唯一索引兜底跨重启去重（design.md Decision 6）；{@code conversationSeq} 是会话内
 * 严格递增、不重复的展示排序依据（design.md Decision 7）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_message")
public class ChatMessageEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端生成的消息幂等 id，唯一索引。 */
    private String msgId;

    /** 所属会话 id，关联 {@code tab_chat_conversation.id}。 */
    private Long conversationId;

    /** 会话内严格递增、不重复的消息序号。 */
    private Long conversationSeq;

    /** 发送者用户 id，关联 {@code tab_user.id}。 */
    private Long senderId;

    /** 消息内容类型，见 {@link cn.nihility.rbac.chat.constant.ChatMessageType}。 */
    private Integer msgType;

    /** 消息内容（敏感词过滤/替换后落库，本阶段服务端可见明文）。 */
    private String content;

    /** 是否命中过敏感词。 */
    private Boolean filtered;

    /** 发送时间。 */
    private LocalDateTime sendTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
