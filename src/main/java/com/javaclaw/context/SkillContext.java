package com.javaclaw.context;

/**
 * ThreadLocal 上下文，用于在 AOP 切面中获取当前 skill 名称
 */
public class SkillContext {

    private static final ThreadLocal<String> CURRENT_SKILL = new ThreadLocal<>();

    public static void setSkill(String skillName) {
        CURRENT_SKILL.set(skillName);
    }

    public static String getSkill() {
        return CURRENT_SKILL.get();
    }

    public static void clear() {
        CURRENT_SKILL.remove();
    }
}