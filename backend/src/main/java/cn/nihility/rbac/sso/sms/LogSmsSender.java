package cn.nihility.rbac.sso.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link SmsSender} 的日志占位实现（add-sso-login-methods change design.md Decision 6）：
 * 仅把验证码写入应用日志，不做任何真实网络调用，仅用于非生产环境联调/演示。等接入真实短信
 * 厂商（阿里云/腾讯云等）时，新增另一个实现类并把 Spring 装配切到那个实现即可替换本类，
 * 本类到时候可以保留作为本地开发默认值或删除，留给那次 change 决定。
 */
@Slf4j
@Component
public class LogSmsSender implements SmsSender {

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(String mobile, String code) {
        log.info("[短信验证码-Mock 发送] 手机号={}，验证码={}（仅写日志，未接入真实短信厂商）", mobile, code);
    }
}
