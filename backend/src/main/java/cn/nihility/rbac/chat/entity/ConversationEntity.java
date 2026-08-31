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
 * 聊天会话持久化实体，对应表 {@code tab_chat_conversation}，覆盖单聊与群聊两种类型
 * （{@code conversationType} 区分）。{@code nextSeq} 是会话级消息序号生成器，取号必须在
 * 事务内配合 {@code SELECT ... FOR UPDATE} 行锁使用，不允许绕过该流程直接更新。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_chat_conversation")
public class ConversationEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话类型：1=单聊，2=群聊，见 {@link cn.nihility.rbac.chat.constant.ConversationType}。 */
    private Integer conversationType;

    /** 群聊名称，单聊为空。 */
    private String name;

    /** 下一个可分配的会话内消息序号。 */
    private Long nextSeq;

    /** 状态，见 {@link cn.nihility.rbac.chat.constant.ConversationStatus}。 */
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
