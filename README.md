# TabooLib Gradle Plugin (Fix)

此分支为 Gradle 9 兼容性修复版本，发布在 WCPE Maven 仓库。

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

然后在 `build.gradle.kts` 中使用：

```kotlin
plugins {
    id("io.izzel.taboolib") version "2.0.37-fix"
}
```
