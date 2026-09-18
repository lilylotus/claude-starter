package cn.nihility.rbac.chat.service;

import cn.nihility.rbac.chat.dto.ChatKeyRegisterRequest;
import cn.nihility.rbac.chat.dto.ChatUserKeyVO;

/**
 * 聊天密钥目录业务逻辑接口：单聊端到端加密的身份公钥注册/更新与查询（design.md
 * Decision 4）。服务端只存储、透传公钥字节串与留痕用指纹，不参与任何加解密运算，
 * 也不对公钥做额外签名/背书。
 */
public interface ChatUserKeyService {

    /**
     * 注册或更新当前登录用户本人的身份公钥：已存在记录则更新，不存在则新建；只能写入
     * {@code userId} 对应的记录，不接受为他人注册（{@code userId} 从登录态解析，不接受
     * 请求体传入，从接口层面杜绝篡改）。
     *
     * @param userId  当前登录用户 id
     * @param request 注册/更新请求
     * @return 注册/更新后的密钥目录记录
     */
    ChatUserKeyVO registerMyKey(Long userId, ChatKeyRegisterRequest request);

    /**
     * 查询目标用户的身份公钥，供发起单聊/首次建立加密会话前调用。
     *
     * @param userId 目标用户 id
     * @return 密钥目录记录
     * @throws cn.nihility.rbac.common.exception.BusinessException 目标用户尚未注册聊天身份
     *                                                               公钥时抛出
     */
    ChatUserKeyVO getUserKey(Long userId);
}
