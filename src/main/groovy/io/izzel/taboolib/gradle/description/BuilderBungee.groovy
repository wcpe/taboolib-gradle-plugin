package io.izzel.taboolib.gradle.description

class BuilderBungee extends Builder {

    @Override
    byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate) {
        def body = startBukkitFile()
        body += "name: ${description.name ?: projectName}"

        if (skipTabooLibRelocate) {
            body += "main: taboolib.platform.BungeePlugin"
        } else {
            body += "main: ${projectGroup}.taboolib.platform.BungeePlugin"
        }

        body += "version: ${projectVersion}"
        write(body, description.lin.links['homepage'], 'website')
        writeLine(body)
        // authors
        def con = description.con.contributors.collect { it.name }.join(', ')
        write(body, con, 'author')
        writeLine(body)
        // dependency
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('bungee') }
                .findAll { it.forceDepend() }
                .collect { it.name }, 'depends')
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('bungee') }
                .findAll { it.optional }
                .collect { it.name }, 'softDepends')
        writeLine(body)
        // custom nodes
        description.bungeeNodes.each {
            if (it.value instanceof List) {
                writeList(body, it.value, it.key)
            } else {
                write(body, it.value, it.key)
            }
        }
        return bytes(body)
    }
}
