package com.autumn.review.engine;

/**
 * 严重级别。与哈佛研究里"评审拖慢 49%"直接对应：
 * HIGH/P0 的问题如果不在提交前拦住，就会变成昂贵的返工 PR。
 */
public enum Severity {

    /** 阻断级：安全漏洞、敏感信息硬编码、会导致线上事故。必须拦住合并。 */
    BLOCKER("P0", "阻断"),

    /** 必改：明确的缺陷、异常处理缺失、依赖风险。不改会持续累积技术债。 */
    CRITICAL("P1", "必改"),

    /** 建议：规范类问题。不影响运行，但影响可维护性与团队一致性。 */
    MAJOR("P2", "建议"),

    /** 提示：可以改进，不影响交付。 */
    MINOR("P3", "提示");

    private final String code;
    private final String label;

    Severity(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 是否属于"必须在提交前解决"的范畴。 */
    public boolean mustFixBeforeMerge() {
        return this == BLOCKER || this == CRITICAL;
    }
}