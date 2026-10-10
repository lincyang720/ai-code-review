package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEP-001：依赖风险。
 *
 * <p>检查两类：
 * A) 已知高危版本 —— 内置一份小型黑名单，接真实项目时替换为 CVE 数据库查询
 * B) 重复引入安全/工具库 —— 常是新旧依赖并存导致的安全与兼容问题
 *
 * <p>说明：生产环境应对接 SCA 工具（OWASP Dependency-Check / Trivy），
 * 这里内置黑名单是为了让 demo 零依赖可跑，并演示"规则可替换"的设计。
 */
public class DependencyRiskRule implements ReviewRule {

    /** 已知存在高危漏洞的版本片段 → 说明 */
    private static final String[][] VULNERABLE = {
        {"log4j-core-2.14", "Log4Shell (CVE-2021-44228)，RCE，应升级到 2.17.1+"},
        {"commons-collections-3.2", "CVE-2015-7501 反序列化 RCE，应升级到 3.2.2+"},
        {"commons-text-1.5", "CVE-2022-42889 Text4Shell RCE，应升级到 1.10+"},
        {"fastjson-1.2.68", "CVE-2022-25845 反序列化 RCE，应升级到 2.x / fastjson2"},
        {"snakeyaml-1.24", "CVE-2022-1471 反序列化 RCE，应升级到 2.0+"},
        {"log4j-core-2.13", "存在 CVE-2021-45046 风险"},
        {"spring-core-5.2.0", "存在多个已知 CVE，建议升级到受支持版本线"},
        {"jackson-databind-2.9", "CVE-2020-36518 反序列化 DoS，应升级到 2.15+"},
    };

    private static final Pattern DEP_LINE = Pattern.compile(
            "<artifactId>([^<]+)</artifactId>\\s*(?:<version>([^<]+)</version>)?");

    @Override
    public String id() {
        return "DEP-001";
    }

    @Override
    public String category() {
        return "依赖";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        if (!filePath.endsWith("pom.xml") && !filePath.endsWith("build.gradle")) {
            return out;
        }

        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int ln = i + 1;

            for (String[] rule : VULNERABLE) {
                if (line.contains(rule[0])) {
                    out.add(new Finding(id(), category(), Severity.BLOCKER, filePath, ln,
                            line.trim(),
                            "依赖存在已知高危漏洞：" + rule[1],
                            "立即升级到修复版本；升级后跑一遍回归测试，"
                                    + "并检查是否存在调用了已废弃 API 的代码（编译期不一定报错）。",
                            true));
                }
            }

            // 同一 groupId 下重复声明 artifactId
            Matcher m = DEP_LINE.matcher(line);
            if (m.find() && line.contains("artifactId")) {
                String artifact = m.group(1);
                int count = 0;
                for (String l2 : lines) {
                    if (l2.contains("<artifactId>" + artifact + "</artifactId>")) {
                        count++;
                    }
                }
                if (count > 1) {
                    out.add(new Finding(id(), category(), Severity.MAJOR, filePath, ln,
                            line.trim(),
                            "artifactId " + artifact + " 在同一文件中出现 " + count + " 次。",
                            "通常意味着新旧版本/不同 scope 并存，容易引入难以排查的类冲突。"
                                    + "用 `mvn dependency:tree -Dincludes=" + artifact
                                    + "` 确认来源，合并到 dependencyManagement 统一管理。",
                            false));
                }
            }
        }
        return out;
    }
}