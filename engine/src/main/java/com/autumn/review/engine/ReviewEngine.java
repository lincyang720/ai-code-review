package com.autumn.review.engine;

import com.autumn.review.rules.CodeConventionRule;
import com.autumn.review.rules.DependencyRiskRule;
import com.autumn.review.rules.ExceptionHandlingRule;
import com.autumn.review.rules.HardcodedSecretRule;
import com.autumn.review.rules.LoggingRule;
import com.autumn.review.rules.LongLineRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
     * 评审引擎。
     *
     * <p>刻意做成<b>纯本地、零外部依赖</b>：不需要任何 API Key、不联网也能跑完。
     * 这一点对落地很关键 —— 客户第一次演示时最怕的就是网络/API 出问题，
     * 而规则层的价值本来就是确定性的。
     *
     * <p>LLM 是可选增强层（见 LlmReviewAssistant），不装也能完整工作。
 *
     * <p><b>默认规则集是刻意"收窄"的。</b>实测 4753 个文件的企业项目，全量规则
     * 会产出 11697 条问题，其中 68% 是超长行——这类格式问题把 P0/P1 完全淹没，
     * 客户看到会直接放弃。因此默认只开高信号规则；格式类规则需显式开启，
     * 更适合在"只扫改动文件"的增量场景使用。
 */
public class ReviewEngine {

    private final List<ReviewRule> rules = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>();

    /** 默认不启用的规则（噪音大，只在增量/专项扫描时开）。 */
    private final List<ReviewRule> optInRules = new ArrayList<>();

    public ReviewEngine() {
        // 顺序即优先级：先跑高价值规则，早失败
        rules.add(new HardcodedSecretRule());
        rules.add(new ExceptionHandlingRule());
        rules.add(new DependencyRiskRule());
        rules.add(new LoggingRule());
        rules.add(new CodeConventionRule());

        // 噪音大，默认关闭
        optInRules.add(new LongLineRule());

        excludes.add("target/");
        excludes.add("build/");
        excludes.add(".git/");
        excludes.add("node_modules/");
    }

    /** 显式开启一条默认关闭的规则（如 NORM-002 超长行）。 */
    public ReviewEngine withRule(String ruleId) {
        for (ReviewRule r : optInRules) {
            if (r.id().equals(ruleId)) {
                rules.add(r);
                optInRules.remove(r);
                break;
            }
        }
        return this;
    }

    /** 开启全部可选规则（含噪音大的格式类规则）。 */
    public ReviewEngine withAllRules() {
        rules.addAll(optInRules);
        optInRules.clear();
        return this;
    }

    /** 当前启用的规则编号（报告里要如实展示，避免"以为开了其实没开"）。 */
    public List<String> enabledRuleIds() {
        return rules.stream().map(ReviewRule::id).toList();
    }

    /** 允许客户侧关掉噪音规则。 */
    public ReviewEngine withoutRule(String ruleId) {
        rules.removeIf(r -> r.id().equals(ruleId));
        return this;
    }

    public ReviewEngine withExclude(String pattern) {
        excludes.add(pattern);
        return this;
    }

    /** 扫描单个文件内容。 */
    public List<Finding> reviewContent(String filePath, String content) {
        List<Finding> all = new ArrayList<>();
        for (ReviewRule rule : rules) {
            try {
                List<Finding> found = rule.scan(filePath, content);
                if (found != null) {
                    all.addAll(found);
                }
            } catch (RuntimeException e) {
                // 单条规则异常不能影响整次评审——这正是自动化流水线最需要的行为
                all.add(new Finding(rule.id(), rule.category(), Severity.MINOR,
                        filePath, 0, "",
                        "规则执行异常：" + e.getMessage() + "。该文件本次未被完整检查。",
                        "这是规则自身的问题，请联系工具维护者。请勿因此跳过人工评审。",
                        true));
            }
        }
        sortBySeverity(all);
        return all;
    }

    /** 扫描整个目录。 */
    public List<Finding> reviewDirectory(Path root) throws IOException {
        return reviewDirectory(root, null);
    }

    /**
     * 扫描整个目录，并回调每个被扫描的文件。
     * scannedCounter 用于在报告里显示"实际扫描了几个文件"——
     * 只报"有问题的文件数"会让 0 问题的项目显示成 0 个文件，演示时是硬伤。
     */
    public List<Finding> reviewDirectory(Path root, java.util.function.Consumer<Path> onScanned)
            throws IOException {
        List<Finding> all = new ArrayList<>();
        List<Path> files = listFiles(root);
        for (Path f : files) {
            if (onScanned != null) {
                onScanned.accept(f);
            }
            String rel = root.relativize(f).toString().replace('\\', '/');
            String content;
            try {
                content = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            all.addAll(reviewContent(rel, content));
        }
        sortBySeverity(all);
        return all;
    }

    /** 统计目录下会被扫描的文件数（不执行评审）。 */
    public int countFiles(Path root) throws IOException {
        return listFiles(root).size();
    }

    private List<Path> listFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        for (String ex : excludes) {
                            if (s.contains(ex)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private boolean isSourceFile(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".java") || n.equals("pom.xml") || n.endsWith(".gradle");
    }

    /** P0 → P1 → P2 → P3，同级按文件+行号。 */
    public static void sortBySeverity(List<Finding> list) {
        list.sort(Comparator
                .comparingInt((Finding f) -> f.severity().ordinal())
                .thenComparing(Finding::filePath)
                .thenComparingInt(Finding::lineNumber));
    }
}