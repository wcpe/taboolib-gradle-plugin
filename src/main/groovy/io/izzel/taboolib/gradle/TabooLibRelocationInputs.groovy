package io.izzel.taboolib.gradle

import java.util.concurrent.Callable

/** 惰性取得最终配置，兼容下游在 afterEvaluate 中修改原任务的属性。 */
class TabooLibRelocationInputs implements Callable<Map<String, Object>>, Serializable {

    private final TabooLibRelocation configuration

    TabooLibRelocationInputs(TabooLibRelocation configuration) {
        this.configuration = configuration
    }

    @Override
    Map<String, Object> call() {
        return configuration.inputProperties
    }
}
