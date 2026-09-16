package cn.nihility.rbac.fileupload.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传相关配置，绑定前缀 {@code rbac.file-upload}（add-file-upload change
 * design.md Decision 4）：文件保存根目录路径。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.file-upload")
public class FileUploadProperties {

    /** 文件保存根目录（相对路径相对于应用当前工作目录解析），默认 {@code upload}。 */
    private String directory = "upload";
}
