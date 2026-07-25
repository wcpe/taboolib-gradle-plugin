import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 7 位短哈希：与 taboolib/reflex fork 命名一致，发布版本号为 <上游版本>-<短哈希>
val gitShortHash: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootDir).start()
        .inputStream.bufferedReader().use { it.readText() }.trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "unknown"

plugins {
    id("groovy")
    `maven-publish`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.0.0"
    kotlin("jvm") version "1.9.24" // Keep this for compatibility
}

group = "io.izzel.taboolib"
// version 从 gradle.properties 读取上游版本号（2.0.37），拼接短哈希作为发布版本号
// 直接设置 project.version，确保 java-gradle-plugin 自动生成的 plugin marker publication 也用拼接后的版本号
version = "${project.version}-$gitShortHash"

configurations {
    create("embed") {
        implementation.get().extendsFrom(this)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(localGroovy())
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    "embed"("org.ow2.asm:asm:9.7.1")
    "embed"("org.ow2.asm:asm-commons:9.7.1")
    "embed"("com.google.code.gson:gson:2.9.0")
    "embed"(kotlin("stdlib"))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.getByName("embed").map { if (it.isDirectory) it else zipTree(it) })
}

gradlePlugin {
    website.set("https://github.com/TabooLib/taboolib-gradle-plugin")
    vcsUrl.set("https://github.com/TabooLib/taboolib-gradle-plugin")

    plugins {
        create("taboolib") {
            id = "io.izzel.taboolib"
            displayName = "TabooLib Gradle Plugin"
            description = "TabooLib Gradle Plugin"
            implementationClass = "io.izzel.taboolib.gradle.TabooLibPlugin"
            tags.set(listOf("taboolib", "bukkit", "minecraft"))
        }
    }
}

publishing {
    repositories {
        maven {
            credentials {
                // 属性名对齐 taboolib fork：wcpeUsername / wcpePassword（发布到 io/izzel/taboolib/ 路径）
                username = project.findProperty("wcpeUsername").toString()
                password = project.findProperty("wcpePassword").toString()
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
            // 与 taboolib/reflex fork 一致，统一发布到 maven-tabooproject-release
            url = uri("https://maven.wcpe.top/repository/maven-tabooproject-release/")
        }
        mavenLocal()
    }
    publications {
        create<MavenPublication>("gradle-plugins") {
            groupId = "io.izzel.taboolib"
            artifactId = "io.izzel.taboolib.gradle.plugin"
            // 版本号已在顶部拼接为 <上游版本>-<短哈希>，此处直接继承 project.version
            from(components["java"])
            println("> Apply \"$groupId:$artifactId:$version\"")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
