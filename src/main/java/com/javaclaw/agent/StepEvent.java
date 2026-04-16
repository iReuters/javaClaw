package com.javaclaw.agent;

/**
 * SSE 步骤事件
 * <p>
 * 用于 SSE 流式传输时，表示一个步骤的开始或结束
 */
public class StepEvent {

    /**
     * 事件类型
     * - step_start: 新步骤开始
     * - step_end: 步骤完成
     * - done: 所有步骤完成
     */
    private String event;

    /**
     * 步骤序号（从1开始）
     */
    private int stepNumber;

    /**
     * 步骤数据
     */
    private AgentStep step;

    /**
     * 最终内容（仅 done 事件时有值）
     */
    private String finalContent;

    /**
     * 创建步骤开始事件
     */
    public static StepEvent stepStart(int stepNumber, AgentStep step) {
        StepEvent e = new StepEvent();
        e.setEvent("step_start");
        e.setStepNumber(stepNumber);
        e.setStep(step);
        return e;
    }

    /**
     * 创建步骤结束事件
     */
    public static StepEvent stepEnd(int stepNumber, AgentStep step) {
        StepEvent e = new StepEvent();
        e.setEvent("step_end");
        e.setStepNumber(stepNumber);
        e.setStep(step);
        return e;
    }

    /**
     * 创建完成事件
     */
    public static StepEvent done(String finalContent) {
        StepEvent e = new StepEvent();
        e.setEvent("done");
        e.setFinalContent(finalContent);
        return e;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public AgentStep getStep() { return step; }
    public void setStep(AgentStep step) { this.step = step; }
    public String getFinalContent() { return finalContent; }
    public void setFinalContent(String finalContent) { this.finalContent = finalContent; }
}
