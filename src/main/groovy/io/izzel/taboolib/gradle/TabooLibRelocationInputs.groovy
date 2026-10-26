package io.izzel.taboolib.gradle

import java.util.concurrent.Callable

/**
 * 惰性取得最终配置。
 *
 * 在真正读取配置前先让 [TabooLibRelocationSource] 把扩展的当前值填进配置对象，
 * 因此无论 `jar` 任务在何时被实例化、这段输入规格在何时被求值，
 * 读到的都是用户脚本执行完毕后的最终值。
 *
 * 只持有可序列化的普通对象，可安全进入配置缓存。
 */
class TabooLibRelocationInputs implements Callable<Map<String, Object>>, Serializable {

    private final TabooLibRelocation configuration

    private final TabooLibRelocationSource source

    TabooLibRelocationInputs(TabooLibRelocation configuration, TabooLibRelocationSource source) {
        this.configuration = configuration
        this.source = source
    }

    @Override
    Map<String, Object> call() {
        source.applyTo(configuration)
        return configuration.inputProperties
    }
}
