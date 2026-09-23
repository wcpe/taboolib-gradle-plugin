package io.izzel.taboolib.gradle.description

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import java.nio.charset.StandardCharsets

abstract class Builder {

    // 配置缓存兼容：不再传入 Project，仅传入所需的标量值
    abstract byte[] build(Description description, String projectName, String projectGroup, String projectVersion, boolean skipTabooLibRelocate)

    static List<String> startBukkitFile() {
        def str = []
        str += ''
        str += ''
        str += '#         Powered by TabooLib 6.2         #'
        str += ''
        str += ''
        return str
    }

    static def writeLine(body) {
        // body.add("")
    }

    static boolean write(List<String> body, data, key) {
        if (data != null) {
            body.add("$key: $data")
            return true
        }
        return false
    }

    static def write(JsonObject body, data, key) {
        if (data != null) {
            body.addProperty("$key", "$data")
        }
    }

    static boolean writeList(List<String> body, data, key) {
        if (data instanceof List<String>) {
            if (data.size() > 0) {
                body.add("$key:")
                for (i in data) {
                    body.add("  - '${i}'")
                }
                return true
            }
        } else if (data != null) {
            body.add("$key:")
            body.add("  - '${data}'")
            return true
        }
        return false
    }

    static def writeList(JsonObject body, data, key) {
        def arr = new JsonArray()
        if (data instanceof List<String>) {
            if (data.size() > 0) {
                data.each { arr.add(it) }
                body.add(key, arr)
            }
        } else if (data != null) {
            arr.add(data.toString())
            body.add(key, arr)
        }
    }

    static byte[] bytes(List<String> body) {
        return body.join('\n').getBytes(StandardCharsets.UTF_8)
    }

    static byte[] bytes(JsonElement body) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(body).getBytes(StandardCharsets.UTF_8)
    }
}