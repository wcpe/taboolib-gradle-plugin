//file:noinspection unused
package io.izzel.taboolib.gradle

/**
 * Fabric 平台配置。
 * <p>
 * 在 {@code taboolib { fabric { ... } }} 中填写后即启用「Fabric 模式」：插件会自动接管 Loom 平台依赖
 * （minecraft / yarn / fabric-loader / fabric-api）、接入 taboolib-fabric 平台库（modImplementation + include 单 jar），
 * 并生成 {@code fabric.mod.json} 与 {@code META-INF/taboolib/*.properties}，无需用户手写元数据生成脚本。
 * <p>
 * 与 Bukkit 等平台的区别：Fabric 必须经 Loom 的 remapJar 出包，故插件不对其做 ASM 重定位/重打包，
 * 而是把元数据写进资源目录、交由 Loom 打包。使用前需在 {@code plugins {}} 中先应用 {@code fabric-loom}。
 */
class Fabric {

    /** 目标 Minecraft 版本，如 "1.20.1"（必填） */
    String mcVersion

    /** Yarn 映射，如 "1.20.1+build.10"（必填） */
    String yarnMappings

    /** Fabric Loader 版本，如 "0.16.5"（必填） */
    String loaderVersion

    /** Fabric API 版本，如 "0.92.2+1.20.1"（必填） */
    String fabricApiVersion

    /** taboolib-fabric 平台库版本；为空时默认取 "${version.taboolib}+${mcVersion}" */
    String platformVersion = null

    /** fabric.mod.json 的 environment：`*`（默认，单人+联机皆加载）/ `client` / `server` */
    String environment = "*"

    /** mod id；为空时由项目名小写化推导（仅保留 a-z0-9_-） */
    String modId = null

    /** 客户端入口点（ClientModInitializer 实现类，可选、可多个；写进 fabric.mod.json 的 entrypoints.client） */
    List<String> clientEntrypoints = []

    /** 追加客户端入口点 */
    def clientEntrypoint(String... names) {
        clientEntrypoints.addAll(names as List)
    }
}
