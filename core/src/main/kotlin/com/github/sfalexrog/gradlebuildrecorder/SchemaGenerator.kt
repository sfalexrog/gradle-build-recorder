package com.github.sfalexrog.gradlebuildrecorder

import com.github.sfalexrog.gradlebuildrecorder.models.*
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.schema.json.encodeToString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.*
import java.io.File

/**
 * Generate each record section independently: the schema library keys definitions by serial name,
 * but our independent result hierarchies deliberately share wire names such as `success`.
 * Namespacing their definitions preserves the existing recording format without collisions.
 */
fun recordingSchema(): JsonObject {
    val definitions = linkedMapOf<String, JsonElement>()
    fun section(name: String, descriptor: SerialDescriptor): JsonObject {
        val raw = Json.parseToJsonElement(
            SerializationClassJsonSchemaGenerator.Default.generateSchema(descriptor).encodeToString()
        ).jsonObject
        fun namespace(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                if (key == "\$ref" && value.jsonPrimitive.content.startsWith("#/\$defs/")) {
                    JsonPrimitive(value.jsonPrimitive.content.replace("#/\$defs/", "#/\$defs/$name."))
                } else namespace(value)
            })
            is JsonArray -> JsonArray(element.map { namespace(it) })
            else -> element
        }
        raw["\$defs"]?.jsonObject?.forEach { (key, value) ->
            definitions["$name.$key"] = namespace(value)
        }
        return namespace(JsonObject(raw.filterKeys { it !in setOf("\$defs", "\$id", "\$schema") })).jsonObject
    }
    val invocation = section("invocation", GradleInvocation.serializer().descriptor)
    val task = section("task", TaskExecutionEvent.serializer().descriptor)
    val download = section("download", FileDownloadEvent.serializer().descriptor)
    val log = section("log", LogLineEvent.serializer().descriptor)
    fun array(item: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", item)
    }
    return buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("title", "GradleBuildRecord")
        put("type", "object")
        putJsonObject("properties") {
            put("invocation", invocation)
            put("fileDownloadEvents", array(download))
            put("taskExecutionEvents", array(task))
            put("stdoutLines", array(log))
            put("stderrLines", array(log))
        }
        put("additionalProperties", false)
        put("required", JsonArray(listOf("invocation", "fileDownloadEvents", "taskExecutionEvents", "stdoutLines", "stderrLines").map(::JsonPrimitive)))
        put("\$defs", JsonObject(definitions))
    }
}

fun main() {
    File("GradleBuildRecord.json").writeText(Json { prettyPrint = true }.encodeToString(recordingSchema()) + "\n")
}
