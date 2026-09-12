# TabooLib Gradle Plugin (Fix)

此分支为 Gradle 9 兼容性修复版本，发布在 WCPE Maven 仓库。

## 构件仓库

本 fork 的构件发布于自有 Maven 仓库 `maven.wcpe.top`，坐标组为 `io.izzel.taboolib`，版本号遵循 `2.0.38-wcpe.N`（上游版本 + wcpe 修订号，最新版本见 [Releases](https://github.com/wcpe/taboolib-gradle-plugin/releases/latest)）。

## 引用方式

在 `settings.gradle.kts` 中添加插件仓库：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.wcpe.top/repository/maven-releases/")
        gradlePluginPortal()
    }
}
```

然后在 `build.gradle.kts` 中使用（版本号见 [Releases](https://github.com/wcpe/taboolib-gradle-plugin/releases/latest)）：

```kotlin
plugins {
    id("io.izzel.taboolib") version "<版本号>"
}
```

## 版本说明

- 版本号格式：`<上游版本>-wcpe.N`（Debian 式），例如 `2.0.38-wcpe.1`
- 版本号定义在 `gradle.properties`，发版时手动递增 `N`
- 发布采用 tag 驱动：更新 `gradle.properties` 后打与 version 同名的 tag 并推送，即触发发布并创建 GitHub Release
- 普通提交推送到 `main` 分支只做构建验证，不再自动发布

发布前先确认目标提交在 `main` 的「构建与缓存回归」通过，再为同一提交创建并推送版本 tag。该 CI 在 Gradle 9.4.0、9.5.0 上运行 `build validatePlugins`，其中 `build` → `check` 包含 `functionalTest`。tag 发布工作流会复用同一套验证，通过后才发布 Maven，Maven 发布成功后才创建 GitHub Release。所有 GitHub Release 命令明确使用当前 fork 仓库，且仅为已有 tag 创建 Release。

本次变更见 [CHANGELOG.md](CHANGELOG.md)。正式发布后还需回读实现 JAR、POM/GMM、源码与文档 JAR 及插件 marker POM，确认远端制品完整，再让下游切换正式坐标。

## 构建缓存与回归验证

重定位和描述文件生成由 `jar` 任务在打包末尾完成。`jar.archiveFile` 仍指向 `build/libs` 中的最终产物，`taboolibMainTask` 保留为依赖 `jar` 的兼容入口，其原有配置属性仍然生效。指定 `classifier` 时额外产物也由 `jar` 跟踪；不应再通过其他任务覆写这些输出。

显式设置 `taboolibMainTask.enabled = false` 或通过 `-x taboolibMainTask` 排除任务时，仍保留原始 JAR；开关也参与缓存键。排除选择器在配置期使用当前 Gradle 的 `NameMatcher` 匹配任务名与路径缩写，执行期不查询任务图。此 fork 定位为 Gradle 9 兼容修复，本轮回归覆盖 9.4 与下游使用的 9.5，不据此新增其他 Gradle 版本的支持承诺。

所有影响产物的 TabooLib 配置（包括平台描述、重定位规则顺序和 `DeleteCode`）纳入 `jar` 输入，ZIP 条目顺序和时间戳固定。启用 `--build-cache` 后支持任务缓存；同一工程重复构建支持 `UP-TO-DATE`，`--configuration-cache-problems=fail` 用于严格验证配置缓存兼容性。

运行真实消费工程回归：

```powershell
.\gradlew.bat functionalTest validatePlugins --no-configuration-cache --max-workers=2
```

回归覆盖依赖内联、可加载字节码重定位、二次构建、删除输出后的缓存恢复、描述及依赖变更、分类产物和损坏字节码后的恢复。测试只使用 Gradle 自带 TestKit，过程证据写入 `.tmp/cache-functional-test/`。

本地验证可用独立版本坐标发布，不覆盖正式发行版本：

```powershell
.\gradlew.bat publishToMavenLocal '-Pversion=2.0.38-wcpe.3-local-test'
```

下游在插件仓库中启用 `mavenLocal()` 并选用该测试版本后，分别运行首次、重复和缓存恢复构建。
