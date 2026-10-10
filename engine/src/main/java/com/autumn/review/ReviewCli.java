package com.autumn.review;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewEngine;
import com.autumn.review.llm.LlmReviewAssistant;
import com.autumn.review.report.HtmlReport;
import com.autumn.review.report.ReviewReport;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 命令行入口。
 *
 * <p>用法：
 * <pre>
 *   java -jar target/review.jar &lt;扫描目标&gt; [输出目录]
 * </pre>
 *
 * <p>设计成零外部依赖，是为了保证客户现场演示"解压即跑"——
 * 不联网、不配 API Key、不会因为网络问题翻车。
 */
public class ReviewCli {

    public static void main(String[] args) throws Exception {
        // Windows 控制台默认 GBK，中文会乱码。演示时这一行是必需的。
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        List<String> positional = new java.util.ArrayList<>();
        boolean allRules = false;
        String enableRule = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-v":
                case "--version":
                    out.println("ai-code-review " + version());
                    System.exit(0);
                    break;
                case "-h":
                case "--help":
                case "help":
                    printHelp(out);
                    System.exit(0);
                    break;
                case "--all-rules":
                    allRules = true;
                    break;
                case "--enable":
                    i++;
                    if (i >= args.length) {
                        out.println("--enable 需要一个规则编号，如 --enable NORM-002");
                        System.exit(1);
                    }
                    enableRule = args[i];
                    break;
                default:
                    // 以 - 开头却不是已知选项 → 明确报错，避免被静默当成扫描路径
                    if (args[i].startsWith("-")) {
                        out.println("未知选项: " + args[i]);
                        out.println("用 ai-code-review --help 查看可用参数");
                        System.exit(1);
                    }
                    positional.add(args[i]);
            }
        }

        if (positional.isEmpty()) {
            printHelp(out);
            System.exit(1);
        }

        Path target = Paths.get(positional.get(0)).toAbsolutePath().normalize();
        Path outDir = Paths.get(positional.size() > 1 ? positional.get(1) : "./out")
                .toAbsolutePath().normalize();

        if (!Files.exists(target)) {
            out.println("扫描目标不存在: " + target);
            System.exit(1);
        }

        out.println(">> 开始评审: " + target);

        // ---- 规则层：确定性、可离线 ----
        ReviewEngine engine = new ReviewEngine();
        if (enableRule != null) {
            engine.withRule(enableRule);
        }
        if (allRules) {
            engine.withAllRules();
        }
        out.println(">> 启用规则 : " + String.join(", ", engine.enabledRuleIds()));
        out.println(">> LLM 层   : 未启用（规则层完全离线可用）");
        out.println();

        long t0 = System.currentTimeMillis();
        int scanned;
        List<Finding> findings;
        if (Files.isDirectory(target)) {
            scanned = engine.countFiles(target);
            findings = engine.reviewDirectory(target);
        } else {
            scanned = 1;
            findings = engine.reviewContent(target.getFileName().toString(),
                    new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        }
        long elapsed = System.currentTimeMillis() - t0;

        // ---- LLM 层：可选增强，失败静默降级 ----
        List<String> llmNotes = LlmReviewAssistant.batchExplain(
                findings, LlmReviewAssistant.NOOP, "Java 后端 / Spring Boot");

        // ---- 输出 ----
        Files.createDirectories(outDir);
        String label = Files.isDirectory(target) ? target.toString() : target.getFileName().toString();

        out.println();
        out.print(ReviewReport.summary(findings, label, scanned));

        if (!llmNotes.isEmpty()) {
            out.println(" LLM 增强说明:");
            llmNotes.forEach(n -> out.println("   - " + n));
            out.println();
        }

        Path md = outDir.resolve("review-report.md");
        Path html = outDir.resolve("review-report.html");
        Path comment = outDir.resolve("gitlab-comment.md");
        ReviewReport.write(md, ReviewReport.markdown(findings, label));
        ReviewReport.write(html, HtmlReport.render(findings, label, scanned, elapsed));
        ReviewReport.write(comment, ReviewReport.gitlabComment(findings));

        out.println(" 报告已生成:");
        out.println("   " + html + "  (推荐：仪表盘 + 折叠，可直接贴 issue/PR)");
        out.println("   " + md + "     (适合 diff 与 AI 读取)");
        out.println("   " + comment + "  (可直接 POST 到 GitLab MR 评论)");
    }

    /** 版本号从 jar manifest 读取，避免源码与构建脚本两处维护。 */
    private static String version() {
        String v = ReviewCli.class.getPackage().getImplementationVersion();
        return v == null ? "(开发构建)" : v;
    }

    private static void printHelp(PrintStream out) {
        out.println("ai-code-review —— AI 代码评审流水线");
        out.println();
        out.println("用法:");
        out.println("  ai-code-review <扫描目标> [输出目录] [选项]");
        out.println();
        out.println("选项:");
        out.println("  --enable <规则号>   开启默认关闭的规则（如 NORM-002 超长行）");
        out.println("  --all-rules        开启全部规则（含格式类，噪音大）");
        out.println("  -v, --version      显示版本号");
        out.println("  -h, --help         显示本帮助");
        out.println();
        out.println("示例:");
        out.println("  ai-code-review ./src ./out                扫描单个项目");
        out.println("  ai-code-review D:/work/myproject ./out    扫描指定项目");
        out.println("  ai-code-review ./src ./out --enable NORM-002  额外开启格式检查");
        out.println();
        out.println("输出:");
        out.println("  <输出目录>/review-report.md   完整报告（逐条可追溯）");
        out.println("  <输出目录>/gitlab-comment.md  GitLab MR 评论模板");
        out.println();
        out.println("默认规则集（高信号）:");
        out.println("  SEC-001安全       硬编码凭证 / 云厂商 Token 形态");
        out.println("  EXC-001  异常       空 catch / printStackTrace / 异常链断裂");
        out.println("  DEP-001  依赖       已知高危版本 / 重复依赖");
        out.println("  LOG-001  日志       字符串拼接 / System.out / 关键事件用 debug");
        out.println("  NORM-001 规范       方法过长 / 类过长 / TODO 密度");
        out.println();
        out.println("环境变量:");
        out.println("  AI_REVIEW_JAVA_OPTS  传给 JVM 的额外参数（如 -Xmx4g）");
    }
}