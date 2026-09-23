package io.izzel.taboolib.gradle.description

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * @project taboolib-gradle-plugin
 *
 * @author Score2
 * @since 2021/09/29 12:20
 */
class BuilderCloudNetV3 extends Builder {
    @Override
    byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate) {
        def info = new JsonObject()
        info.addProperty('group', projectGroup)
        info.addProperty('name', description.name ?: projectName)

        if (skipTabooLibRelocate) {
            info.addProperty('main', "taboolib.platform.CloudNetV3Plugin")
        } else {
            info.addProperty('main', "${projectGroup}.taboolib.platform.CloudNetV3Plugin")
        }

        info.addProperty('version', projectVersion)
        // authors
        def con = description.con.contributors.collect { it.name }
        if (con.size() >= 1) {
            info.addProperty('author', con.get(0).toString())
        } else {
            writeList(info, con, 'authors')
        }
        // dependencies
        def depends = new JsonArray()
        description.dep.dependencies.forEach { it ->
            def depend = new JsonObject()
            depend.addProperty('group', it.group)
            depend.addProperty('name', it.name)
            depend.addProperty('version', it.version)
            depends.add(depend)
        }
        return bytes(info)
    }
}
