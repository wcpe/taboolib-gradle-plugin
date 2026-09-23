package io.izzel.taboolib.gradle.description

import com.google.gson.JsonArray
import com.google.gson.JsonObject

class BuilderVelocity extends Builder {

    @Override
    byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate) {
        def info = new JsonObject()
        info.addProperty('id', (description.name ?: projectName).toLowerCase())
        info.addProperty('name', description.name ?: projectName)

        if (skipTabooLibRelocate) {
            info.addProperty('main', "taboolib.platform.VelocityPlugin")
        } else {
            info.addProperty('main', "${projectGroup}.taboolib.platform.VelocityPlugin")
        }

        info.addProperty('version', projectVersion)
        // authors
        def con = description.con.contributors.collect { it.name }
        writeList(info, con, 'authors')
        // dependencies
        if (description.dep.dependencies.size() > 0) {
            def dependencies = new JsonArray()
            description.dep.dependencies.findAll { it.with == null || it.with.equalsIgnoreCase('velocity') }.each { dep ->
                def dependency = new JsonObject()
                dependency.addProperty('id', dep.name)
                dependency.addProperty('optional', dep.optional)
                dependencies.add(dependency)
            }
            info.add('dependencies', dependencies)
        }
        return bytes(info)
    }
}
