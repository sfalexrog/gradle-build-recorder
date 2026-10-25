package com.github.sfalexrog.gradlebuildrecorder

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemaGeneratorTest {
    @Test
    fun `success definitions retain section-specific fields`() {
        val definitions = recordingSchema().getValue("\$defs").jsonObject
        fun properties(section: String) = definitions.getValue("$section.success")
            .jsonObject.getValue("properties").jsonObject.keys
        assertEquals(setOf("type"), properties("invocation"))
        assertEquals(setOf("type"), properties("download"))
        assertTrue(properties("task").containsAll(listOf("startTime", "endTime", "isUpToDate", "isFromCache")))
    }

    @Test
    fun `generation is deterministic and all references resolve`() {
        val schema = recordingSchema()
        assertEquals(schema.toString(), recordingSchema().toString())
        val definitions = schema.getValue("\$defs").jsonObject
        fun checkReferences(value: JsonElement) {
            when (value) {
                is JsonObject -> value.forEach { (key, child) ->
                    if (key == "\$ref") assertTrue(definitions.containsKey(child.jsonPrimitive.content.removePrefix("#/\$defs/")))
                    else checkReferences(child)
                }
                is JsonArray -> value.forEach(::checkReferences)
                else -> Unit
            }
        }
        checkReferences(schema)
    }
}
