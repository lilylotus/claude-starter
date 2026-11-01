package cn.nihility.rbac.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 视频播放凭证返回对象（add-video-management change design.md Decision 3）：前端用
 * {@code ticket} 拼接流式播放地址作为 {@code <video src>}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoPlayTicketVO {

    /** 随机播放凭证，仅对申请时的视频有效。 */
    @Schema(description = "随机播放凭证，仅对申请时的视频有效")
    private String ticket;

    /** 凭证有效期（秒），持续播放期间每次访问会滑动续期。 */
    @Schema(description = "凭证有效期（秒），持续播放期间每次访问会滑动续期")
    private Long expiresInSeconds;
}
