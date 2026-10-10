package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-001：敏感信息硬编码。
 *
 * <p>这是唯一真正做到"零漏报零误报"的一类问题——关键词 + 赋值形态，
 * 判定条件完全确定，所以值得放在最前面拦住。
 */
public class HardcodedSecretRule implements ReviewRule {

    /*
     * 形如：password = "xxx" / secret: 'xxx' / token="xxx"
     * group(1) = 敏感关键词；group(2) = 变量名（=号左边，取最后一段标识符）；
     * group(3) = 值。
     * 之所以要抓变量名，是为了识别
     *   private static final String PASSWORD = "password";
     * 这类「字段名当值」的误报。
     */
    private static final Pattern ASSIGN = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|credential)\\b"
            + "\\s*[:=]\\s*[\"']+([^\"']{6,})[\"']");

    /** 从赋值语句左侧提取变量名，如 `private static final String PASSWORD` -> PASSWORD */
    private static final Pattern VAR_NAME = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*[=:]");

    // 形如：AKIA... / ghp_... / sk-... / Bearer xxx
    private static final Pattern TOKEN_SHAPE = Pattern.compile(
            "(AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,})");

    @Override
    public String id() {
        return "SEC-001";
    }

    @Override
    public String category() {
        return "安全";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int ln = i + 1;

            // 跳过测试文件与示例文件——测试里出现假密码是合理的
            if (filePath.contains("/test/") || filePath.contains("/tests/")
                    || filePath.contains("/test-") || filePath.endsWith("Test.java")
                    || filePath.contains("/resources/")) {
                continue;
            }

            Matcher m = ASSIGN.matcher(line);
            if (m.find()) {
                String keyword = m.group(1);
                String value = m.group(2);
                String varName = extractVarName(line);

                // 排除占位值，避免噪音
                if (isPlaceholder(value)) {
                    continue;
                }
                // 排除「字段名当值」型误报，如 PASSWORD = "password"
                if (varName != null && isSelfNamedConstant(varName, value)) {
                    continue;
                }

                out.add(new Finding(id(), category(), Severity.BLOCKER, filePath, ln,
                        line.trim(),
                        "检测到硬编码凭证（" + keyword + "）。一旦代码进入仓库，"
                                + "该凭证即视为已泄露，必须轮换而不是只删代码。",
                        "1) 立刻在凭证管理平台轮换该凭证；"
                                + "2) 改为从环境变量 / 配置中心读取，例如 "
                                + "`@Value(\"${app.secret}\")` 或 K8s Secret 挂载；"
                                + "3) 补一条 pre-commit 钩子阻止再次提交。",
                        true));
            }

            Matcher t = TOKEN_SHAPE.matcher(line);
            if (t.find()) {
                out.add(new Finding(id(), category(), Severity.BLOCKER, filePath, ln,
                        line.trim(),
                        "检测到疑似真实云厂商凭证格式（" + t.group(1).substring(0, Math.min(8, t.group(1).length()))
                                + "…）。",
                        "立即吊销并轮换该凭证，改为运行时注入；同时清理 git 历史"
                                + "（git filter-repo 或 BFG），仅删除当前文件不足以解除泄露。",
                        true));
            }
        }
        return out;
    }

    /** 判断是否只是占位符/示例值。 */
    private boolean isPlaceholder(String v) {
        String s = v.toLowerCase();
        return s.contains("your") || s.contains("xxx") || s.contains("todo")
                || s.contains("changeme") || s.contains("example")
                || s.contains("placeholder") || s.contains("${") || s.equals("123456")
                || s.contains("test") || s.contains("dummy") || s.contains("fake");
    }

    /**
     * 从赋值语句左侧提取变量名。
     * `private static final String PASSWORD = "x"` -> PASSWORD
     */
    private String extractVarName(String line) {
        Matcher vm = VAR_NAME.matcher(line);
        String last = null;
        while (vm.find()) {
            last = vm.group(1);
        }
        return last;
    }

    /**
     * 判断是否属于「把字段名/常量名本身当值」这种误报。
     *
     * <p>真实项目里非常常见这种写法：
     * <pre>
     *   private static final String PASSWORD = "password";
     *   private static final String USERNAME = "username";
     * </pre>
     * 这是把字段名/占位默认值以字符串形式写死，<b>不是真凭证</b>。
     *
     * <p>实测 hccloud 上 3 条 P0 全部属于此类 —— 一个 P0 全是假警报，
     * 比没有 P0 更糟糕：客户会直接判定这工具不可信。
     */
    private boolean isSelfNamedConstant(String variableName, String value) {
        String v = value.trim().toLowerCase();

        // 1) 值与变量名相同（去掉下划线/驼峰差异后比较）
        String normalizedVar = variableName.toLowerCase().replace("_", "");
        String normalizedVal = v.replace("_", "").replace("-", "").replace(".", "");
        if (normalizedVar.equals(normalizedVal)) {
            return true;
        }
        // 2) 值以变量名开头/结尾（prefix_xxx / xxx_suffix）
        if (normalizedVal.startsWith(normalizedVar) || normalizedVal.endsWith(normalizedVar)) {
            return true;
        }
        // 3) 属于弱值白名单
        return v.equals("password") || v.equals("passwd") || v.equals("secret")
                || v.equals("token") || v.equals("username") || v.equals("user")
                || v.equals("admin") || v.equals("null") || v.equals("none")
                || v.equals("default") || v.equals("value") || v.equals("string")
                || v.isEmpty();
    }
}