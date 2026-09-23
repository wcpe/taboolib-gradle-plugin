package io.izzel.taboolib.gradle

import org.gradle.api.Project
import org.gradle.util.internal.NameMatcher

/** 只在配置期使用 Gradle 自身匹配器，把 -x 选择结果转换为可缓存的布尔输入。 */
final class TabooLibTaskExclusions {

    static boolean isExcluded(Project project, String taskName) {
        def selectors = project.gradle.startParameter.excludedTaskNames
        if (selectors.isEmpty()) {
            return false
        }
        def root = project.rootProject
        def startDirectory = project.gradle.startParameter.projectDir ?: project.gradle.startParameter.currentDir
        def selectedProject = root.allprojects.find { it.projectDir == startDirectory } ?: root
        return selectors.any { String selector ->
            def segments = selector.tokenize(':')
            if (segments.isEmpty()) {
                return false
            }
            Project target = selector.startsWith(':') ? root : selectedProject
            def qualified = selector.contains(':')
            for (String segment : segments.dropRight(1)) {
                target = new NameMatcher().find(segment, target.childProjects)
                if (target == null) {
                    return false
                }
            }
            def scope = qualified ? [target] : target.allprojects
            if (!scope.contains(project)) {
                return false
            }
            def availableNames = scope.collectMany { it.tasks.names }.toSet()
            return new NameMatcher().find(segments.last(), availableNames) == taskName
        }
    }
}
