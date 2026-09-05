# TabooLib Gradle Plugin (Fix)

此分支为 Gradle 9 兼容性修复版本，发布在 WCPE Maven 仓库。

## 构件仓库

本 fork 的构件发布于自有 Maven 仓库 `repo.wcpe.top`，坐标组为 `io.izzel.taboolib`，版本号遵循 `2.0.38-wcpe.N`（上游版本 + wcpe 修订号，最新版本见 [Releases](https://github.com/wcpe/taboolib-gradle-plugin/releases/latest)）。

## 引用方式

在 `settings.gradle.kts` 中添加插件仓库：

```kotlin
pluginManagement {
    repositories {
        maven("https://repo.wcpe.top/repository/maven-releases/")
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
