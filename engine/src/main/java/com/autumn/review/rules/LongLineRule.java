package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * NORM-002：超长行检查。
 *
 * <p>单独拆成一条规则、且<b>默认不启用</b>的原因：实测在 hccloud（4753 个Java 文件）
 * 上，它贡献了 7985 条问题，占总问题数 68% —— 把真正重要的 P0/P1 完全淹没。
 * 客户看到一万多条问题会直接放弃，这类"格式类"问题不该占用评审通道。
 *
 * <p>超长行属于<b>IDE 格式化插件</b>的职责（一次全量格式化即可解决），
 * 更适合在"只扫本次改动文件"的增量场景开启，而不是全量基线扫描。
 */
public class LongLineRule implements ReviewRule {

    private static final int MAX_LINE_LENGTH = 120;

    @Override
    public String id() {
        return "NORM-002";
    }

    @Override
    public String category() {
        return "规范";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.length() > MAX_LINE_LENGTH) {
                out.add(new Finding(id(), category(), Severity.MINOR, filePath, i + 1,
                        raw.length() > 100 ? raw.substring(0, 100) + "…" : raw,
                        "单行 " + raw.length() + " 字符，超过 " + MAX_LINE_LENGTH + "。",
                        "交给 IDE 格式化插件批量处理（一次全量格式化即可清零），"
                                + "不建议占用人工评审注意力。",
                        true));
            }
        }
        return out;
    }
}