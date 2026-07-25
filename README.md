# TabooLib Gradle Plugin (Fix)

此分支为 Gradle 9 兼容性修复版本，发布在 WCPE Maven 仓库。

## 构件仓库

本 fork 的构件发布于自有 Maven 仓库 `maven.wcpe.top`，坐标组为 `io.izzel.taboolib`，版本号遵循 `2.0.37-<短哈希>`（每个提交一版，最新版本见 [Releases](https://github.com/wcpe/taboolib-gradle-plugin/releases/latest)）。

## 引用方式

在 `settings.gradle.kts` 中添加插件仓库：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.wcpe.top/repository/maven-tabooproject-release/")
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

- 版本号格式：`<上游版本>-<7 位提交短哈希>`，例如 `2.0.37-813aaaf`
- 每次 push 到 `main` 分支会自动发布新版本并创建 GitHub Release
- push 到 `dev` 分支仅测试发布流程，不创建 Release
- 仅含文档变更的提交（`docs:` 类型或仅改动 `*.md` 文件）不触发发布
