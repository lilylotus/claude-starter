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
 * 离线消息队列持久化实体，对应表 {@code tab_chat_message_offline}。消息发送时若接收者
 * 当前没有任何在线连接，写入一条本实体记录；接收者重新上线并完成认证后按序补偿推送，
 * 推送成功后标记 {@code delivered = true}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_message_offline")
public class ChatMessageOfflineEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的消息 id，关联 {@code tab_chat_message.id}。 */
    private Long messageId;

    /** 接收者用户 id，关联 {@code tab_user.id}。 */
    private Long receiverId;

    /** 是否已补偿推送。 */
    private Boolean delivered;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
