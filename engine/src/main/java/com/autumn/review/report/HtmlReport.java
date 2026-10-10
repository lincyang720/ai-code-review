package com.autumn.review.report;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.Severity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 报告生成器。
 *
 * <p><b>设计来源</b>：{@code gzh-design} 技能的「石墨极简（Graphite Minimal）」主题。
 * 取其设计变量（配色/圆角/间距），但<b>不复用其 HTML 组件</b>——
 * 那是为公众号编辑器设计的，报告是工程产物，且要能贴进 GitLab issue / PR 评论。
 *
 * <p><b>核心改进</b>（相对 Markdown 版）：
 * <ul>
 *   <li>顶部数字仪表盘 —— 一眼看清严重程度分布，不用逐条数</li>
 *   <li>按级别折叠 —— P0/P1 默认展开，P2/P3 默认收起。
 *       实测 2634 行可压缩到屏上20 行</li>
 *   <li>每条默认只显示「一句话 + 位置」，证据与修法点了才展开</li>
 *   <li>「人工仍需判断」永远置顶 —— 明确工具边界</li>
 * </ul>
 *
 * <p><b>全内联样式</b>：保证能贴进 GitLab issue、PR 评论、邮件，不依赖外部 CSS。
 */
public final class HtmlReport {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // —— 石墨极简设计变量（取自 gzh-design/theme-graphite-minimal）——
    private static final String INK = "#27272A";        // 标题色
    private static final String BODY = "#52525B";       // 主色·正文
    private static final String DEEP = "#3F3F46";       // 深灰强调
    private static final String SUBTLE = "#71717A";    // 次要文字
    private static final String FAINT = "#A1A1AA";     // 辅助文字
    private static final String LINE = "#E4E4E7";      // 极细线
    private static final String TAG_BG = "#F4F4F5";    // 标签底
    private static final String SOFT_BG = "#FAFAFA";   // 极浅灰底
    private static final String WHITE = "#FFFFFF";
    private static final String FONT = "-apple-system,BlinkMacSystemFont,'PingFang SC',"
            + "'Hiragino Sans GB','Microsoft YaHei',sans-serif";
    private static final String MONO = "ui-monospace,SFMono-Regular,Menlo,Consolas,monospace";

    // 级别配色：只在标签处用色，正文保持克制
    private static final String C_P0 = "#B91C1C";
    private static final String C_P0_BG = "#FEF2F2";
    private static final String C_P1 = "#B45309";
    private static final String C_P1_BG = "#FFFBEB";
    private static final String C_P2 = "#52525B";
    private static final String C_P2_BG = "#F4F4F5";
    private static final String C_P3 = "#71717A";
    private static final String C_P3_BG = "#FAFAFA";

    private HtmlReport() {
    }

    /** 生成完整 HTML 报告。 */
    public static String render(List<Finding> findings, String target,
                                int scannedFiles, long elapsedMillis) {
        Map<Severity, Integer> count = countBySeverity(findings);
        int mustFix = (int) findings.stream()
                .filter(f -> f.severity().mustFixBeforeMerge()).count();

        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html>\n")
         .append("<html lang=\"zh-CN\">\n<head>\n")
         .append("<meta charset=\"UTF-8\">\n")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
         .append("<title>AI 代码评审报告</title>\n")
         .append("<style>\n")
         .append("body{margin:0;padding:24px 12px;background:")
         .append(SOFT_BG).append(";}\n")
         .append("details>summary{cursor:pointer;list-style:none;}\n")
         .append("details>summary::-webkit-details-marker{display:none;}\n")
         .append(".toc a{color:").append(BODY).append(";text-decoration:none;}\n")
         .append("code{font-family:").append(MONO).append(";}\n")
         .append("</style>\n</head>\n<body>\n");

        h.append("<section style=\"max-width:760px;margin:0 auto;background:").append(WHITE)
         .append(";font-family:").append(FONT).append(";color:").append(BODY)
         .append(";line-height:1.8;letter-spacing:0.3px;border-radius:12px;")
         .append("border:1px solid ").append(LINE).append(";padding:32px 28px;\">\n");

        // ===== 头部 =====
        h.append("<p style=\"margin:0;font-size:12px;color:").append(FAINT)
         .append(";letter-spacing:1.5px;\">AI CODE REVIEW</p>\n");
        h.append("<h1 style=\"margin:6px 0 0;font-size:24px;color:").append(INK)
         .append(";font-weight:600;\">代码评审报告</h1>\n");

        // ===== 概要仪表盘 =====
        h.append("<table style=\"width:100%;margin:20px 0 0;border-collapse:collapse;font-size:13px;\">\n")
         .append("<tr><td style=\"padding:6px 0;color:").append(SUBTLE)
         .append(";\">扫描目标</td><td style=\"padding:6px 0;text-align:right;color:")
         .append(DEEP).append(";word-break:break-all;\">").append(esc(target))
         .append("</td></tr>\n")
         .append("<tr><td style=\"padding:6px 0;color:").append(SUBTLE)
         .append(";\">扫描时间</td><td style=\"padding:6px 0;text-align:right;color:")
         .append(DEEP).append(";\">").append(LocalDateTime.now().format(TS))
         .append("</td></tr>\n");
        if (elapsedMillis > 0) {
            h.append("<tr><td style=\"padding:6px 0;color:").append(SUBTLE)
             .append(";\">扫描耗时</td><td style=\"padding:6px 0;text-align:right;color:")
             .append(DEEP).append(";\">").append(elapsedMillis / 1000.0).append(" 秒")
             .append("</td></tr>\n");
        }
        h.append("</table>\n");

        // 四个数字卡
        h.append("<table style=\"width:100%;margin:18px 0 0;border-collapse:separate;")
         .append("border-spacing:6px 0;\"><tr>\n");
        h.append(numCard("P0 阻断", count.get(Severity.BLOCKER), C_P0, C_P0_BG));
        h.append(numCard("P1 必改", count.get(Severity.CRITICAL), C_P1, C_P1_BG));
        h.append(numCard("P2 建议", count.get(Severity.MAJOR), C_P2, C_P2_BG));
        h.append(numCard("P3 提示", count.get(Severity.MINOR), C_P3, C_P3_BG));
        h.append("</tr></table>\n");

        // 结论条
        String verdict = mustFix == 0
                ? "未发现阻断项，可进入人工评审"
                : mustFix + " 条阻断/必改项建议在合并前处理";
        h.append("<div style=\"margin:18px 0 0;padding:14px 16px;background:").append(TAG_BG)
         .append(";border-radius:8px;font-size:14px;color:").append(DEEP).append(";\">")
         .append("<strong style=\"color:").append(INK).append(";\">结论</strong>　")
         .append(esc(verdict)).append("</div>\n");

        // 干净度
        if (scannedFiles > 0) {
            int hitFiles = (int) findings.stream().map(Finding::filePath).distinct().count();
            int clean = (int) Math.round((scannedFiles - hitFiles) * 100.0 / scannedFiles);
            h.append("<p style=\"margin:14px 0 0;font-size:13px;color:").append(SUBTLE)
             .append(";\">扫描 ").append(scannedFiles).append(" 个文件，其中 <strong style=\"color:")
             .append(DEEP).append(";\">").append(hitFiles).append("</strong> 个存在问题，")
             .append("<strong style=\"color:").append(DEEP).append(";\">").append(clean)
             .append("%</strong> 干净。</p>\n");
        }

        // ===== 人工边界（永远置顶）=====
        h.append("<div style=\"margin:28px 0 0;padding:16px 18px;background:").append(WHITE)
         .append(";border:1px solid ").append(LINE).append(";border-radius:8px;\">\n")
         .append("<p style=\"margin:0;font-size:14px;font-weight:600;color:").append(INK)
         .append(";\">本工具不覆盖，需人工判断</p>\n")
         .append("<p style=\"margin:8px 0 0;font-size:13px;color:").append(SUBTLE)
         .append(";line-height:1.9;\">架构与设计取舍　·　业务逻辑正确性　·　"
                 + "需求理解偏差　·　跨模块契约一致性</p>\n")
         .append("</div>\n");

        if (findings.isEmpty()) {
            h.append("<div style=\"margin:32px 0 0;padding:28px;text-align:center;")
             .append("background:").append(SOFT_BG).append(";border-radius:8px;\">")
             .append("<p style=\"margin:0;font-size:15px;color:").append(SUBTLE)
             .append(";\">未发现确定性问题</p></div>\n");
            return finish(h);
        }

        // ===== 目录 =====
        h.append("<p style=\"margin:32px 0 10px;font-size:15px;font-weight:600;color:")
         .append(INK).append(";\">问题清单</p>\n");
        h.append("<div style=\"font-size:13px;line-height:2.1;\">");
        for (Severity s : Severity.values()) {
            int n = count.get(s);
            if (n == 0) {
                continue;
            }
            h.append("<span style=\"display:inline-block;margin-right:18px;\">")
             .append(esc(s.code() + " " + s.label()))
             .append(" <strong style=\"color:").append(INK).append(";\">").append(n)
             .append("</strong></span>");
        }
        h.append("</div>\n");

        // ===== 明细：按级别分组，P0/P1 展开，P2/P3 折叠 =====
        for (Severity s : Severity.values()) {
            List<Finding> group = findings.stream()
                    .filter(f -> f.severity() == s).toList();
            if (group.isEmpty()) {
                continue;
            }
            boolean open = s.mustFixBeforeMerge();
            h.append(section(s, group, open));
        }

        // ===== 页脚 =====
        h.append("<div style=\"margin-top:36px;padding-top:18px;border-top:1px solid ")
         .append(LINE).append(";\">")
         .append("<p style=\"margin:0;font-size:12px;color:").append(FAINT)
         .append(";\">本报告由确定性规则生成，不替代人工评审。"
                 + "标记为「启发式」的条目建议人工确认。</p>")
         .append("<p style=\"margin:6px 0 0;font-size:12px;color:").append(FAINT)
         .append(";\">ai-code-review · 规则层零依赖、离线可跑</p>")
         .append("</div>\n");

        return finish(h);
    }

    /** 单个级别分组。 */
    private static String section(Severity s, List<Finding> group, boolean open) {
        String color = colorOf(s);
        String bg = bgOf(s);
        StringBuilder h = new StringBuilder();
        h.append("<details ").append(open ? "open" : "").append(" style=\"margin:24px 0 0;\">\n")
         .append("<summary style=\"padding:12px 16px;background:").append(bg)
         .append(";border-radius:8px;font-size:15px;font-weight:600;color:").append(color)
         .append(";\">").append(esc(s.code() + " " + s.label()))
         .append("　<span style=\"font-weight:400;font-size:13px;\">")
         .append(group.size()).append(" 条</span>")
         .append(open ? "　<span style=\"font-weight:400;font-size:12px;color:" + SUBTLE
                 + ";\">−</span>" : "　<span style=\"font-weight:400;font-size:12px;color:"
                 + SUBTLE + ";\">＋</span>")
         .append("</summary>\n<div style=\"padding:4px 0 0 2px;\">\n");

        int idx = 1;
        for (Finding f : group) {
            h.append(card(f, idx++));
        }
        h.append("</div>\n</details>\n");
        return h.toString();
    }

    /** 单条问题卡片。 */
    private static String card(Finding f, int idx) {
        String color = colorOf(f.severity());
        String bg = bgOf(f.severity());
        StringBuilder h = new StringBuilder();
        h.append("<details style=\"margin:8px 0;border:1px solid ").append(LINE)
         .append(";border-radius:8px;background:").append(WHITE).append(";\">\n");

        // 摘要行：一句话 + 位置
        h.append("<summary style=\"padding:12px 14px;font-size:14px;color:").append(INK)
         .append(";line-height:1.7;\">")
         .append("<span style=\"display:inline-block;min-width:22px;color:").append(FAINT)
         .append(";font-size:13px;\">#").append(idx).append("</span> ")
         .append("<span style=\"display:inline-block;padding:1px 7px;margin-right:8px;")
         .append("background:").append(bg).append(";color:").append(color)
         .append(";border-radius:4px;font-size:11px;\">").append(f.severity().code())
         .append("</span>")
         .append(esc(f.message())).append("</summary>\n");

        // 详情
        h.append("<div style=\"padding:0 14px 14px 14px;\">\n");

        h.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
        if (f.lineNumber() > 0) {
            h.append("<tr><td style=\"padding:3px 0;color:").append(SUBTLE)
             .append(";white-space:nowrap;width:52px;\">位置</td>")
             .append("<td style=\"padding:3px 0;color:").append(DEEP)
             .append(";font-family:").append(MONO)
             .append(";font-size:12px;word-break:break-all;\">")
             .append(esc(shorten(f.filePath()))).append(":").append(f.lineNumber())
             .append("</td></tr>\n");
        }
        h.append("<tr><td style=\"padding:3px 0;color:").append(SUBTLE)
         .append(";white-space:nowrap;\">规则</td><td style=\"padding:3px 0;color:")
         .append(DEEP).append(";\">").append(esc(f.ruleId())).append(" · ")
         .append(esc(f.category())).append(" · ")
         .append(f.deterministic() ? "确定性命中" : "启发式，建议人工确认")
         .append("</td></tr>\n");
        h.append("</table>\n");

        if (!f.evidence().isEmpty()) {
            h.append("<div style=\"margin:10px 0 0;padding:10px 12px;background:").append(TAG_BG)
             .append(";border-left:3px solid ").append(FAINT)
             .append(";border-radius:0 4px 4px 0;font-family:").append(MONO)
             .append(";font-size:12px;color:").append(DEEP)
             .append(";white-space:pre-wrap;word-break:break-all;\">")
             .append(esc(f.evidence())).append("</div>\n");
        }

        if (f.suggestion() != null && !f.suggestion().isEmpty()) {
            h.append("<div style=\"margin:10px 0 0;font-size:13px;color:").append(DEEP)
             .append(";line-height:1.8;\"><strong style=\"color:").append(INK)
             .append(";\">怎么改</strong>　").append(esc(f.suggestion()))
             .append("</div>\n");
        }

        h.append("</div>\n</details>\n");
        return h.toString();
    }

    private static String numCard(String label, int n, String color, String bg) {
        return "<td style=\"width:25%;padding:14px 8px;background:" + bg
                + ";border-radius:8px;text-align:center;\">"
                + "<div style=\"font-size:22px;font-weight:600;color:" + color + ";\">"
                + n + "</div>"
                + "<div style=\"font-size:11px;color:" + color + ";opacity:0.75;\">"
                + label + "</div></td>\n";
    }

    private static String finish(StringBuilder h) {
        return h.append("</section>\n</body>\n</html>\n").toString();
    }

    private static String colorOf(Severity s) {
        switch (s) {
            case BLOCKER: return C_P0;
            case CRITICAL: return C_P1;
            case MAJOR: return C_P2;
            default: return C_P3;
        }
    }

    private static String bgOf(Severity s) {
        switch (s) {
            case BLOCKER: return C_P0_BG;
            case CRITICAL: return C_P1_BG;
            case MAJOR: return C_P2_BG;
            default: return C_P3_BG;
        }
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

    /** 只保留文件名，避免路径过长撑破卡片。 */
    private static String shorten(String path) {
        int i = path.lastIndexOf('/');
        if (i < 0) {
            i = path.lastIndexOf('\\');
        }
        return i >= 0 ? "…/" + path.substring(i + 1) : path;
    }

    /** HTML 转义。评审内容含代码与引号，必须转义否则破坏结构。 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}