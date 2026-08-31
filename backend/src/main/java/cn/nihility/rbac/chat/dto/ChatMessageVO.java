package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 历史消息视图对象，供会话消息收发界面渲染使用。
 */
@Getter
@Setter
@Schema(description = "历史消息")
public class ChatMessageVO {

    /** 消息主键 id。 */
    @Schema(description = "消息主键 id")
    private Long id;

    /** 客户端生成的消息幂等 id。 */
    @Schema(description = "客户端生成的消息幂等 id")
    private String msgId;

    /** 所属会话 id。 */
    @Schema(description = "所属会话 id")
    private Long conversationId;

    /** 会话内消息序号，前端应按该字段排序展示，而不是仅依赖发送时间。 */
    @Schema(description = "会话内消息序号，前端应按该字段排序展示")
    private Long conversationSeq;

    /** 发送者用户 id。 */
    @Schema(description = "发送者用户 id")
    private Long senderId;

    /** 发送者展示名（姓名（账号编码））。 */
    @Schema(description = "发送者展示名")
    private String senderName;

    /** 消息内容类型：1=文本。 */
    @Schema(description = "消息内容类型：1=文本")
    private Integer msgType;

    /** 消息内容（敏感词过滤/替换后落库的内容）。 */
    @Schema(description = "消息内容")
    private String content;

    /** 是否命中过敏感词。 */
    @Schema(description = "是否命中过敏感词")
    private Boolean filtered;

    /** 发送时间。 */
    @Schema(description = "发送时间")
    private LocalDateTime sendTime;
}
