package cn.nihility.rbac.sso.sms;

/**
 * 短信发送可插拔接口（add-sso-login-methods change design.md Decision 6）：只负责把一个
 * 已经生成好的验证码发送到指定手机号，不关心验证码的生成/存储/校验规则。当前唯一实现
 * {@link LogSmsSender} 只写应用日志、不接入任何真实短信厂商；后续接入某个厂商时新增一个
 * 实现类并把 Spring 装配切到那个实现即可。
 */
public interface SmsSender {

    /**
     * 发送短信验证码。
     *
     * @param mobile 接收手机号
     * @param code   验证码明文
     */
    void send(String mobile, String code);
}
