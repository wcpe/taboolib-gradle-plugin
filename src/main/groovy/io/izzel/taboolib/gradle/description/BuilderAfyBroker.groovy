package io.izzel.taboolib.gradle.description

class BuilderAfyBroker extends Builder {

    @Override
    byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate) {
        def body = startBukkitFile()
        body += "name: ${description.name ?: projectName}"

        if (skipTabooLibRelocate) {
            body += "main: taboolib.platform.AfyBrokerPlugin"
        } else {
            body += "main: ${projectGroup}.taboolib.platform.AfyBrokerPlugin"
        }

        body += "version: ${projectVersion}"
        writeLine(body)
        // authors
        def con = description.con.contributors.collect { it.name }.join(', ')
        write(body, con, 'author')
        writeLine(body)
        // dependency
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('afybroker') }
                .findAll { it.forceDepend() }
                .collect { it.name }, 'depends')
        writeList(body, description.dep.dependencies
                .findAll { it.with == null || it.with.equalsIgnoreCase('afybroker') }
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
