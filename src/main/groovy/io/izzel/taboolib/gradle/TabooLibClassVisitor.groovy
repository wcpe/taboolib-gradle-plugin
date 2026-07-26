package io.izzel.taboolib.gradle

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class TabooLibClassVisitor extends ClassVisitor {

    String name

    // 配置缓存兼容：不再持有 Project / TabooLibExtension，仅保留所需的标量值
    boolean deleteCode

    boolean skipTabooLibRelocate

    Map<String, String> relocations

    String projectName

    String projectVersion

    String projectGroup

    boolean api

    List<String> pluginAnnotations = [
            "Lorg/spongepowered/api/plugin/Plugin;",
            "Lorg/spongepowered/plugin/jvm/Plugin;",
            "Lcom/velocitypowered/api/plugin/Plugin;"
    ]

    TabooLibClassVisitor(ClassVisitor classVisitor, boolean deleteCode, boolean skipTabooLibRelocate, Map<String, String> relocations, String projectName, String projectVersion, String projectGroup, boolean api) {
        super(Opcodes.ASM9, classVisitor);
        this.deleteCode = deleteCode
        this.skipTabooLibRelocate = skipTabooLibRelocate
        this.relocations = relocations
        this.projectName = projectName
        this.projectVersion = projectVersion
        this.projectGroup = projectGroup
        this.api = api
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (deleteCode) return new EmptyMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions))
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    @Override
    AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // 插件注解
        if (descriptor in pluginAnnotations) {
            return new PluginAnnotationVisitor(super.visitAnnotation(descriptor, visible), projectName, projectVersion)
        }
        // Metadata
        if (!skipTabooLibRelocate && descriptor == "Lkotlin/Metadata;") {
            return new KotlinMetaAnnotationVisitor(super.visitAnnotation(descriptor, visible), projectGroup, relocations)
        }
        // 其他
        return super.visitAnnotation(descriptor, visible)
    }

    @Override
    void visitEnd() {
        super.visitEnd()
    }
}
