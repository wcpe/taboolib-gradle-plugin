package io.izzel.taboolib.gradle

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

class KotlinMetaAnnotationVisitor extends AnnotationVisitor {

    // 配置缓存兼容：不再持有 Project / TabooLibExtension，仅保留所需的标量值
    String projectGroup

    Map<String, String> relocations

    KotlinMetaAnnotationVisitor(AnnotationVisitor annotationVisitor, String projectGroup, Map<String, String> relocations) {
        super(Opcodes.ASM9, annotationVisitor)
        this.projectGroup = projectGroup
        this.relocations = relocations
    }

    @Override
    void visit(String name, Object value) {
        if (value instanceof String) {
            def group = projectGroup.replace('.', '/')
            def rep = value.replace("Ltaboolib", "L$group/taboolib")
            relocations.each { k, v ->
                rep = rep.replace("L${k.replace('.', '/')}", "L${v.replace('.', '/')}")
            }
            super.visit(name, rep)
        } else {
            super.visit(name, value)
        }
    }

    @Override
    AnnotationVisitor visitArray(String name) {
        return new KotlinMetaAnnotationVisitor(super.visitArray(name), projectGroup, relocations)
    }
}
