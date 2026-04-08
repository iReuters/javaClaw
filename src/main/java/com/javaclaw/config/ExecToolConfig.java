package com.javaclaw.config;

import lombok.Data;

/**
 * 执行类工具（如 shell）的配置：超时等。对应 config.json 中 tools.exec。
 */
@Data
public class ExecToolConfig {

    /** 单次执行超时秒数 */
    private int timeoutSeconds = 60;

    // 手动添加getter方法，因为Lombok可能没有正确生成
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
