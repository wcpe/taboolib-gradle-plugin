package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.Platforms
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt

/**
 * TabooLib 构建插件。
 *
 * 全流程不使用 `afterEvaluate` / `gradle.projectsEvaluated`：
 * - `afterEvaluate` 在按需配置（`org.gradle.configureondemand=true`）下会对已求值的工程抛
 *   "cannot be executed in the current context"，同时也是配置缓存的经典障碍；
 * - `gradle.projectsEvaluated` 是构建监听器，属于配置缓存要求里的禁止项。
 *
 * 替代思路是「能惰性的一律惰性」：所有会读取 `taboolib { }` 的取值点都改为 `Provider`，
 * 由 Gradle 在需要的那一刻求值 —— 依赖集合被观察时、任务输入快照时、任务动作执行时。
 * 这些时机都晚于用户脚本，因此拿得到最终配置。
 *
 * 特别注意一个坑：**不能**在 `jar.configure { }` 里直接快照扩展。
 * `jar` 可能被用户在脚本里提前实例化（`tasks.jar { manifest { ... } }`），
 * 那一刻 `taboolib { }` 尚未执行，快照到的会是默认值 —— 产物缺 plugin.yml、
 * 类也没被重定位，且不报错（静默失效）。因此取值一律经 [TabooLibRelocationSource]。
 *
 * 另外为 `subproject = true` 的模块提供精简路径：这类模块会被内联进主插件，
 * 自己既不产出插件 jar 也不做重定位，因此只保留依赖注入与 `taboo` 内联，
 * 不再挂重定位动作，也不声明重定位相关的输入输出。
 */
class TabooLibPlugin implements Plugin<Project> {

    /** 兼容任务名：历史入口，`-x` 与直接按名调用都要继续可用。 */
    static final String MAIN_TASK_NAME = 'taboolibMainTask'

    @Override
    void apply(Project project) {
        // 添加仓库
        //
        // 这里要区分两件事：
        // 1. 上游仓库必须存在且排在前面 —— NMS 平台工件（ink.ptms.core:v12004 等）只在
        //    上游发布，wcpe 上仅有 POM 而无 jar；
        // 2. wcpe 仓库只负责 io.izzel.taboolib 分组的定制版模块（X.Y.Z-wcpe.N）。
        //
        // 顺序不能反：Gradle 在某个仓库拿到 metadata 后即认定 artifact 归属该仓库，
        // 不会回退到下一个。wcpe 放前面会让 NMS 依赖在「POM 命中、jar 404」处直接失败。
        // 同理，wcpe 必须加内容过滤 —— 否则它同样会截断其它分组的解析路径。
        //
        // 注意 env.repoTabooLib 是运行期自举地址（写进产物 env.properties），
        // 运行期由 PrimitiveSettings 自动追加本处上游地址兜底，两者职责不同，不可合并。
        def env = new Env()
        project.repositories.maven {
            url = project.uri(env.repoTabooLibUpstream)
        }
        project.repositories.maven {
            url = project.uri(env.repoTabooLib)
            content {
                includeGroup(env.repoTabooLibGroup)
            }
        }
        project.repositories.maven {
            url = project.uri("https://repo.spongepowered.org/maven")
        }

        // 注册扩展
        def tabooExt = project.extensions.create('taboolib', TabooLibExtension)

        // 「构建 API 产物」判定：startParameter 在整次构建内不可变，配置期读取即可，
        // 而请求的任务名本身就是配置缓存键的一部分，不会跨调用串味。
        def api = project.gradle.startParameter.taskNames.any { it == "taboolibBuildApi" }

        // 注册配置
        def taboo = project.configurations.maybeCreate('taboo')     // 这个名字起的着实二逼
        def include = project.configurations.maybeCreate('include') // 这个代替 "taboo"

        registerAuxiliaryTasks(project, tabooExt)
        installDependencies(project, tabooExt, taboo, include, api)
        wireJarAndRelocation(project, tabooExt, taboo, api)
    }

    /**
     * 注册 `taboolibRefreshDependencies` 与 `taboolibBuildApi` 两个兼容入口。
     *
     * 两者原本在 apply 期就 eager 创建，这里改为惰性注册：任务只在真正被调用时才实例化，
     * 也让 `taboolibBuildApi` 的 `dependsOn(build)` 不必在配置期解析 `build`。
     */
    private static void registerAuxiliaryTasks(Project project, TabooLibExtension tabooExt) {
        // 注册任务 - 刷新依赖
        // 用独立任务类型而不是 doLast 闭包：闭包会捕获脚本对象，配置缓存会判失败。
        project.tasks.register('taboolibRefreshDependencies', TabooLibRefreshDependenciesTask) { TabooLibRefreshDependenciesTask task ->
            task.group = "taboolib"
            // 版本号要等用户脚本执行后才可知，用 Property 延续惰性，同时保证可被配置缓存序列化。
            task.taboolibVersion.convention(project.provider { tabooExt.version.taboolib })
        }

        // 注册任务 - 构建 API 版本
        // `build` 来自 base 插件，用 withPlugin 保证它已存在再接线（内联工程可以没有 base，
        // 原实现靠 try/catch 吞掉同样的场景）。
        def buildApiTask = project.tasks.register('taboolibBuildApi') { task ->
            task.group = "taboolib"
        }
        project.pluginManager.withPlugin('base') {
            buildApiTask.configure { task ->
                task.dependsOn(project.tasks.named('build'))
            }
        }
    }

    /**
     * 依赖注入：全部通过 `Provider` 惰性求值。
     *
     * 原先这些赋值位于 `afterEvaluate` 里，目的是读到用户在 `taboolib { }` 中设置的值。
     * `Configuration.dependencies.addAllLater(Provider)` 让依赖集合在被真正解析 / 观察时才求值，
     * 求值时机同样晚于用户脚本，但不再是「配置期末尾」这个隐式钩子 —— 后者正是按需配置下抛
     * "cannot be executed in the current context" 与配置缓存失配的来源。
     */
    private static void installDependencies(Project project, TabooLibExtension tabooExt,
                                          taboo, include, boolean api) {
        // TabooLib 模块按「是否为必要模块」分流：
        // - api 构建 或（必要模块 且 不是子模块）→ taboo（会内联进产物）
        // - 其余 → compileOnly + testImplementation（仅编译期可见）
        //
        // `taboo` 用 `withDependencies` 而不是 `addAllLater`：前者在解析前把内置模块
        // **追加到用户依赖之后**，与 `afterEvaluate` 时代的位置一致；后者会把惰性依赖
        // 排到用户依赖之前，虽然内容相同但会改变 `jar` 的条目顺序（产物字节随之变化）。
        taboo.withDependencies(new TabooLibTabooDependenciesAction(project.provider {
            taboolibModuleDependencies(project, tabooExt, api, true)
        }))

        project.pluginManager.withPlugin('java') {
            // 继承 "taboo", "include" 配置
            project.configurations.implementation.extendsFrom(taboo, include)

            // 自动引入 com.mojang:datafixerupper:4.0.26
            // 常量依赖不含扩展读取，保持原来的配置期注入即可。
            project.dependencies.add('compileOnly', 'com.mojang:datafixerupper:4.0.26')

            // 自动引入协程：compileOnly 与 testImplementation 各一份
            project.configurations.compileOnly.dependencies.addAllLater(project.provider {
                coroutinesDependency(project, tabooExt)
            })
            project.configurations.testImplementation.dependencies.addAllLater(project.provider {
                coroutinesDependency(project, tabooExt)
            })

            // 自动引入仅编译期可见的 TabooLib 模块
            project.configurations.compileOnly.dependencies.addAllLater(project.provider {
                taboolibModuleDependencies(project, tabooExt, api, false)
            })
            project.configurations.testImplementation.dependencies.addAllLater(project.provider {
                taboolibModuleDependencies(project, tabooExt, api, false)
            })
        }
    }

    /** 协程依赖：版本为 null（用户显式关闭）时返回空表，等价于「不注入」。 */
    private static List coroutinesDependency(Project project, TabooLibExtension tabooExt) {
        String coroutines = tabooExt.version.coroutines
        if (coroutines == null) {
            return []
        }
        return [project.dependencies.create('org.jetbrains.kotlinx:kotlinx-coroutines-core:' + coroutines)]
    }

    /**
     * 计算需要注入的 TabooLib 模块依赖。
     *
     * 只在依赖集合被观察时调用，因此读到的 `taboolib { }` 配置一定已经完成。
     *
     * @param forTaboo `true` 取「内联进产物」的那一组，`false` 取「仅编译期」的那一组
     */
    private static List taboolibModuleDependencies(Project project, TabooLibExtension tabooExt,
                                                   boolean api, boolean forTaboo) {
        String version = tabooExt.version.taboolib
        if (version == null) {
            return []
        }
        return tabooExt.env.modules.findAll { String module ->
            boolean inline = api || (isCoreModule(module) && !tabooExt.subproject)
            return inline == forTaboo
        }.collect { String module ->
            project.dependencies.create("io.izzel.taboolib:${module}:${version}")
        }
    }

    /**
     * jar 任务接线与重定位挂载。
     *
     * `taboolibMainTask` 与其 `dependsOn(jar)` 保持与历史一致的形态，
     * 重定位的输入输出与末尾动作则在配置期一次性挂好 —— 是否真正生效由
     * [TabooLibRelocation] 里的 `subproject` / `enabled` / `excluded` 在取值时决定。
     * 这样既不需要延后配置，也不需要在 `jar.configure` 里读扩展。
     */
    private static void wireJarAndRelocation(Project project, TabooLibExtension tabooExt, taboo, boolean api) {
        // 配置对象由插件提前建好：`jar` 需要它在配置期挂输入/动作，而那个时点
        // `taboolibMainTask` 可能不该被实例化（subproject 模块）。两者共享同一实例，
        // 因此 `-x` / `enabled = false` / 属性读写双向可见，语义与原先完全一致。
        TabooLibRelocation relocationConfiguration = new TabooLibRelocation()

        def tabooTask = project.tasks.register(MAIN_TASK_NAME, TabooLibMainTask) { TabooLibMainTask task ->
            task.group = "taboolib"
            task.adoptRelocationConfiguration(relocationConfiguration)
        }

        project.pluginManager.withPlugin('java') {
            def jarTaskProvider = project.tasks.named('jar', Jar)

            // 兼容入口：直接按名调用 taboolibMainTask 时也要先产出 jar。
            tabooTask.configure { TabooLibMainTask task ->
                task.dependsOn(jarTaskProvider)
            }

            jarTaskProvider.configure { Jar task ->
                // 内联 taboo 依赖：用 Provider 包装 taboo configuration 的解析，推迟到 task 执行阶段。
                // 之前是 task.from(taboo.collect { ... })——Groovy collect 会立即调用
                // Configuration.iterator() 触发 cross-project resolve，强制提前 evaluate
                // 所有 taboo(project(":...")) 引用的上游子项目。Gradle 9 严格的
                // DefaultMutationGuard 会因此拒绝部分子项目（如 PublishingPlugin
                // 想注册 afterEvaluate 监听器，但目标 project 已配置完成）。
                task.from(project.provider {
                    taboo.collect { // 在这里打包 "taboo" 依赖
                        // 不要用 File.isDirectory() 判断：那是一次文件系统探测，会让每个依赖产物的
                        // 路径（各子项目 build/libs/*.jsr）登记为 Gradle 配置缓存输入 —— 任一模块
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
                // 确定性产物设置与历史一致，保证既有产物字节不变。
                task.preserveFileTimestamps = false
                task.reproducibleFileOrder = true
                if (api) {
                    task.getArchiveClassifier().set("api")
                }

                TabooLibRelocationSource source = relocationSource(project, tabooExt, task, api)
                // 先按当前值填一次，让 `subproject` 等判定在配置期就能反映脚本已设置的部分；
                // 若脚本稍后才设置，取值点的 [TabooLibRelocationSource.applyTo] 会再次覆盖。
                source.applyTo(relocationConfiguration)

                // 排除选择器允许任务名缩写（如 `-x taboolibM`）。该判定只读 startParameter
                // 与任务名集合，属于配置期常量，结果作为可缓存的布尔输入进入重定位配置。
                relocationConfiguration.excluded = TabooLibTaskExclusions.isExcluded(project, MAIN_TASK_NAME)

                File archiveFile = task.archiveFile.get().asFile
                task.inputs.property('taboolib', project.providers.provider(new TabooLibRelocationInputs(relocationConfiguration, source)))
                task.inputs.files(project.providers.provider(new TabooLibRelocationFiles(relocationConfiguration, source, archiveFile, true)))
                        .withPropertyName('taboolibExternalInput').withPathSensitivity(PathSensitivity.NAME_ONLY)
                task.outputs.files(project.providers.provider(new TabooLibRelocationFiles(relocationConfiguration, source, archiveFile, false)))
                        .withPropertyName('taboolibAdditionalOutput')
                task.outputs.cacheIf('TabooLib 重定位与元数据已声明为 jar 输入') { true }

                // jar 是最终产物的唯一拥有者：重定位放在 jar 自身末尾，Gradle 保存的是
                // 已重定位产物的快照；兼容任务保留入口和配置属性，但不再二次改写输出。
                //
                // finalizer 用 Provider 表达条件：空列表 = 不加这条边。Provider 在任务图构建期求值，
                // 那时 `taboolib { subproject }` 必然已定，因此 subproject 模块**不会**被拉进
                // finalizer 边（`taboolibMainTask` 也不会因此被实例化）。
                def mainTaskRef = tabooTask
                task.finalizedBy(project.provider {
                    tabooExt.subproject ? [] : [mainTaskRef.get()]
                })
                task.doLast(new TabooLibRelocateAction(relocationConfiguration, source))
            }
        }
    }

    /**
     * 构造延迟填充来源。
     *
     * 这里**只**创建 `Provider`，不读取任何 `taboolib { }` 的值，
     * 因此可以在 `jar.configure` 里安全调用（即使 `jar` 被脚本提前实例化）。
     */
    private static TabooLibRelocationSource relocationSource(Project project, TabooLibExtension tabooExt,
                                                             Jar jarTask, boolean api) {
        def versions = tabooExt.version
        def environment = tabooExt.env

        return new TabooLibRelocationSource(
                api,
                project.provider { versions.taboolib },
                project.provider { versions.coroutines },
                project.provider { tabooExt.classifier },
                project.provider { tabooExt.subproject },
                project.provider { versions.skipVersionFile },
                project.provider { versions.skipPlatformFile },
                project.provider { versions.skipKotlin },
                project.provider { versions.skipKotlinRelocate },
                project.provider { versions.skipTabooLibRelocate },
                // `DeleteCode` 是命令行属性，配置期常量。
                project.provider { project.hasProperty("DeleteCode") },
                project.provider { project.name },
                project.provider { project.group.toString() },
                project.provider { project.version.toString() },
                project.provider { kotlinPluginVersion(project) },
                project.provider { tabooExt.rootPackage },
                project.provider { new LinkedHashMap<String, String>(tabooExt.relocation) },
                project.provider { new ArrayList<String>(tabooExt.exclude) },
                project.provider { new LinkedHashSet<String>(environment.modules) },
                project.provider { tabooExt.des },
                project.provider { environment.debug },
                project.provider { environment.forceDownloadInDev },
                project.provider { environment.enableLegacyDependencyResolver },
                project.provider { environment.enableIsolatedClassloader },
                project.provider { environment.disableOnSkippedVersion },
                project.provider { environment.disableOnUnsupportedVersion },
                project.provider { environment.disableWhenPrimitiveLoaderError },
                project.provider { environment.repoCentral },
                project.provider { environment.repoTabooLib },
                project.provider { environment.fileLibs },
                project.provider { environment.fileAssets },
                project.provider { jarTask.archiveFile.get().asFile })
    }

    /**
     * 读取 Kotlin Gradle 插件版本。
     *
     * 对未应用 Kotlin 插件的工程（纯 Java 插件工程）取不到版本，返回 null；
     * 此时 `skipKotlinRelocate` 通常为 true，不会用到该值。
     */
    private static String kotlinPluginVersion(Project project) {
        try {
            return KotlinPluginWrapperKt.getKotlinPluginVersion(project)
        } catch (Throwable ignored) {
            return null
        }
    }

    /**
     * 是否为必要模块
     */
    static def isCoreModule(String module) {
        return module == "common" || module == "platform-application" || Platforms.values().any { p -> p.module == module }
    }
}
