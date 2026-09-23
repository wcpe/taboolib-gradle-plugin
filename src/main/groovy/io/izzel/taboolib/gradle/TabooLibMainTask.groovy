package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.Description
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault

/** 保留原任务和配置入口；最终产物由 jar 单独拥有，避免 finalizer 再次改写 jar 输出。 */
@DisableCachingByDefault(because = "兼容生命周期任务，实际重定位及缓存由 jar 任务负责。")
class TabooLibMainTask extends DefaultTask {

    @Internal
    final TabooLibRelocation relocationConfiguration = new TabooLibRelocation()

    @Override
    void setEnabled(boolean enabled) {
        super.setEnabled(enabled)
        // 只同步普通状态，jar 执行时不持有或查询兼容任务对象。
        if (relocationConfiguration != null) {
            relocationConfiguration.enabled = enabled
        }
    }

    @Internal
    File getInJar() { relocationConfiguration.inJar }

    void setInJar(File value) { relocationConfiguration.inJar = value }

    @Internal
    Map<String, String> getRelocations() { relocationConfiguration.relocations }

    void setRelocations(Map<String, String> value) { relocationConfiguration.relocations = value }

    @Internal
    String getClassifier() { relocationConfiguration.classifier }

    void setClassifier(String value) { relocationConfiguration.classifier = value }

    @Internal
    boolean getApi() { relocationConfiguration.api }

    @Internal
    boolean isApi() { relocationConfiguration.api }

    void setApi(boolean value) { relocationConfiguration.api = value }

    @Internal
    boolean getSubproject() { relocationConfiguration.subproject }

    @Internal
    boolean isSubproject() { relocationConfiguration.subproject }

    void setSubproject(boolean value) { relocationConfiguration.subproject = value }

    @Internal
    List<String> getExclude() { relocationConfiguration.exclude }

    void setExclude(List<String> value) { relocationConfiguration.exclude = value }

    @Internal
    boolean getSkipVersionFile() { relocationConfiguration.skipVersionFile }

    @Internal
    boolean isSkipVersionFile() { relocationConfiguration.skipVersionFile }

    void setSkipVersionFile(boolean value) { relocationConfiguration.skipVersionFile = value }

    @Internal
    boolean getSkipPlatformFile() { relocationConfiguration.skipPlatformFile }

    @Internal
    boolean isSkipPlatformFile() { relocationConfiguration.skipPlatformFile }

    void setSkipPlatformFile(boolean value) { relocationConfiguration.skipPlatformFile = value }

    @Internal
    Set<String> getModules() { relocationConfiguration.modules }

    void setModules(Set<String> value) { relocationConfiguration.modules = value }

    @Internal
    Description getPluginDescription() { relocationConfiguration.pluginDescription }

    void setPluginDescription(Description value) { relocationConfiguration.pluginDescription = value }

    @Internal
    boolean getEnvDebug() { relocationConfiguration.envDebug }

    @Internal
    boolean isEnvDebug() { relocationConfiguration.envDebug }

    void setEnvDebug(boolean value) { relocationConfiguration.envDebug = value }

    @Internal
    boolean getForceDownloadInDev() { relocationConfiguration.forceDownloadInDev }

    @Internal
    boolean isForceDownloadInDev() { relocationConfiguration.forceDownloadInDev }

    void setForceDownloadInDev(boolean value) { relocationConfiguration.forceDownloadInDev = value }

    @Internal
    String getEnvRepoCentral() { relocationConfiguration.envRepoCentral }

    void setEnvRepoCentral(String value) { relocationConfiguration.envRepoCentral = value }

    @Internal
    String getEnvRepoTabooLib() { relocationConfiguration.envRepoTabooLib }

    void setEnvRepoTabooLib(String value) { relocationConfiguration.envRepoTabooLib = value }

    @Internal
    String getEnvFileLibs() { relocationConfiguration.envFileLibs }

    void setEnvFileLibs(String value) { relocationConfiguration.envFileLibs = value }

    @Internal
    String getEnvFileAssets() { relocationConfiguration.envFileAssets }

    void setEnvFileAssets(String value) { relocationConfiguration.envFileAssets = value }

    @Internal
    boolean getEnableLegacyDependencyResolver() { relocationConfiguration.enableLegacyDependencyResolver }

    @Internal
    boolean isEnableLegacyDependencyResolver() { relocationConfiguration.enableLegacyDependencyResolver }

    void setEnableLegacyDependencyResolver(boolean value) { relocationConfiguration.enableLegacyDependencyResolver = value }

    @Internal
    boolean getEnableIsolatedClassloader() { relocationConfiguration.enableIsolatedClassloader }

    @Internal
    boolean isEnableIsolatedClassloader() { relocationConfiguration.enableIsolatedClassloader }

    void setEnableIsolatedClassloader(boolean value) { relocationConfiguration.enableIsolatedClassloader = value }

    @Internal
    boolean getDisableOnSkippedVersion() { relocationConfiguration.disableOnSkippedVersion }

    @Internal
    boolean isDisableOnSkippedVersion() { relocationConfiguration.disableOnSkippedVersion }

    void setDisableOnSkippedVersion(boolean value) { relocationConfiguration.disableOnSkippedVersion = value }

    @Internal
    boolean getDisableOnUnsupportedVersion() { relocationConfiguration.disableOnUnsupportedVersion }

    @Internal
    boolean isDisableOnUnsupportedVersion() { relocationConfiguration.disableOnUnsupportedVersion }

    void setDisableOnUnsupportedVersion(boolean value) { relocationConfiguration.disableOnUnsupportedVersion = value }

    @Internal
    boolean getDisableWhenPrimitiveLoaderError() { relocationConfiguration.disableWhenPrimitiveLoaderError }

    @Internal
    boolean isDisableWhenPrimitiveLoaderError() { relocationConfiguration.disableWhenPrimitiveLoaderError }

    void setDisableWhenPrimitiveLoaderError(boolean value) { relocationConfiguration.disableWhenPrimitiveLoaderError = value }

    @Internal
    boolean getSkipKotlin() { relocationConfiguration.skipKotlin }

    @Internal
    boolean isSkipKotlin() { relocationConfiguration.skipKotlin }

    void setSkipKotlin(boolean value) { relocationConfiguration.skipKotlin = value }

    @Internal
    String getCoroutinesVersion() { relocationConfiguration.coroutinesVersion }

    void setCoroutinesVersion(String value) { relocationConfiguration.coroutinesVersion = value }

    @Internal
    String getTaboolibVersion() { relocationConfiguration.taboolibVersion }

    void setTaboolibVersion(String value) { relocationConfiguration.taboolibVersion = value }

    @Internal
    boolean getSkipKotlinRelocate() { relocationConfiguration.skipKotlinRelocate }

    @Internal
    boolean isSkipKotlinRelocate() { relocationConfiguration.skipKotlinRelocate }

    void setSkipKotlinRelocate(boolean value) { relocationConfiguration.skipKotlinRelocate = value }

    @Internal
    boolean getSkipTabooLibRelocate() { relocationConfiguration.skipTabooLibRelocate }

    @Internal
    boolean isSkipTabooLibRelocate() { relocationConfiguration.skipTabooLibRelocate }

    void setSkipTabooLibRelocate(boolean value) { relocationConfiguration.skipTabooLibRelocate = value }

    @Internal
    String getKotlinVersion() { relocationConfiguration.kotlinVersion }

    void setKotlinVersion(String value) { relocationConfiguration.kotlinVersion = value }

    @Internal
    boolean getDeleteCode() { relocationConfiguration.deleteCode }

    @Internal
    boolean isDeleteCode() { relocationConfiguration.deleteCode }

    void setDeleteCode(boolean value) { relocationConfiguration.deleteCode = value }

    @Internal
    String getProjectName() { relocationConfiguration.projectName }

    void setProjectName(String value) { relocationConfiguration.projectName = value }

    @Internal
    String getProjectGroup() { relocationConfiguration.projectGroup }

    void setProjectGroup(String value) { relocationConfiguration.projectGroup = value }

    @Internal
    String getProjectVersion() { relocationConfiguration.projectVersion }

    void setProjectVersion(String value) { relocationConfiguration.projectVersion = value }

    // 保留直接调用的兼容方法，但不再作为任务动作自动执行。
    def relocate() { relocationConfiguration.relocate(logger) }

    byte[] buildEnv() { relocationConfiguration.buildEnv() }

    byte[] buildVersion() { relocationConfiguration.buildVersion() }

    @Override
    String toString() { "TabooLibMainTask{}" }
}
