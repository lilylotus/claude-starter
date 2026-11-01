package cn.nihility.rbac.video.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 视频模块相关配置，绑定前缀 {@code rbac.video}（add-video-management change design.md
 * Decision 2/3）：视频保存根目录、单个视频大小上限、播放凭证有效期，独立于
 * {@code rbac.image}/{@code rbac.file-upload}，互不影响。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.video")
public class VideoProperties {

    /** 视频保存根目录（相对路径相对于应用当前工作目录解析），默认 {@code videos}。 */
    private String directory = "videos";

    /** 单个视频文件大小上限，默认 2GB；须不大于 {@code spring.servlet.multipart.max-file-size}。 */
    private DataSize maxFileSize = DataSize.ofGigabytes(2);

    /** 播放凭证有效期（秒），每次流式访问校验通过后按该时长滑动续期，默认 600 秒。 */
    private long playTicketTtlSeconds = 600;
}
