package cn.nihility.rbac.image.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片模块相关配置，绑定前缀 {@code rbac.image}（add-image-gallery change design.md
 * Decision 4）：图片保存根目录路径，独立于 {@code rbac.file-upload}，互不影响。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.image")
public class ImageProperties {

    /** 图片保存根目录（相对路径相对于应用当前工作目录解析），默认 {@code images}。 */
    private String directory = "images";
}
