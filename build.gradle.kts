import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("groovy")
    `maven-publish`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.0.0"
    kotlin("jvm") version "1.9.24" // Keep this for compatibility
}

group = "io.izzel.taboolib"
// 发布版本号直接读取 gradle.properties 的 version（Debian 式：上游版本 + wcpe 修订号，如 2.0.38-wcpe.1），
// 与 tag 驱动的发布流程保持一致：打与 version 同名的 tag 即触发发布

configurations {
    create("embed") {
        implementation.get().extendsFrom(this)
    }
}

val kotlinPluginTestRuntime by configurations.creating
val functionalTestSourceSet = sourceSets.create("functionalTest")

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(localGroovy())
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    "embed"("org.ow2.asm:asm:9.9")
    "embed"("org.ow2.asm:asm-commons:9.9")
    "embed"("com.google.code.gson:gson:2.9.0")
    "embed"(kotlin("stdlib"))
    add(functionalTestSourceSet.implementationConfigurationName, gradleTestKit())
    kotlinPluginTestRuntime("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(kotlinPluginTestRuntime)
}

// 使用 Gradle 自带 TestKit 执行真实消费工程，避免为了构建回归额外引入测试框架。
val functionalTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "验证 TabooLib 下游配置缓存、任务缓存和产物稳定性"
    dependsOn(functionalTestSourceSet.classesTaskName, tasks.pluginUnderTestMetadata)
    classpath = functionalTestSourceSet.runtimeClasspath
    mainClass.set("io.izzel.taboolib.gradle.TabooLibCacheFunctionalTest")
    args(layout.projectDirectory.dir(".tmp/cache-functional-test").asFile.absolutePath)
    maxHeapSize = "512m"
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.check {
    dependsOn(functionalTest)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.getByName("embed").map { if (it.isDirectory) it else zipTree(it) })
}

gradlePlugin {
    testSourceSets(functionalTestSourceSet)
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
            val releasesRepoUrl = uri("https://maven.wcpe.top/repository/maven-releases/")
            val snapshotsRepoUrl = uri("https://maven.wcpe.top/repository/maven-snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
        mavenLocal()
    }
    // 不手动创建 publication：com.gradle.plugin-publish 已自动生成 pluginMaven publication
    // （artifactId = 项目名 taboolib-gradle-plugin）和 taboolibPluginMarkerMaven publication
    // （artifactId = io.izzel.taboolib.gradle.plugin）。手动创建会与 pluginMaven 坐标重叠，
    // 导致两者发布到同一路径时 release 仓库拒绝覆盖。
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
