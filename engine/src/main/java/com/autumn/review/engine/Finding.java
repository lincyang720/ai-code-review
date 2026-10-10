package com.autumn.review.engine;

/**
 * 一条评审意见。
 *
 * <p>刻意做成"可直接回帖到 GitLab MR"的结构：定位到行、给出证据、给出可执行的修法。
 * 这是哈佛研究里"PR 需要返工率翻倍"的反面 —— 我们要做的不是多提意见，
 * 而是让人工评审只剩下"判断"这一件事。
 */
public class Finding {

    /** 规则编号，形如 SEC-001，便于客户侧关掉噪音规则。 */
    private final String ruleId;

    /** 规则分类：安全 / 异常 / 日志 / 依赖 / 规范。 */
    private final String category;

    private final Severity severity;

    private final String filePath;

    /** 1-based 行号；无法定位到具体行时为 0。 */
    private final int lineNumber;

    /** 命中的原始代码行，作为证据。 */
    private final String evidence;

    /** 问题描述：说清楚"哪里不对"。 */
    private final String message;

    /** 修法建议：说清楚"怎么改"。这是最影响采纳率的一段。 */
    private final String suggestion;

    /** 规则是否确定性命中。true = 无需 LLM 也能确定，false = 启发式，需要人工确认。 */
    private final boolean deterministic;

    public Finding(String ruleId, String category, Severity severity, String filePath,
                   int lineNumber, String evidence, String message, String suggestion,
                   boolean deterministic) {
        this.ruleId = ruleId;
        this.category = category;
        this.severity = severity;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.evidence = evidence == null ? "" : evidence.trim();
        this.message = message;
        this.suggestion = suggestion;
        this.deterministic = deterministic;
    }

    public String ruleId() {
        return ruleId;
    }

    public String category() {
        return category;
    }

    public Severity severity() {
        return severity;
    }

    public String filePath() {
        return filePath;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String evidence() {
        return evidence;
    }

    public String message() {
        return message;
    }

    public String suggestion() {
        return suggestion;
    }

    public boolean deterministic() {
        return deterministic;
    }

    /** 输出成 GitLab MR 评论的 Markdown。 */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("**[").append(severity.code()).append(" ").append(category).append("] ")
          .append(message).append('\n');
        if (lineNumber > 0) {
            sb.append("位置：第 ").append(lineNumber).append(" 行\n");
        }
        if (!evidence.isEmpty()) {
            sb.append("```\n").append(evidence).append("\n```\n");
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append("建议：").append(suggestion).append('\n');
        }
        sb.append("规则：").append(ruleId).append(deterministic ? "（确定性命中）" : "（启发式，建议人工确认）");
        return sb.toString();
    }

    @Override
    public String toString() {
        String loc = lineNumber > 0 ? (":" + lineNumber) : "";
        return "[" + severity.code() + "] " + ruleId + " " + filePath + loc + " — " + message;
    }
}