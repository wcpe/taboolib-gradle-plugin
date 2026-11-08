package io.izzel.taboolib.gradle

import org.gradle.api.Project
import org.gradle.util.internal.NameMatcher

/** 只在配置期使用 Gradle 自身匹配器，把 -x 选择结果转换为可缓存的布尔输入。 */
final class TabooLibTaskExclusions {

    /**
     * 判断 [taskName] 是否被 `-x` 排除。
     *
     * ## Isolated Projects 兼容
     *
     * 原实现遍历 `project.rootProject.allprojects` / `target.allprojects`，
     * 并读 `it.tasks.names`。这三者在 Gradle Isolated Projects 下都是跨项目访问：
     * 若用户在 IP 构建里用了 `-x`，会直接刷出大量 configuration-cache problem。
     *
     * 新实现全程只用**字符串**（项目路径）与**本项目**的 tasks：
     *   · `:a:b` 形式的选择器：比较字符串路径，不遍历项目树；
     *   · 无冒号形式：只在本项目上匹配。
     *
     * 语义上与 Gradle 自身的 -x 一致（-x 本来就不递归到子项目）。
     */
    static boolean isExcluded(Project project, String taskName) {
        def selectors = project.gradle.startParameter.excludedTaskNames
        if (selectors.isEmpty()) {
            return false
        }
        // 只用字符串：本项目路径。不碰 rootProject / allprojects。
        String selfPath = project.path
        def matcher = new NameMatcher()

        return selectors.any { String selector ->
            if (!selector) {
                return false
            }
            if (selector.contains(':')) {
                // 全限定形式：`:task`（根项目） / `:a:b:task` / `a:b:task`
                String normalized = selector.startsWith(':') ? selector : ':' + selector
                int lastColon = normalized.lastIndexOf(':')
                // 根项目任务写作 `:task`，其 lastColon 恰好为 0（不能当成无效）。
                if (lastColon < 0) {
                    return false
                }
                // ownerPath 对于 `:task` 应为 ":"（根项目），对于 `:a:task` 为 ":a"。
                String ownerPath = (lastColon == 0) ? ':' : normalized.substring(0, lastColon)
                String taskSelector = normalized.substring(lastColon + 1)
                if (!taskSelector) {
                    return false
                }
                // 只在「选择器所属项目就是本项目」时比较。
                if (ownerPath != selfPath) {
                    return false
                }
                // 读**本项目**的 tasks.names（IP 允许）。
                // 用 NameMatcher 支持任务名前缀缩写（如 `-x taboolibM`）。
                return matcher.find(taskSelector, project.tasks.names) == taskName
            }
            // 无冒号：只在本项目上匹配（不遍历子项目，避免 IP 违规）。
            return matcher.find(selector, project.tasks.names) == taskName
        }
    }
}
