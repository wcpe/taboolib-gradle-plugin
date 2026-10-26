package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.Description
import org.gradle.api.provider.Provider

/**
 * [TabooLibRelocation] 的延迟填充来源。
 *
 * 存在的理由：插件不能再用 `afterEvaluate` 等用户脚本执行完再取值，而
 * `jar` 任务有可能在用户脚本里被提前实例化（`tasks.jar { manifest { ... } }` 之类），
 * 此时 `taboolib { }` 还没执行，直接快照到的是默认值 —— 表现为产物里没有 plugin.yml、
 * 类也没被重定位（静默失效，比报错更糟）。
 *
 * 所以取值点被推迟到「真正需要的那一刻」：
 * - [TabooLibRelocationInputs] 在任务输入快照时读；
 * - [TabooLibRelocationFiles] 在文件集合求值时读；
 * - [TabooLibRelocateAction] 在任务动作执行时读。
 *
 * 每个字段都用一个 [Provider] 承载，值在被 `get()` 时才求值，因此不管
 * `jar` 何时被实例化，读到的都是用户脚本执行后的最终配置。
 *
 * 本类只持有可序列化的 `Provider`，不持有 `Project` / `Task`，可安全进入配置缓存。
 */
class TabooLibRelocationSource implements Serializable {

    /** 是否为 api 构建（由命令行请求的任务名决定，配置期常量） */
    private final boolean api

    private final Provider<String> taboolibVersion
    private final Provider<String> coroutinesVersion
    private final Provider<String> classifier
    private final Provider<Boolean> subproject
    private final Provider<Boolean> skipVersionFile
    private final Provider<Boolean> skipPlatformFile
    private final Provider<Boolean> skipKotlin
    private final Provider<Boolean> skipKotlinRelocate
    private final Provider<Boolean> skipTabooLibRelocate
    private final Provider<Boolean> deleteCode
    private final Provider<String> projectName
    private final Provider<String> projectGroup
    private final Provider<String> projectVersion
    private final Provider<String> kotlinVersion
    private final Provider<String> rootPackage
    private final Provider<File> inJar

    private final Provider<Map<String, String>> userRelocations
    private final Provider<List<String>> exclude
    private final Provider<Set<String>> modules
    private final Provider<Description> pluginDescription

    private final Provider<Boolean> envDebug
    private final Provider<Boolean> forceDownloadInDev
    private final Provider<Boolean> enableLegacyDependencyResolver
    private final Provider<Boolean> enableIsolatedClassloader
    private final Provider<Boolean> disableOnSkippedVersion
    private final Provider<Boolean> disableOnUnsupportedVersion
    private final Provider<Boolean> disableWhenPrimitiveLoaderError
    private final Provider<String> envRepoCentral
    private final Provider<String> envRepoTabooLib
    private final Provider<String> envFileLibs
    private final Provider<String> envFileAssets

    TabooLibRelocationSource(boolean api,
                            Provider<String> taboolibVersion,
                            Provider<String> coroutinesVersion,
                            Provider<String> classifier,
                            Provider<Boolean> subproject,
                            Provider<Boolean> skipVersionFile,
                            Provider<Boolean> skipPlatformFile,
                            Provider<Boolean> skipKotlin,
                            Provider<Boolean> skipKotlinRelocate,
                            Provider<Boolean> skipTabooLibRelocate,
                            Provider<Boolean> deleteCode,
                            Provider<String> projectName,
                            Provider<String> projectGroup,
                            Provider<String> projectVersion,
                            Provider<String> kotlinVersion,
                            Provider<String> rootPackage,
                            Provider<Map<String, String>> userRelocations,
                            Provider<List<String>> exclude,
                            Provider<Set<String>> modules,
                            Provider<Description> pluginDescription,
                            Provider<Boolean> envDebug,
                            Provider<Boolean> forceDownloadInDev,
                            Provider<Boolean> enableLegacyDependencyResolver,
                            Provider<Boolean> enableIsolatedClassloader,
                            Provider<Boolean> disableOnSkippedVersion,
                            Provider<Boolean> disableOnUnsupportedVersion,
                            Provider<Boolean> disableWhenPrimitiveLoaderError,
                            Provider<String> envRepoCentral,
                            Provider<String> envRepoTabooLib,
                            Provider<String> envFileLibs,
                            Provider<String> envFileAssets,
                            Provider<File> inJar) {
        this.api = api
        this.taboolibVersion = taboolibVersion
        this.coroutinesVersion = coroutinesVersion
        this.classifier = classifier
        this.subproject = subproject
        this.skipVersionFile = skipVersionFile
        this.skipPlatformFile = skipPlatformFile
        this.skipKotlin = skipKotlin
        this.skipKotlinRelocate = skipKotlinRelocate
        this.skipTabooLibRelocate = skipTabooLibRelocate
        this.deleteCode = deleteCode
        this.projectName = projectName
        this.projectGroup = projectGroup
        this.projectVersion = projectVersion
        this.kotlinVersion = kotlinVersion
        this.rootPackage = rootPackage
        this.inJar = inJar
        this.userRelocations = userRelocations
        this.exclude = exclude
        this.modules = modules
        this.pluginDescription = pluginDescription
        this.envDebug = envDebug
        this.forceDownloadInDev = forceDownloadInDev
        this.enableLegacyDependencyResolver = enableLegacyDependencyResolver
        this.enableIsolatedClassloader = enableIsolatedClassloader
        this.disableOnSkippedVersion = disableOnSkippedVersion
        this.disableOnUnsupportedVersion = disableOnUnsupportedVersion
        this.disableWhenPrimitiveLoaderError = disableWhenPrimitiveLoaderError
        this.envRepoCentral = envRepoCentral
        this.envRepoTabooLib = envRepoTabooLib
        this.envFileLibs = envFileLibs
        this.envFileAssets = envFileAssets
    }

    /**
     * 把当前配置写进目标对象。
     *
     * 只在目标需要取值时调用，因此必须可重复执行（不累积状态）。
     * 刻意不碰 `enabled` 与 `excluded`：前者由 `Task.setEnabled` 维护，
     * 后者由排除选择器在配置期一次算定，两者都不属于扩展内容。
     *
     * `inJar` 只在目标尚未显式指定时填充，保留历史上「下游可提前覆盖」的语义。
     */
    void applyTo(TabooLibRelocation target) {
        String version = taboolibVersion.getOrNull()
        String coroutines = coroutinesVersion.getOrNull()
        boolean skipKotlinRelocateValue = skipKotlinRelocate.get()
        boolean skipTabooLibRelocateValue = skipTabooLibRelocate.get()
        // 归一化后的 Kotlin 版本号用于重定位包名（去掉分隔符）
        String rawKotlinVersion = kotlinVersion.getOrNull()
        String compactKotlinVersion = rawKotlinVersion == null ? null : rawKotlinVersion.replaceAll("[._-]", "")

        target.classifier = classifier.getOrNull()
        target.api = api
        // 保留下游提前显式指定的 inJar
        target.inJar = target.inJar ?: inJar.get()
        target.subproject = subproject.get()
        target.skipVersionFile = skipVersionFile.get()
        target.skipPlatformFile = skipPlatformFile.get()
        target.skipKotlin = skipKotlin.get()
        target.skipKotlinRelocate = skipKotlinRelocateValue
        target.skipTabooLibRelocate = skipTabooLibRelocateValue
        target.deleteCode = deleteCode.get()
        target.projectName = projectName.get()
        target.projectGroup = projectGroup.get()
        target.projectVersion = projectVersion.get()
        target.kotlinVersion = rawKotlinVersion
        target.exclude = exclude.get()
        target.modules = modules.get()
        target.pluginDescription = pluginDescription.get()

        target.envDebug = envDebug.get()
        target.forceDownloadInDev = forceDownloadInDev.get()
        target.enableLegacyDependencyResolver = enableLegacyDependencyResolver.get()
        target.enableIsolatedClassloader = enableIsolatedClassloader.get()
        target.disableOnSkippedVersion = disableOnSkippedVersion.get()
        target.disableOnUnsupportedVersion = disableOnUnsupportedVersion.get()
        target.disableWhenPrimitiveLoaderError = disableWhenPrimitiveLoaderError.get()
        target.envRepoCentral = envRepoCentral.get()
        target.envRepoTabooLib = envRepoTabooLib.get()
        target.envFileLibs = envFileLibs.get()
        target.envFileAssets = envFileAssets.get()

        target.coroutinesVersion = coroutines
        target.taboolibVersion = version

        // 重定向规则顺序必须与历史一致：先用用户声明的规则，再追加 TabooLib / Kotlin 规则。
        // 每次都重建 Map（而不是在原 Map 上追加），保证反复取用时结果不变。
        Map<String, String> relocations = new LinkedHashMap<>(userRelocations.get())
        if (!skipTabooLibRelocateValue) {
            String root = rootPackage.getOrNull() ?: projectGroup.get()
            relocations['taboolib'] = root + '.taboolib'
        }
        if (!skipKotlinRelocateValue && compactKotlinVersion != null) {
            relocations['kotlin.'] = 'kotlin' + compactKotlinVersion + '.'
            if (coroutines != null) {
                relocations['kotlinx.coroutines.'] = 'kotlin' + compactKotlinVersion +
                        'x.coroutines' + coroutines.replaceAll("[._-]", "") + '.'
            }
        }
        target.relocations = relocations
    }
}
