package io.izzel.taboolib.gradle

import org.gradle.api.Action
import org.gradle.api.Task

/**
 * jar 的末尾动作仅保存配置数据，不捕获 Project 或其他任务。
 *
 * 执行前先同步扩展配置（见 [TabooLibRelocationSource]），使产物元数据
 * 一定来自用户脚本执行后的最终值。
 */
class TabooLibRelocateAction implements Action<Task>, Serializable {

    private final TabooLibRelocation configuration

    private final TabooLibRelocationSource source

    TabooLibRelocateAction(TabooLibRelocation configuration, TabooLibRelocationSource source) {
        this.configuration = configuration
        this.source = source
    }

    @Override
    void execute(Task task) {
        source.applyTo(configuration)
        configuration.relocate(task.logger)
    }
}
