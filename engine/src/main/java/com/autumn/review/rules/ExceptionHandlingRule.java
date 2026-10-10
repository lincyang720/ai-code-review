package com.autumn.review.rules;

import com.autumn.review.engine.Finding;
import com.autumn.review.engine.ReviewRule;
import com.autumn.review.engine.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXC-001：异常处理缺失与吞异常。
 *
 * <p>对应哈佛研究里"返工 PR 占比近乎翻倍"最常见的根因：
 * AI 生成的代码最擅长写 happy path，最不擅长写错误分支。
 *
 * <p>覆盖三类：
 * A) 空 catch / 只打印不处理 —— 吞异常，故障静默
 * B) catch 了但丢失原始信息 —— rethrow 时不传 cause，排障链条断掉
 * C) 抛裸 RuntimeException —— 上层无法分类处理
 */
public class ExceptionHandlingRule implements ReviewRule {

    private static final Pattern CATCH = Pattern.compile(
            "catch\\s*\\(\\s*([A-Za-z0-9_.|\\s]+?)\\s*\\)\\s*\\{\\s*\\}?");

    private static final Pattern PRINT_STACK = Pattern.compile("printStackTrace\\s*\\(");

    private static final Pattern BARE_RUNTIME = Pattern.compile(
            "throw\\s+new\\s+RuntimeException\\s*\\(\\s*[\"'][^\"']*[\"']\\s*\\)");

    /**
     * catch 块起始。用于判断某行是否处于 catch 作用域内。
     * 只有在 catch 块里抛新异常才可能丢失 cause；在方法顶层抛业务异常是完全正常的写法，
     * 不能报——否则在真实项目上会产生海量误报（实测 4645 文件里误报 2241 条）。
     */
    private static final Pattern CATCH_START = Pattern.compile(
            "catch\\s*\\(");

    /** throw new XxxException(...)，用于判断参数个数（是否带 cause）。 */
    private static final Pattern THROW_NEW = Pattern.compile(
            "throw\\s+new\\s+\\w+(Exception|Throwable|Error)\\s*\\(");

    @Override
    public String id() {
        return "EXC-001";
    }

    @Override
    public String category() {
        return "异常";
    }

    @Override
    public List<Finding> scan(String filePath, String content) {
        List<Finding> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int ln = i + 1;
            boolean inCatch = isInsideCatch(lines, i);

            // A) 空 catch
            Matcher c = CATCH.matcher(line);
            if (c.find() && isEmptyCatchBody(lines, i)) {
                out.add(new Finding(id(), category(), Severity.CRITICAL, filePath, ln,
                        trimmed,
                        "捕获了 " + c.group(1).trim() + " 但什么也没做（空 catch）。"
                                + "这类代码会让故障静默消失，线上出问题后无法定位。",
                        "要么向上抛出（并保留 cause），要么至少打日志并降级；"
                                + "确需忽略请写明原因，例如 `catch (IgnoreException e) { /* 契约允许为空 */ }`。",
                        true));
            }

            // B) printStackTrace
            if (PRINT_STACK.matcher(line).find()) {
                out.add(new Finding(id(), category(), Severity.CRITICAL, filePath, ln,
                        trimmed,
                        "使用 printStackTrace 输出异常。生产环境该输出通常被重定向到 stdout，"
                                + "不进日志系统，等于丢掉了最关键的排障信息。",
                        "改用 SLF4J：`log.error(\"下单失败, orderId={}\", orderId, e);`"
                                + " —— 注意把异常对象作为最后一个参数传入，不要用字符串拼接。",
                        true));
            }

            // C) 裸 RuntimeException
            Matcher b = BARE_RUNTIME.matcher(line);
            if (b.find()) {
                out.add(new Finding(id(), category(), Severity.MAJOR, filePath, ln,
                        trimmed,
                        "抛出裸 RuntimeException。上层无法按类型分类处理，只能一起兜底，"
                                + "等于把异常语义丢掉了。",
                        "定义业务专属异常（如 `OrderNotFoundException`），"
                                + "让调用方能针对不同失败原因采取不同策略。",
                        true));
            }

            /*
             * D) catch 块内重新抛出但丢了 cause。
             * 关键：必须限定 inCatch。在方法顶层 `throw new BizException("订单不存在")`
             * 是完全正常的写法，早期版本没做这个限定，在 hccloud 上误报了 2241 条。
             */
            if (inCatch && !c.find() && isThrowWithoutCause(line)) {
                out.add(new Finding(id(), category(), Severity.MAJOR, filePath, ln,
                        trimmed,
                        "在 catch 块内重新抛出新异常，但没有传入原始 cause，异常链在此断裂 —— "
                                + "原始堆栈丢失，线上排障会断在最关键的一环。",
                        "改为 `throw new XxxException(\"上下文描述\", e);`，把 catch 的异常对象"
                                + "作为最后一个参数传入。若确实无需 cause，请改用受检异常声明而非吞掉。",
                        true));
            }
        }
        return out;
    }

    /**
     * 判断某一行是否处于 catch 块作用域内。
     * 做法：从该行往上回溯，遇到 catch 记+1，遇到大括号闭合记-1，净值为正则在 catch 内。
     */
    private boolean isInsideCatch(String[] lines, int idx) {
        int depth = 0;
        for (int i = idx - 1; i >= 0 && i > idx - 40; i--) {
            String t = lines[i].trim();
            if (CATCH_START.matcher(t).find()) {
                depth++;
                continue;
            }
            for (int k = 0; k < t.length(); k++) {
                char ch = t.charAt(k);
                if (ch == '}') {
                    depth--;
                } else if (ch == '{') {
                    // 闭合了外层块，说明已经出了 catch
                    if (depth <= 0) {
                        return false;
                    }
                }
            }
        }
        return depth > 0;
    }

    /** throw new XxxException(单参数) —— 没有第二个 cause 参数。 */
    private boolean isThrowWithoutCause(String line) {
        java.util.regex.Matcher m = THROW_NEW.matcher(line);
        if (!m.find()) {
            return false;
        }
        // 取括号内内容
        int open = line.indexOf('(', m.start());
        if (open < 0) {
            return false;
        }
        int close = line.lastIndexOf(')');
        if (close <= open) {
            return false;
        }
        String args = line.substring(open + 1, close);
        // 已有两个参数（描述 + cause）则不报
        return args.split(",").length < 2;
    }

    /** 判断 catch 块体是否为空（下一非空行是 }）。 */
    private boolean isEmptyCatchBody(String[] lines, int catchLineIdx) {
        for (int j = catchLineIdx + 1; j < Math.min(catchLineIdx + 4, lines.length); j++) {
            String t = lines[j].trim();
            if (t.isEmpty()) {
                continue;
            }
            return t.startsWith("}");
        }
        return false;
    }
}