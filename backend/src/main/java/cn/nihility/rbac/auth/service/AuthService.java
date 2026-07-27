package cn.nihility.rbac.auth.service;

import cn.nihility.rbac.auth.dto.ChangePasswordRequest;
import cn.nihility.rbac.auth.dto.LoginRequest;
import cn.nihility.rbac.auth.dto.LoginResponse;
import cn.nihility.rbac.auth.dto.PublicKeyVO;
import cn.nihility.rbac.auth.dto.RefreshRequest;
import cn.nihility.rbac.auth.dto.RefreshResponse;

/**
 * 登录认证业务逻辑接口：获取登录公钥、口令登录、access-key 刷新、修改密码。
 */
public interface AuthService {

    /**
     * 查询当前生效的 RSA 公钥，供客户端登录前加密账号、密码。
     *
     * @return 公钥信息
     */
    PublicKeyVO getPublicKey();

    /**
     * 口令登录：RSA 私钥解密账号密码密文后校验，成功则签发 access-key/refresh-key。
     *
     * @param request 登录请求（账号、密码均为 RSA 密文）
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request);

    /**
     * 使用有效的 refresh-key 换取新的 access-key。
     *
     * @param request 刷新请求
     * @return 刷新响应
     */
    RefreshResponse refresh(RefreshRequest request);

    /**
     * 修改当前登录用户（取自 {@link cn.nihility.rbac.auth.context.CurrentUserContext}）的密码，
     * 校验旧密码正确后更新摘要，若原本处于首登待改密状态则一并清除。
     *
     * @param request 修改密码请求
     */
    void changePassword(ChangePasswordRequest request);
}
