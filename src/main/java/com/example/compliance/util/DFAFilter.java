package com.example.compliance.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * DFA（Deterministic Finite Automaton）敏感词过滤核心引擎
 * 算法原理：
 * 将敏感词库构建成树形状态机，每个字符对应一个状态节点。
 * 扫描文本时只需遍历一次，时间复杂度 O(n)，不受词库规模影响。
 * 特性：
 * - 支持词库热加载（通过重建状态机 + 原子替换引用）
 * - 支持多类别词库（涉密词、敏感词、违法词）
 * - 支持跳过干扰字符（空格、特殊符号等）
 * - 最大匹配模式（优先匹配最长敏感词）
 * - 读写锁保证并发安全
 */
public class DFAFilter {

    private static final Logger log = LoggerFactory.getLogger(DFAFilter.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile DfaNode rootNode = new DfaNode();

    @Setter
    private volatile Set<Character> ignoreChars = new HashSet<>();

    /**
     * 完全重建词库（替换原有词库）
     */
    public void rebuildLibrary(Map<String, List<String>> categoryWordsMap) {
        lock.writeLock().lock();
        try {
            DfaNode newRoot = new DfaNode();
            for (Map.Entry<String, List<String>> entry : categoryWordsMap.entrySet()) {
                String category = entry.getKey();
                for (String word : entry.getValue()) {
                    if (word == null || word.trim().isEmpty()) {
                        continue;
                    }
                    String trimmed = word.trim().toLowerCase();
                    DfaNode current = newRoot;
                    for (int i = 0; i < trimmed.length(); i++) {
                        char c = trimmed.charAt(i);
                        current = current.children.computeIfAbsent(c, k -> new DfaNode());
                    }
                    current.isEnd = true;
                    current.category = category;
                    current.word = trimmed;
                }
            }
            rootNode = newRoot;
            log.info("DFA词库完全重建完成，总节点数: {}", countNodes(newRoot));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 扫描文本，返回命中的所有敏感词及其位置信息
     *
     * @param text 待检测文本
     * @return 敏感词匹配结果列表
     */
    public List<MatchResult> scan(String text) {
        List<MatchResult> results = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return results;
        }

        lock.readLock().lock();
        try {
            String lowerText = text.toLowerCase();
            int length = lowerText.length();
            int i = 0;

            while (i < length) {
                DfaNode current = rootNode;
                int matchEnd = -1;
                String matchedWord = null;
                String matchedCategory = null;
                int j = i;

                // 最大匹配：尝试找到最长的敏感词
                while (j < length) {
                    char c = lowerText.charAt(j);

                    // 跳过干扰字符（但记录位置继续匹配）
                    if (ignoreChars.contains(c)) {
                        j++;
                        continue;
                    }

                    DfaNode next = current.children.get(c);
                    if (next == null) {
                        break;
                    }
                    current = next;
                    j++;

                    if (current.isEnd) {
                        matchEnd = j;
                        matchedWord = current.word;
                        matchedCategory = current.category;
                    }
                }

                if (matchEnd > 0) {
                    results.add(new MatchResult(matchedWord, i, matchEnd, matchedCategory));
                    i = matchEnd;
                } else {
                    i++;
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return results;
    }

    private int countNodes(DfaNode node) {
        int count = node.children.size();
        for (DfaNode child : node.children.values()) {
            count += countNodes(child);
        }
        return count;
    }

    /**
     * DFA 状态机节点
     */
    static class DfaNode {
        Map<Character, DfaNode> children = new HashMap<>();
        boolean isEnd = false;
        String category;
        String word;
    }

    /**
     * 敏感词匹配结果
     */
    public record MatchResult(String word, int startIndex, int endIndex, String category) {
        @Override
        public String toString() {
            return "MatchResult{word='" + word + "', pos=[" + startIndex + "-" + endIndex +
                    "], category='" + category + "'}";
        }
    }
}