package cn.nihility.rbac.chat.service;

import cn.nihility.rbac.chat.service.support.AhoCorasickAutomaton;

/**
 * 敏感词内容过滤能力：服务启动时加载全部启用词条构建内存 AC 自动机，消息落库/推送前
 * 调用 {@link #filter} 做匹配；敏感词库管理接口（增删改状态）后调用 {@link #reload}
 * 触发内存自动机重建，使变更立即生效，不需要重启服务（chat-security spec"敏感词内容
 * 过滤"/"敏感词库后台管理"需求）。
 */
public interface SensitiveWordFilterService {

    /**
     * 对消息文本做敏感词匹配，命中的片段整体替换为等长的 {@code *}。
     *
     * @param text 待检测的消息文本
     * @return 过滤结果
     */
    AhoCorasickAutomaton.FilterResult filter(String text);

    /**
     * 从数据库重新加载全部启用状态的敏感词条，重建内存 AC 自动机并整体替换，
     * 替换过程对并发读取（{@link #filter}）无感知（volatile 引用整体切换）。
     */
    void reload();
}
