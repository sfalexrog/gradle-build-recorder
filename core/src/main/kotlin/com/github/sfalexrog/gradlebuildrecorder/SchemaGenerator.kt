package com.github.sfalexrog.gradlebuildrecorder

import com.github.sfalexrog.gradlebuildrecorder.models.GradleBuildRecord
import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.schema.json.encodeToString
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

@OptIn(InternalSerializationApi::class)
fun main(args: Array<String>) {
    val generator = SerializationClassJsonSchemaGenerator.Default
    val schemas = listOf(
        GradleBuildRecord::class,
    ).associate { serializable ->
        Pair(
            serializable.java.simpleName,
            generator.generateSchema(serializable.serializer().descriptor)
        )
    }
    schemas.forEach { (className, schema) ->
        val schemaJson = schema.encodeToString(Json { prettyPrint = true })
        File("$className.json").bufferedWriter().use { out ->
            out.write(schemaJson)
        }
    }
}