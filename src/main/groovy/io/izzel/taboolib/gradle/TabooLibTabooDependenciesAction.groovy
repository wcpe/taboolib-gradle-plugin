package io.izzel.taboolib.gradle

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.provider.Provider

/**
 * 把插件内置的 TabooLib 模块依赖追加到 `taboo` 配置**末尾**。
 *
 * 为什么不用 `Configuration.dependencies.addAllLater`：那个 API 会把惰性依赖排在
 * 用户显式声明的依赖**之前**（实测：注入项先出现）。而 `afterEvaluate` 时代的顺序是
 * 「用户先 `taboo project(':x')`、内置模块后加」，这个顺序会一路传递到 `jar` 的打包顺序，
 * 最终改变产物里 ZIP 条目的排列（内容相同、字节不同）。
 *
 * `Configuration.withDependencies` 在解析前回调，往集合末尾追加，正好复现原有顺序。
 * 回调对象必须是可序列化的普通类（不能是 Groovy 闭包，闭包会捕获脚本对象导致
 * 配置缓存判失败），因此这里显式实现 [Action] + `Serializable`。
 */
class TabooLibTabooDependenciesAction implements Action<DependencySet>, Serializable {

    private final Provider<List> modules

    TabooLibTabooDependenciesAction(Provider<List> modules) {
        this.modules = modules
    }

    @Override
    void execute(DependencySet dependencies) {
        dependencies.addAll(modules.get())
    }
}
