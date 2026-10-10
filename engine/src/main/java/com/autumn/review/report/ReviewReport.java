package com.autumn.review.report;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewEngine;
import com.autumn.review.engine.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 评审报告输出。
 *
 * <p>同时产出两种格式：
 * <ul>
 *   <li>控制台摘要 —— 演示时给决策者看，只讲"拦住了什么"</li>
 *   <li>Markdown 明细 —— 给技术负责人看，逐条可追溯</li>
 * </ul>
 *
 * <p>刻意把"汇总"和"明细"分开：前者对应老板关心的"风险被控制住了"，
 * 后者对应工程师关心的"我具体要改哪行"。混在一起两头都不好用。
 */
public final class ReviewReport {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReviewReport() {
    }

    /** 控制台摘要。scannedFiles = 实际扫描的文件数（不是"有问题的文件数"）。 */
    public static String summary(List<Finding> findings, String target, int scannedFiles) {
        Map<Severity, Integer> count = countBySeverity(findings);
        int total = findings.size();
        int mustFix = (int) findings.stream()
                .filter(f -> f.severity().mustFixBeforeMerge()).count();

        StringBuilder sb = new StringBuilder();
        sb.append("════════════════════════════════════════════════════════\n");
        sb.append(" AI 代码评审 · 拦截摘要\n");
        sb.append("════════════════════════════════════════════════════════\n");
        sb.append(" 扫描目标 : ").append(target).append('\n');
        sb.append(" 扫描时间 : ").append(LocalDateTime.now().format(TS)).append('\n');
        sb.append(" 扫描文件 : ").append(scannedFiles).append(" 个\n");
        sb.append(" 发现问题 : ").append(total).append(" 条")
          .append(total == 0 ? "（未发现确定性问题）" : "").append('\n');
        sb.append('\n');

        sb.append(" 分级明细\n");
        sb.append("   P0 阻断  ").append(fmt(count.get(Severity.BLOCKER)))
          .append(" 条   ← 必须拦截，否则等于没上线这道门\n");
        sb.append("   P1 必改  ").append(fmt(count.get(Severity.CRITICAL)))
          .append(" 条   ← 合入前必须解决\n");
        sb.append("   P2 建议  ").append(fmt(count.get(Severity.MAJOR))).append(" 条\n");
        sb.append("   P3 提示  ").append(fmt(count.get(Severity.MINOR))).append(" 条\n\n");

        sb.append(" 结论     : ")
          .append(mustFix == 0
                  ? "无阻断项，可进入人工评审\n"
                  : mustFix + " 条阻断/必改项建议拦截合并\n")
          .append(" 人工关注 : 架构取舍与业务语义仍需人工判断（本工具不覆盖）\n");
        sb.append("════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    /** 控制台摘要（文件数由 findings 推断，仅用于调试场景）。 */
    public static String summary(List<Finding> findings, String target) {
        return summary(findings, target, distinctFiles(findings));
    }

    /** Markdown 明细报告。 */
    public static String markdown(List<Finding> findings, String target) {
        Map<Severity, Integer> count = countBySeverity(findings);

        StringBuilder sb = new StringBuilder();
        sb.append("# AI 代码评审报告\n\n");
        sb.append("- 扫描目标：").append(target).append('\n');
        sb.append("- 扫描时间：").append(LocalDateTime.now().format(TS)).append('\n');
        sb.append("- 发现问题：").append(findings.size()).append(" 条\n\n");

        sb.append("| 级别 | 数量 | 含义 |\n|---|---|---|\n");
        sb.append("| P0 阻断 | ").append(count.get(Severity.BLOCKER))
          .append(" | 安全/依赖高危，必须拦截 |\n");
        sb.append("| P1 必改 | ").append(count.get(Severity.CRITICAL))
          .append(" | 异常处理缺陷，会导致故障静默 |\n");
        sb.append("| P2 建议 | ").append(count.get(Severity.MAJOR))
          .append(" | 规范与可维护性 |\n");
        sb.append("| P3 提示 | ").append(count.get(Severity.MINOR)).append(" | 可改进项 |\n\n");

        sb.append("## 人工仍需判断的部分\n\n");
        sb.append("以下内容**本工具不覆盖**，需要人工评审：\n\n");
        sb.append("- 架构与设计取舍是否合理\n");
        sb.append("- 业务语义是否正确（需求理解偏差）\n");
        sb.append("- 边界条件与异常场景的业务影响面\n");
        sb.append("- 跨模块/跨团队的接口契约一致性\n\n");
        sb.append("> 设计原则：规则层消灭「确定性问题」，把人的时间留给")
          .append("「需要判断的问题」，这是评审瓶颈的真正解法。\n\n");

        sb.append("## 问题明细\n\n");
        int idx = 1;
        Severity last = null;
        for (Finding f : findings) {
            if (last != f.severity()) {
                sb.append("### ").append(f.severity().code()).append(' ')
                  .append(f.severity().label()).append("\n\n");
                last = f.severity();
            }
            sb.append("#### ").append(idx++).append(". ")
              .append(f.ruleId()).append(" · ").append(f.category()).append('\n');
            sb.append("- 位置：`").append(f.filePath());
            if (f.lineNumber() > 0) {
                sb.append(':').append(f.lineNumber());
            }
            sb.append("`\n");
            sb.append("- 级别：").append(f.severity().code()).append('\n');
            sb.append("- 问题：").append(f.message()).append('\n');
            if (!f.evidence().isEmpty()) {
                sb.append("- 证据：\n\n```\n").append(f.evidence()).append("\n```\n");
            }
            sb.append("- 建议：").append(f.suggestion()).append('\n');
            sb.append("- 判定方式：")
              .append(f.deterministic() ? "确定性命中" : "启发式，建议人工确认")
              .append("\n\n");
        }
        return sb.toString();
    }

    /** GitLab MR 评论格式（扁平，便于直接 POST 到 notes API）。 */
    public static String gitlabComment(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 AI 代码评审结果\n\n");
        int mustFix = (int) findings.stream()
                .filter(f -> f.severity().mustFixBeforeMerge()).count();
        sb.append("共发现 ").append(findings.size()).append(" 条问题，其中 **")
          .append(mustFix).append(" 条**建议在合并前处理。\n\n");

        for (Finding f : findings) {
            if (f.severity().mustFixBeforeMerge()) {
                sb.append(f.toMarkdown()).append("\n\n---\n\n");
            }
        }
        sb.append("<details><summary>展开查看全部 ")
          .append(findings.size()).append(" 条</summary>\n\n");
        for (Finding f : findings) {
            if (!f.severity().mustFixBeforeMerge()) {
                sb.append(f.toMarkdown()).append("\n\n");
            }
        }
        sb.append("</details>\n\n");
        sb.append("<sub>本评论由确定性规则生成，不替代人工评审；"
                + "架构与业务语义请以人工结论为准。</sub>");
        return sb.toString();
    }

    private static Map<Severity, Integer> countBySeverity(List<Finding> findings) {
        Map<Severity, Integer> m = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            m.put(s, 0);
        }
        for (Finding f : findings) {
            m.merge(f.severity(), 1, Integer::sum);
        }
        return m;
    }

    private static int distinctFiles(List<Finding> findings) {
        return (int) findings.stream().map(Finding::filePath).distinct().count();
    }

    /** 左对齐补空格到指定宽度。手工实现，避免依赖高版本 String API。 */
    private static String fmt(Integer n) {
        String s = String.valueOf(n == null ? 0 : n.intValue());
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < 3; i++) {
            sb.append(' ');
        }
        return sb.append(s).toString();
    }

    public static void write(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}