package io.izzel.taboolib.gradle.description

class BuilderNukkit extends Builder {

    @Override
    byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate) {
        def body = startBukkitFile()
        body += "name: ${description.name ?: projectName}"

        if (skipTabooLibRelocate) {
            body += "main: taboolib.platform.NukkitPlugin"
        } else {
            body += "main: ${projectGroup}.taboolib.platform.NukkitPlugin"
        }

        body += "version: ${projectVersion}"
        write(body, description.lin.links['homepage'], 'website')
        writeLine(body)
        // authors
        def con = description.con.contributors.collect { it.name }
        writeList(body, con, 'authors')
        writeLine(body)
        // dependency
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('nukkit') }
                .findAll { it.forceDepend() }
                .collect { it.name }, 'depend')
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('nukkit') }
                .findAll { it.optional }
                .collect { it.name }, 'softdepend')
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('nukkit') }
                .findAll { it.loadbefore }
                .collect { it.name }, 'loadbefore')
        writeLine(body)
        // custom nodes
        description.nukkitNodes.each {
            if (it.value instanceof List) {
                writeList(body, it.value, it.key)
            } else {
                write(body, it.value, it.key)
            }
        }
        return bytes(body)
    }
}
