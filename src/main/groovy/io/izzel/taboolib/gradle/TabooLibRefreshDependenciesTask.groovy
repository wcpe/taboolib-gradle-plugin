package io.izzel.taboolib.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 清理本地 Gradle 缓存中的 TabooLib 模块目录，用于切换 TabooLib 版本后强制重新下载。
 *
 * 之所以做成独立任务类型而不是 `doLast { }` 闭包：闭包会捕获脚本 / 工程对象，
 * 配置缓存会以 "references a Gradle script object ... at execution time" 判失败。
 * 这里只保留可序列化的 [Property] 输入与可控的执行动作。
 */
@DisableCachingByDefault(because = "该任务只删除本地缓存目录，不产生可缓存输出。")
class TabooLibRefreshDependenciesTask extends DefaultTask {

    /** 要清理的 TabooLib 版本号；为 null 时任务不做任何事（与原实现一致）。 */
    @Input
    @Optional
    final Property<String> taboolibVersion = project.objects.property(String)

    @TaskAction
    void refresh() {
        String version = taboolibVersion.getOrNull()
        if (version == null) {
            return
        }
        // 保持原实现的相对路径语义：相对于当前工作目录的 Gradle 用户目录缓存。
        def taboolibFile = new File("../../caches/modules-2/files-2.1/io.izzel.taboolib").canonicalFile
        taboolibFile.listFiles()?.each { module ->
            def file = new File(taboolibFile, "${module.name}/${version}")
            if (file.exists()) {
                file.deleteDir()
                System.out.println("Delete $file")
            }
        }
    }
}
