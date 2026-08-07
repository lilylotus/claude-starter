package cn.nihility.rbac.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用对外接口凭证相关配置，绑定前缀 {@code rbac.app-secret}：SM4 对称加密主密钥，
 * 用于 SecretKey 落库前的加密/解密（app-api-credentials-config change design.md Decision 3）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.app-secret")
public class AppSecretProperties {

    /** SM4 密钥（Base64 编码，16 字节），供 {@code Sm4JdkUtils} 加解密应用 SecretKey 使用。 */
    private String sm4Key;
}
