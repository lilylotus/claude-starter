package cn.nihility.rbac.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.core.io.Resource;

/**
 * 流式播放时由业务层返回给 Controller 的视频文件资源与内容类型，Controller 据此构造
 * 支持 HTTP Range 的响应。
 */
@Getter
@Builder
@AllArgsConstructor
public class VideoStreamResource {

    /** 磁盘上的视频文件资源。 */
    private final Resource resource;

    /** 内容类型，如 video/mp4。 */
    private final String contentType;
}
