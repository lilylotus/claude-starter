package cn.nihility.rbac.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.util.RsaJdkUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 校验 {@code application.yml} 里 {@code rbac.user.login.public-key}/{@code private-key}
 * 默认值本身是一套可用的密钥对（能够互相加解密），而不仅仅是"看起来像"合法的 Base64 字符串。
 * <p>
 * 背景：{@link cn.nihility.rbac.auth.service.impl.AuthServiceImplTest} 等单元测试用
 * {@link RsaJdkUtils#generateKeyPair()} 在内存里现生成密钥对，从未真正加载过配置文件里的
 * 默认密钥对，因此曾经出现过配置文件里的默认私钥实际是 PKCS#1 格式（而不是
 * {@link RsaJdkUtils#loadPrivateKey} 要求的 PKCS#8 格式）却没有被任何测试发现的情况——
 * 只有真正启动应用、调用登录接口时才会在运行期报错。本测试通过 {@code @SpringBootTest}
 * 加载真实的 {@link RbacLoginProperties} 绑定结果，直接做一次公钥加密、私钥解密的往返校验，
 * 在编译期/CI 阶段就能捕获这类默认配置值损坏的问题。
 */
@SpringBootTest
class RbacLoginPropertiesKeyPairTest {

    @Autowired
    private RbacLoginProperties loginProperties;

    @Test
    void defaultKeyPairShouldRoundTripEncryptAndDecrypt() {
        String plainText = "smoketest001";

        String cipherText = RsaJdkUtils.encrypt(plainText, loginProperties.getPublicKey());
        String decrypted = RsaJdkUtils.decrypt(cipherText, loginProperties.getPrivateKey());

        assertThat(decrypted).isEqualTo(plainText);
    }
}
