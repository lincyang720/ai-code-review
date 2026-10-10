package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * NORM-001：结构性规范问题（类过长、方法过长、TODO 密度）。
 *
 * <p>刻意<b>不包含超长行</b>——超长行属于格式化插件的职责，且在真实项目里
 * 数量压倒性地大，会把真正重要的 P0/P1 淹没在噪音里（实测占 68%）。
 *
 * <p>这类问题对应"评审拖慢"里最耗时的部分：人类评审大量时间花在挑格式上。
 * 规则化后，人只需要看真正的设计问题。
 */
public class CodeConventionRule implements ReviewRule {

    private static final int MAX_METHOD_LINES = 60;
    private static final int MAX_CLASS_LINES = 800;
    private static final int TODO_REPEAT_WARN = 3;

    @Override
    public String id() {
        return "NORM-001";
    }

    @Override
    public String category() {
        return "规范";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int total = lines.length;

        // 超长类
        if (total > MAX_CLASS_LINES) {
            out.add(new Finding(id(), category(), Severity.MINOR, filePath, 1,
                    "文件共 " + total + " 行",
                    "类过长（超过 " + MAX_CLASS_LINES + " 行）。单类承担多个职责，"
                            + "改动时冲突概率和评审成本都会明显上升。",
                    "按职责拆分为多个类；若确需保持单文件，考虑抽取内部类或使用组合而非继承。",
                    true));
        }

        // 超长行已移至 LongLineRule (NORM-002)，默认不启用

        // 方法过长（粗略：统计连续缩进块内的大括号配对）
        int start = -1;
        int depth = 0;
        for (int i = 0; i < total; i++) {
            String t = lines[i];
            boolean isSig = start < 0
                    && (t.contains("public ") || t.contains("private ") || t.contains("protected "))
                    && t.contains("(") && t.contains(")")
                    && t.contains("{")
                    && !t.trim().startsWith("return")
                    && !t.contains("new ")
                    && !t.contains("interface")
                    && !t.contains("class ");
            if (isSig) {
                start = i;
                depth = 0;
            }
            if (start >= 0) {
                for (char c : t.toCharArray()) {
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                    }
                }
                if (depth <= 0 && i > start) {
                    int len = i - start + 1;
                    if (len > MAX_METHOD_LINES) {
                        out.add(new Finding(id(), category(), Severity.MAJOR, filePath, start + 1,
                                lines[start].trim(),
                                "方法体约 " + len + " 行，超过 " + MAX_METHOD_LINES + "。",
                                "拆分职责：识别出可以独立成方法的步骤。判断标准是"
                                        + "「这一段能不能单独起个名字」，能就叫出来。",
                                false));
                    }
                    start = -1;
                }
            }
        }

        // TODO 密度
        int todoCount = 0;
        for (String l : lines) {
            if (l.contains("TODO") || l.contains("FIXME") || l.contains("XXX")) {
                todoCount++;
            }
        }
        if (todoCount >= TODO_REPEAT_WARN) {
            out.add(new Finding(id(), category(), Severity.MAJOR, filePath, 1,
                    "全文 TODO/FIXME 共 " + todoCount + " 处",
                    "遗留标记过多。这些标记本身没问题，但集中出现通常说明这个类"
                            + "处于未完成状态，长期挂着会让人不敢动它。",
                    "要么补一个工单号关联上，要么直接改掉 —— 无主的 TODO 等于没有 TODO。",
                    true));
        }

        return out;
    }
}