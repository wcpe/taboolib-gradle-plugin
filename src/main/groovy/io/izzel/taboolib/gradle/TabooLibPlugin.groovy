package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.BuilderFabric
import io.izzel.taboolib.gradle.description.Platforms
import org.gradle.api.GradleException
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
            url project.uri("https://repo.tabooproject.org/repository/releases/")
        }
        project.repositories.maven {
            url project.uri("https://repo.spongepowered.org/maven")
        }

        // 注册扩展（注入 project，供 Fabric 模式在配置阶段装配依赖）
        def tabooExt = project.extensions.create('taboolib', TabooLibExtension, project)
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
            // Fabric 模式：依赖与元数据已在 fabric{} 配置阶段装配（见 setupFabric），此处跳过 Bukkit 系的
            // 模块依赖装配、ASM 重定位/重打包与 implementation 继承，交给 Loom remapJar 出包。
            if (tabooExt.fabric != null) {
                return
            }
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
                        if (it.isDirectory()) {
                            it
                        } else if (it.name.endsWith(".jar")) {
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
                task.tabooExt = tabooExt
                task.project = project
                task.inJar = task.inJar ?: jarTaskProvider.get().archiveFile.get().asFile
                task.relocations = tabooExt.relocation
                task.classifier = tabooExt.classifier
                task.api = api

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

    /**
     * Fabric 模式装配：检测 Loom → 接管平台依赖（minecraft/yarn/loader/fabric-api）+ 接入 taboolib-fabric
     * 平台库（modImplementation + include 单 jar）+ common API（compileOnly）；元数据写入资源目录由 Loom 打包。
     * 不做 ASM 重定位/重打包，也不让 implementation 继承 Loom 的 include 配置。
     */
    static void setupFabric(Project project, TabooLibExtension ext) {
        def f = ext.fabric
        if (!project.pluginManager.hasPlugin('fabric-loom')) {
            throw new GradleException("使用 taboolib { fabric { } } 需先在 plugins {} 中应用 fabric-loom（且置于 io.izzel.taboolib 之前）")
        }
        ['mcVersion', 'yarnMappings', 'loaderVersion', 'fabricApiVersion'].each { String p ->
            if (f."$p" == null || (f."$p" as String).isEmpty()) {
                throw new GradleException("taboolib.fabric.$p 不能为空")
            }
        }
        def core = ext.version.taboolib
        def platformVer = f.platformVersion ?: (core != null ? "${core}+${f.mcVersion}" : null)
        if (platformVer == null) {
            throw new GradleException("无法推断 platform-fabric-impl 版本，请设置 taboolib.fabric.platformVersion 或 taboolib.version.taboolib")
        }

        // Loom 平台坐标
        project.dependencies.add('minecraft', "com.mojang:minecraft:${f.mcVersion}")
        project.dependencies.add('mappings', "net.fabricmc:yarn:${f.yarnMappings}:v2")
        project.dependencies.add('modImplementation', "net.fabricmc:fabric-loader:${f.loaderVersion}")
        project.dependencies.add('modImplementation', "net.fabricmc.fabric-api:fabric-api:${f.fabricApiVersion}")
        // taboolib-fabric 平台库 mod：modImplementation 供 dev 重映射，include 打进单 jar(JiJ)
        def platformDep = "io.izzel.taboolib:platform-fabric-impl:${platformVer}"
        project.dependencies.add('modImplementation', platformDep)
        project.dependencies.add('include', platformDep) // 此处 include 为 Loom 的 JiJ 配置
        // 写插件用的 TabooLib API（编译期）
        if (core != null) {
            ['common', 'common-util', 'common-platform-api'].each {
                project.dependencies.add('compileOnly', "io.izzel.taboolib:${it}:${core}")
            }
        }
        if (ext.version.coroutines != null) {
            project.dependencies.add('compileOnly', "org.jetbrains.kotlinx:kotlinx-coroutines-core:${ext.version.coroutines}")
        }

        // 元数据 → 生成资源目录（processResources 打入 jar，Loom remapJar 原样带上）
        def metaDir = project.layout.buildDirectory.dir('generated/taboolib-fabric-meta')
        project.sourceSets.main.resources.srcDir(metaDir)
        def genTask = project.tasks.register('generateFabricMeta') { t ->
            t.group = 'taboolib'
            t.outputs.dir(metaDir)
            t.doLast {
                def dir = metaDir.get().asFile
                def taboo = new File(dir, 'META-INF/taboolib')
                taboo.mkdirs()
                new File(taboo, 'project.properties').setText("group=${project.group}\n", 'UTF-8')
                new File(taboo, 'version.properties').setText(buildFabricVersion(project, ext), 'UTF-8')
                new File(taboo, 'env.properties').setText(buildFabricEnv(ext), 'UTF-8')
                def modJson = new File(dir, 'fabric.mod.json')
                modJson.bytes = BuilderFabric.build(ext.des, project, f)
            }
        }
        project.tasks.named('processResources').configure { it.dependsOn(genTask) }
    }

    /** Fabric 的 version.properties：强制 skip-kotlin-relocate / skip-taboolib-relocate（平台库已是预重映射、不重定位用户类） */
    private static String buildFabricVersion(Project project, TabooLibExtension ext) {
        def kotlinVersion = ext.version.skipKotlin ? 'null' : KotlinPluginWrapperKt.getKotlinPluginVersion(project)
        return [
                "kotlin=${kotlinVersion}",
                "kotlin-coroutines=${ext.version.coroutines}",
                "taboolib=${ext.version.taboolib}",
                "skip-kotlin-relocate=true",
                "skip-taboolib-relocate=true"
        ].join('\n') + '\n'
    }

    /** Fabric 的 env.properties：强制 enable-isolated-classloader（Knot 下隔离加载） */
    private static String buildFabricEnv(TabooLibExtension ext) {
        def modules = new HashSet<String>(ext.env.modules)
        modules.removeIf { String i -> i.startsWith('common') }
        return [
                "debug=${ext.env.debug}",
                "enable-isolated-classloader=true",
                "disable-when-primitive-loader-error=${ext.env.disableWhenPrimitiveLoaderError}",
                "repo-central=${ext.env.repoCentral}",
                "repo-taboolib=${ext.env.repoTabooLib}",
                "file-libs=${ext.env.fileLibs}",
                "file-assets=${ext.env.fileAssets}",
                "module=${modules.join(',')}"
        ].join('\n') + '\n'
    }
}
