package cn.nihility.rbac.common.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * multipart 上传大小守卫配置，绑定前缀 {@code rbac.upload}（add-video-management change
 * design.md Decision 4）。全局 {@code spring.servlet.multipart} 上限为支持大视频上传放开到
 * 2GB 后，除 {@link #largeUploadPaths} 外的 multipart 请求仍按 {@link #defaultMaxRequestSize}
 * 限制，保持既有上传接口原 10MB 上限语义。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.upload")
public class UploadLimitProperties {

    /** 普通 multipart 请求的最大请求体大小，默认 10MB。 */
    private DataSize defaultMaxRequestSize = DataSize.ofMegabytes(10);

    /** 允许超过默认上限、仅受全局 multipart 上限约束的上传路径（Ant 风格）。 */
    private List<String> largeUploadPaths = new ArrayList<>(List.of("/api/videos/upload"));
}
