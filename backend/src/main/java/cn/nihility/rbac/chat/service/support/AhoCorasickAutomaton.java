package cn.nihility.rbac.chat.service.support;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量级 Aho-Corasick（AC）自动机实现，用于聊天消息内容的敏感词多模式串匹配，
 * 时间复杂度与文本长度成线性关系，不随词库规模增长而退化（chat-security spec
 * "敏感词内容过滤"需求，design.md Decision 8）。不依赖任何第三方分词/敏感词库，
 * 词库变更后通过 {@link #build} 重新构建一个新实例整体替换（见
 * {@link cn.nihility.rbac.chat.service.impl.SensitiveWordFilterServiceImpl}）。
 */
public final class AhoCorasickAutomaton {

    /** 自动机根节点。 */
    private final Node root;

    private AhoCorasickAutomaton(Node root) {
        this.root = root;
    }

    /**
     * 基于给定词条集合构建一个新的自动机实例（Trie 构建 + BFS 计算失败指针）。
     *
     * @param words 词条集合，{@code null}/空白词条会被忽略
     * @return 构建完成的自动机
     */
    public static AhoCorasickAutomaton build(Collection<String> words) {
        Node root = new Node(0);
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Node current = root;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                int nextDepth = current.depth + 1;
                current = current.children.computeIfAbsent(c, k -> new Node(nextDepth));
            }
            current.end = true;
        }

        Deque<Node> queue = new ArrayDeque<>();
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();
                Node failCandidate = current.fail;
                while (failCandidate != null && !failCandidate.children.containsKey(c)) {
                    failCandidate = failCandidate.fail;
                }
                child.fail = failCandidate == null ? root : failCandidate.children.get(c);
                if (child.fail == child) {
                    child.fail = root;
                }
                queue.add(child);
            }
        }
        return new AhoCorasickAutomaton(root);
    }

    /**
     * 对文本做敏感词匹配，命中时把命中片段整体替换为等长的 {@code *}。
     *
     * @param text 待检测文本
     * @return 过滤结果，{@code content} 为替换后的文本（未命中时与原文相同），
     *         {@code hit} 标记是否命中过任意词条
     */
    public FilterResult filter(String text) {
        if (text == null || text.isEmpty() || root.children.isEmpty()) {
            return new FilterResult(text, false);
        }

        char[] chars = text.toCharArray();
        boolean[] hitMask = new boolean[chars.length];
        boolean hit = false;
        Node current = root;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            current = current.children.getOrDefault(c, root);

            Node matchNode = current;
            while (matchNode != root) {
                if (matchNode.end) {
                    hit = true;
                    for (int j = i - matchNode.depth + 1; j <= i; j++) {
                        hitMask[j] = true;
                    }
                }
                matchNode = matchNode.fail;
            }
        }

        if (!hit) {
            return new FilterResult(text, false);
        }
        StringBuilder replaced = new StringBuilder(text);
        for (int i = 0; i < chars.length; i++) {
            if (hitMask[i]) {
                replaced.setCharAt(i, '*');
            }
        }
        return new FilterResult(replaced.toString(), true);
    }

    /**
     * 敏感词过滤结果。
     *
     * @param content 过滤/替换后的文本
     * @param hit     是否命中过任意敏感词
     */
    public record FilterResult(String content, boolean hit) {
    }

    /** Trie 节点，{@code depth} 记录从根到当前节点的字符深度，用于命中时定位替换范围。 */
    private static final class Node {

        private final int depth;
        private final Map<Character, Node> children = new HashMap<>();
        private Node fail;
        private boolean end;

        private Node(int depth) {
            this.depth = depth;
        }
    }
}
