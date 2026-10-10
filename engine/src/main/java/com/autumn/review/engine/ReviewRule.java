package com.autumn.review.engine;

/**
 * 一条评审规则。
 *
 * <p>设计原则：规则只处理"有明确对错"的事（安全、异常、日志、依赖、规范）。
 * 架构取舍、业务语义这类需要理解上下文的问题，交给人工或 LLM 辅助，
 * 不在确定性规则里做 —— 这是这套流水线能真正落地的前提。
 */
public interface ReviewRule {

    /** 规则编号，形如 SEC-001。 */
    String id();

    /** 规则分类。 */
    String category();

    /**
     * 扫描一个文件，返回发现的问题。
     *
     * @param filePath 文件路径（用于报告）
     * @param content  文件全部内容（按行 split 后传入更省内存，这里传原文）
     * @return 命中列表，无命中返回空列表（不要返回 null）
     */
    java.util.List<Finding> scan(String filePath, String content);
}