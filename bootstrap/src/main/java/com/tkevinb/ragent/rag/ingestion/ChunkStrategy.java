package com.tkevinb.ragent.rag.ingestion;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块策略
 * <p>
 * ① 结构分块：按 ## / ### / #### 标题层级切
 * ② 递归兜底：超出 maxChars 的块递归切
 */
@Service
public class ChunkStrategy {

    private static final int MAX_CHARS = 1024;
    private static final int MIN_CHARS = 128;

    /**
     * 将文本切分为多个 chunk
     * @return List<ChunkResult> — text 为 chunk 内容, isParent 表示是否为大块
     */
    public List<ChunkResult> chunk(String text) {
        List<ChunkResult> parents = splitByHeading(text);
        List<ChunkResult> result = new ArrayList<>();

        for (ChunkResult parent : parents) {
            if (parent.text.length() <= MAX_CHARS) {
                result.add(parent);
            } else {
                // 递归切分：按段落切
                List<ChunkResult> children = recursiveSplit(parent.text);
                for (ChunkResult child : children) {
                    child.parentText = parent.text;
                    result.add(child);
                }
            }
        }
        return result;
    }

    /** 按 Markdown 标题层级切分 */
    private List<ChunkResult> splitByHeading(String text) {
        List<ChunkResult> result = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.matches("^#{1,4}\\s.*") && current.length() >= MIN_CHARS) {
                result.add(new ChunkResult(current.toString().trim(), true, null));
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (!current.isEmpty()) {
            result.add(new ChunkResult(current.toString().trim(), true, null));
        }
        return result;
    }

    /** 按段落递归切分 */
    private List<ChunkResult> recursiveSplit(String text) {
        List<ChunkResult> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : text.split("\n\n")) {
            if (current.length() + para.length() > MAX_CHARS && !current.isEmpty()) {
                result.add(new ChunkResult(current.toString().trim(), false, null));
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        if (!current.isEmpty()) {
            result.add(new ChunkResult(current.toString().trim(), false, null));
        }
        return result;
    }

    public static class ChunkResult {
        public final String text;
        public final boolean isParent;
        public String parentText;

        public ChunkResult(String text, boolean isParent, String parentText) {
            this.text = text;
            this.isParent = isParent;
            this.parentText = parentText;
        }
    }
}
