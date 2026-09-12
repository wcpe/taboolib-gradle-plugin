package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.Platforms
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt

class TabooLibPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 添加仓库
        project.repositories.maven {
            url = project.uri("https://repo.tabooproject.org/repository/releases/")
        }
        project.repositories.maven {
            url = project.uri("https://repo.spongepowered.org/maven")
        }

        // 注册扩展
        def tabooExt = project.extensions.create('taboolib', TabooLibExtension)
        // 注册任务
        def tabooTask = project.tasks.maybeCreate('taboolibMainTask', TabooLibMainTask)
        tabooTask.group = "taboolib"
        // 注册任务 - 刷新依赖
        project.tasks.maybeCreate('taboolibRefreshDependencies')
        project.tasks.taboolibRefreshDependencies.group = "taboolib"
        project.tasks.taboolibRefreshDependencies.doLast {
            def taboolibFile = new File("../../caches/modules-2/files-2.1/io.izzel.taboolib").canonicalFile
            taboolibFile.listFiles()?.each { module ->
                def file = new File(taboolibFile, "${module.name}/${tabooExt.version.taboolib}")
                if (file.exists()) {
                    file.deleteDir()
                    System.out.println("Delete $file")
                }
            }
        }
        // 注册任务 - 构建 API 版本
        project.tasks.maybeCreate('taboolibBuildApi')
        project.tasks.taboolibBuildApi.group = "taboolib"

        // 注册配置
        def taboo = project.configurations.maybeCreate('taboo')     // 这个名字起的着实二逼
        def include = project.configurations.maybeCreate('include') // 这个代替 "taboo"

        // 添加依赖以及重定向配置
        project.afterEvaluate {
            def api = false
            try {
                project.tasks.taboolibBuildApi.dependsOn(project.tasks.build)
                api = project.gradle.startParameter.taskNames.any { it == "taboolibBuildApi" }
            } catch (Throwable ignored) {
            }

            // 继承 "taboo", "include" 配置
            project.configurations.implementation.extendsFrom(taboo)
            project.configurations.implementation.extendsFrom(include)

            // 自动引入 com.mojang:datafixerupper:4.0.26
            project.dependencies.add('compileOnly', 'com.mojang:datafixerupper:4.0.26')
            // 自动引入 org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3
            if (tabooExt.version.coroutines != null) {
                project.dependencies.add('compileOnly', 'org.jetbrains.kotlinx:kotlinx-coroutines-core:' + tabooExt.version.coroutines)
                project.dependencies.add('testImplementation', 'org.jetbrains.kotlinx:kotlinx-coroutines-core:' + tabooExt.version.coroutines)
            }
            // 自动引入 TabooLib 模块
            if (tabooExt.version.taboolib != null) {
                tabooExt.env.modules.each {
                    def dependency = project.dependencies.create("io.izzel.taboolib:${it}:${tabooExt.version.taboolib}")
                    if (api || isCoreModule(it) && !tabooExt.subproject) {
                        project.configurations.taboo.dependencies.add(dependency)
                    } else {
                        project.configurations.compileOnly.dependencies.add(dependency)
                        project.configurations.testImplementation.dependencies.add(dependency)
                    }
                }
            }

            def jarTaskProvider = project.tasks.named('jar', Jar)
            jarTaskProvider.configure { Jar task ->
                task.finalizedBy(tabooTask)
                // 用 Provider 包装 taboo configuration 的解析，推迟到 task 执行阶段。
                // 之前是 task.from(taboo.collect { ... })——Groovy collect 会立即调用
                // Configuration.iterator() 触发 cross-project resolve，强制提前 evaluate
                // 所有 taboo(project(":...")) 引用的上游子项目。Gradle 9 严格的
                // DefaultMutationGuard 会因此拒绝部分子项目（如 PublishingPlugin
                // 想注册 afterEvaluate 监听器，但目标 project 已配置完成）。
                // 改用 project.provider { ... } 后求值推迟到 task 执行阶段，避免在
                // 配置阶段触发任何 cross-project resolve。
                task.from(project.provider {
                    taboo.collect { // 在这里打包 "taboo" 依赖
                        // 不要用 File.isDirectory() 判断：那是一次文件系统探测，会让每个依赖产物的
                        // 路径（各子项目 build/libs/*.jar）登记为 Gradle 配置缓存输入 —— 任一模块
                        // 重新构建都会令配置缓存条目失效，导致每次构建都重新配置整个工程。
                        // 改按扩展名判断：jar 展开内容，其余（目录 / 其它文件）原样收集。
                        // it.name 是纯字符串、zipTree/files 都是惰性文件树，均不触发 stat。
                        if (it.name.endsWith(".jar")) {
                            project.zipTree(it)
                        } else {
                            project.files(it)
                        }
                    }
                })
                task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                if (api) {
                    task.getArchiveClassifier().set("api")
                }
            }

            def kotlinVersion = KotlinPluginWrapperKt.getKotlinPluginVersion(project).replaceAll("[._-]", "")
            tabooTask.configure { TabooLibMainTask task ->
                task.inJar = task.inJar ?: jarTaskProvider.get().archiveFile.get().asFile
                task.relocations = tabooExt.relocation
                task.classifier = tabooExt.classifier
                task.api = api

                // 配置缓存兼容：在配置期提取所有需要的值为任务属性
                task.subproject = tabooExt.subproject
                task.exclude = tabooExt.exclude
                task.skipVersionFile = tabooExt.version.skipVersionFile
                task.skipPlatformFile = tabooExt.version.skipPlatformFile
                task.modules = tabooExt.env.modules
                task.pluginDescription = tabooExt.des
                task.envDebug = tabooExt.env.debug
                task.forceDownloadInDev = tabooExt.env.forceDownloadInDev
                task.envRepoCentral = tabooExt.env.repoCentral
                task.envRepoTabooLib = tabooExt.env.repoTabooLib
                task.envFileLibs = tabooExt.env.fileLibs
                task.envFileAssets = tabooExt.env.fileAssets
                task.enableLegacyDependencyResolver = tabooExt.env.enableLegacyDependencyResolver
                task.enableIsolatedClassloader = tabooExt.env.enableIsolatedClassloader
                task.disableOnSkippedVersion = tabooExt.env.disableOnSkippedVersion
                task.disableOnUnsupportedVersion = tabooExt.env.disableOnUnsupportedVersion
                task.disableWhenPrimitiveLoaderError = tabooExt.env.disableWhenPrimitiveLoaderError
                task.skipKotlin = tabooExt.version.skipKotlin
                task.coroutinesVersion = tabooExt.version.coroutines
                task.taboolibVersion = tabooExt.version.taboolib
                task.skipKotlinRelocate = tabooExt.version.skipKotlinRelocate
                task.skipTabooLibRelocate = tabooExt.version.skipTabooLibRelocate
                task.kotlinVersion = KotlinPluginWrapperKt.getKotlinPluginVersion(project)
                task.deleteCode = project.hasProperty("DeleteCode")
                task.projectName = project.name
                task.projectGroup = project.group.toString()
                task.projectVersion = project.version.toString()

                // 重定向
                if (!tabooExt.version.isSkipTabooLibRelocate()) {
                    def root = tabooExt.rootPackage ?: project.group.toString()
                    task.relocations['taboolib'] = root + '.taboolib'
                }
                if (!tabooExt.version.isSkipKotlinRelocate()) {
                    task.relocations['kotlin.'] = 'kotlin' + kotlinVersion + '.'
                    if (tabooExt.version.coroutines != null) {
                        def coroutinesVersion = tabooExt.version.coroutines.replaceAll("[._-]", "")
                        task.relocations['kotlinx.coroutines.'] = 'kotlin' + kotlinVersion + 'x.coroutines' + coroutinesVersion + '.'
                    }
                }
            }
        }
    }

    /**
     * 是否为必要模块
     */
    static def isCoreModule(String module) {
        return module == "common" || module == "platform-application" || Platforms.values().any { p -> p.module == module }
    }
}
