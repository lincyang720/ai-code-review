package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LOG-001：日志使用问题。
 *
 * <p>分两类：
 * A) 日志字符串拼接 —— 无论日志级别是否开启，拼接都会执行，是隐性性能损耗
 * B) 用 log.debug 记录关键业务事件 —— 线上通常只开 info/debug，关键事件丢失
 */
public class LoggingRule implements ReviewRule {

    /** log.xxx("..." + var) 形式 */
    private static final Pattern CONCAT = Pattern.compile(
            "log\\.(trace|debug|info|warn|error)\\s*\\(\\s*\"[^\"]*\"\\s*\\+");

    private static final Pattern SYSTEM_OUT = Pattern.compile(
            "System\\.(out|err)\\.(print|println|printf)");

    /** 关键业务词，用 debug 级别记录会导致线上丢日志 */
    private static final String[] CRITICAL_KEYWORDS = {
            "下单", "支付", "退款", "删除", "权限", "登录", "注册", "资金", "对账", "结算"
    };

    @Override
    public String id() {
        return "LOG-001";
    }

    @Override
    public String category() {
        return "日志";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int ln = i + 1;

            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }

            Matcher m = CONCAT.matcher(line);
            if (m.find()) {
                out.add(new Finding(id(), category(), Severity.MAJOR, filePath, ln,
                        trimmed,
                        "日志使用字符串拼接。无论日志级别是否开启，拼接都会执行，"
                                + "高频路径下这是隐性开销，也让日志难以结构化检索。",
                        "改用占位符：`log.info(\"订单 {} 创建成功, id={}\", userId, orderId);` —— "
                                + "单引号版本连字符串都不用创建。",
                        true));
            }

            if (SYSTEM_OUT.matcher(line).find()) {
                out.add(new Finding(id(), category(), Severity.MAJOR, filePath, ln,
                        trimmed,
                        "使用 System.out/err 输出。绕过日志框架，缺少级别、缺少 traceId，"
                                + "日志采集侧通常直接丢弃。",
                        "统一改用 SLF4J logger，项目起始类上加 `@Slf4j`（Lombok）或手动 `private static final Logger log = LoggerFactory.getLogger(X.class);`",
                        true));
            }

            String lower = line;
            for (String kw : CRITICAL_KEYWORDS) {
                if (lower.contains(kw) && line.contains("log.debug")) {
                    out.add(new Finding(id(), category(), Severity.CRITICAL, filePath, ln,
                            trimmed,
                            "关键业务事件（" + kw + "）用 debug 级别记录。线上默认只开 info，"
                                    + "该日志不会落盘 —— 恰好是最需要它的场景。",
                            "改为 info；确属高频且可降级的用 debug，但要明确降级策略。",
                            true));
                    break;
                }
            }
        }
        return out;
    }
}