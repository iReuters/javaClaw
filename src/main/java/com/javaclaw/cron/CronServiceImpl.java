package com.javaclaw.cron;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务服务简单实现：持久化存于 cronStorePath；setOnJob 注册回调；start 后到期触发 onJob。
 * 当前为占位：不解析 cron 表达式，不调度任务；仅保存回调供后续扩展。
 */
@Component
public class CronServiceImpl implements CronService {

    private final Path cronStorePath;
    private volatile CronService.CronJobCallback callback;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CronServiceImpl(@Value("${javaclaw.agents.workspace}") String workspace) {
        this.cronStorePath = Paths.get(workspace, "cron");
    }

    @Override
    public void setOnJob(CronService.CronJobCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start() {
        running.set(true);
        // 占位：未实现实际调度，后续可在此启动调度线程
    }

    @Override
    public void stop() {
        running.set(false);
    }

    /** 供 Gateway 等调用：模拟一次任务触发（测试或手动） */
    public void trigger(CronService.CronJob job) {
        if (callback != null && job != null) {
            callback.onJob(job);
        }
    }
}
