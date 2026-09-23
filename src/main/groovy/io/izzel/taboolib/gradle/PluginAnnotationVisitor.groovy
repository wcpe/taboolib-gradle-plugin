package io.izzel.taboolib.gradle

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

class PluginAnnotationVisitor extends AnnotationVisitor {

    // 配置缓存兼容：不再持有 Project，仅保留所需的标量值
    String projectName

    String projectVersion

    PluginAnnotationVisitor(AnnotationVisitor annotationVisitor, String projectName, String projectVersion) {
        super(Opcodes.ASM9, annotationVisitor)
        this.projectName = projectName
        this.projectVersion = projectVersion
    }

    @Override
    void visit(String name, Object value) {
        if (value instanceof String) {
            super.visit(name, value
                    .replace("@plugin_id@", projectName.toLowerCase())
                    .replace("@plugin_name@", projectName)
                    .replace("@plugin_version@", projectVersion)
            )
        } else {
            super.visit(name, value)
        }
    }

    @Override
    void visitEnum(String name, String descriptor, String value) {
        super.visitEnum(name, descriptor, value)
    }

    @Override
    AnnotationVisitor visitAnnotation(String name, String descriptor) {
        return super.visitAnnotation(name, descriptor)
    }

    @Override
    AnnotationVisitor visitArray(String name) {
        return super.visitArray(name)
    }

    @Override
    void visitEnd() {
        super.visitEnd()
    }
}
