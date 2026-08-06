package cn.nihility.rbac.user.service;

import java.util.Collection;
import java.util.Map;

/**
 * 批量把审计字段（{@code create_by}/{@code update_by}，落库形式是用户 id 的字符串）
 * 解析成人可读展示名的能力，供各业务模块的详情/列表查询复用，避免逐行单独查询造成 N+1。
 */
public interface UserDisplayService {

    /**
     * 批量解析用户 id 文本对应的展示名。
     *
     * @param userIdTexts 待解析的用户 id 文本集合（即 entity {@code createBy}/{@code updateBy}
     *                    字段原样的值），空白/无法解析为数字的值会被忽略
     * @return 输入文本 -> {@code 姓名（账号编码）} 形式展示名的映射；查不到对应用户、或输入
     *         无法解析为合法 id 的文本不会出现在返回结果里，调用方需自行处理"未知用户"展示
     */
    Map<String, String> resolveDisplayNames(Collection<String> userIdTexts);
}
