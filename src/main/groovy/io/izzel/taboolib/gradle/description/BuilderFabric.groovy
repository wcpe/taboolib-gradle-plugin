package io.izzel.taboolib.gradle.description

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.izzel.taboolib.gradle.Fabric
import org.gradle.api.Project

/**
 * 生成 Fabric 的 {@code fabric.mod.json}。
 * <p>
 * 不经 {@link Platforms} 的后置重打包流程（那与 Loom remapJar 冲突），而是由插件的 Fabric 分支把本结果
 * 写进资源目录、交由 Loom 打包。入口固定为 taboolib 的 {@code FabricPlugin}（main），客户端入口可选。
 */
class BuilderFabric {

    static byte[] build(Description description, Project project, Fabric fabric) {
        def root = new JsonObject()
        root.addProperty('schemaVersion', 1)
        // id：优先 fabric.modId；否则由名称/项目名小写化（仅保留 fabric 允许的字符）
        def rawId = fabric.modId ?: (description.name ?: project.name)
        root.addProperty('id', rawId.toString().toLowerCase().replaceAll('[^a-z0-9_-]', '_'))
        root.addProperty('version', project.version.toString())
        root.addProperty('name', (description.name ?: project.name).toString())
        root.addProperty('environment', fabric.environment)

        // 入口点：main 固定为 taboolib 的 Fabric 入口；client 由用户提供（可选）
        def entrypoints = new JsonObject()
        def main = new JsonArray()
        main.add('taboolib.platform.FabricPlugin')
        entrypoints.add('main', main)
        if (fabric.clientEntrypoints != null && !fabric.clientEntrypoints.isEmpty()) {
            def client = new JsonArray()
            fabric.clientEntrypoints.each { client.add(it.toString()) }
            entrypoints.add('client', client)
        }
        root.add('entrypoints', entrypoints)

        // 依赖：固定声明对 loader / minecraft / fabric-api / taboolib-fabric 的依赖
        def depends = new JsonObject()
        depends.addProperty('fabricloader', '>=0.15.0')
        depends.addProperty('minecraft', '*')
        depends.addProperty('fabric-api', '*')
        depends.addProperty('taboolib-fabric', '*')
        root.add('depends', depends)

        // 作者（来自 description.contributors）
        if (description.con != null && !description.con.contributors.isEmpty()) {
            def authors = new JsonArray()
            description.con.contributors.each { authors.add(it.name.toString()) }
            root.add('authors', authors)
        }

        return Builder.bytes(root)
    }
}
