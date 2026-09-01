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
 * 聊天会话成员持久化实体，对应表 {@code tab_chat_conversation_member}，覆盖单聊固定两条
 * 成员记录与群聊的多条成员记录，{@code conversationId + userId} 唯一。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_conversation_member")
public class ConversationMemberEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 id，关联 {@code tab_chat_conversation.id}。 */
    private Long conversationId;

    /** 成员用户 id，关联 {@code tab_user.id}。 */
    private Long userId;

    /** 成员角色，见 {@link cn.nihility.rbac.chat.constant.ConversationMemberRole}。 */
    private Integer role;

    /** 加入时间。 */
    private LocalDateTime joinedTime;

    /** 状态，见 {@link cn.nihility.rbac.chat.constant.ConversationMemberStatus}。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
