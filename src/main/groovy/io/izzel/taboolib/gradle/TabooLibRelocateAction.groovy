package io.izzel.taboolib.gradle

import org.gradle.api.Action
import org.gradle.api.Task

/** jar 的末尾动作仅保存配置数据，不捕获 Project 或其他任务。 */
class TabooLibRelocateAction implements Action<Task>, Serializable {

    private final TabooLibRelocation configuration

    TabooLibRelocateAction(TabooLibRelocation configuration) {
        this.configuration = configuration
    }

    @Override
    void execute(Task task) {
        configuration.relocate(task.logger)
    }
}
