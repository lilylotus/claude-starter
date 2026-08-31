package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话成员视图对象，供群成员管理界面展示使用。
 */
@Getter
@Setter
@Schema(description = "会话成员")
public class ConversationMemberVO {

    /** 成员用户 id。 */
    @Schema(description = "成员用户 id")
    private Long userId;

    /** 成员展示名（姓名（账号编码）），查不到用户时为"未知用户"。 */
    @Schema(description = "成员展示名")
    private String userName;

    /** 成员角色：1=群主，2=普通成员。 */
    @Schema(description = "成员角色：1=群主，2=普通成员")
    private Integer role;

    /** 加入时间。 */
    @Schema(description = "加入时间")
    private LocalDateTime joinedTime;

    /** 状态：2000=在会话中，3000=已退出/被移出。 */
    @Schema(description = "状态：2000=在会话中，3000=已退出/被移出")
    private Integer status;
}
