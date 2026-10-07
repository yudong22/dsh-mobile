package com.clarklevis.dsh.shared.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.contentOrNull

object GatewayWireDecoder {
    /** Report schema metadata without copying message text or credentials from the wire frame. */
    @OptIn(ExperimentalSerializationApi::class)
    fun failureSummary(text: String, cause: Throwable): String {
        val root = runCatching { wireJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        val kind = (root?.get("kind") as? JsonPrimitive)?.contentOrNull.safeLabel()
        val event = ((root?.get("event") as? JsonObject)?.get("type") as? JsonPrimitive)
            ?.contentOrNull.safeLabel()
        val location = listOfNotNull(kind, event).joinToString("/").ifEmpty { "未知帧" }
        val rawPath = Regex("at path:?\\s*(\\$[A-Za-z0-9_.\\[\\]-]+)")
            .find(cause.message.orEmpty())?.groupValues?.get(1)?.safePath()
        val path = rawPath?.let { candidate ->
            val first = candidate.removePrefix("\$.").substringBefore('.').substringBefore('[')
            val eventObject = root?.get("event") as? JsonObject
            if (first !in root.orEmpty() && first in eventObject.orEmpty()) {
                "\$.event${candidate.removePrefix("\$")}"
            } else candidate
        }
        val missingFields = (cause as? MissingFieldException)?.missingFields
            ?.mapNotNull { it.safeLabel() }
            .orEmpty().ifEmpty {
                listOfNotNull(Regex("Field '([A-Za-z][A-Za-z0-9_]{0,48})' is required")
                    .find(cause.message.orEmpty())?.groupValues?.get(1))
            }
        val detail = if (missingFields.isNotEmpty()) "缺少必填字段 ${missingFields.joinToString("、")}" else {
            val expected = Regex("Expected (?:an? )?(string|int|integer|boolean|number|array|object)", RegexOption.IGNORE_CASE)
                .find(cause.message.orEmpty())?.groupValues?.get(1)?.lowercase()
                ?.let { if (it == "int") "integer" else it }
                ?: when (path?.substringAfterLast('.')) {
                    "turn", "step", "seq", "sourceEventSeq", "shadowedItemCount", "shadowedTokenCount" -> "integer"
                    "isError", "interrupted", "replay", "accepted", "applied" -> "boolean"
                    else -> null
                }
            val actual = path?.let { findJsonType(root, it) }
            when {
                expected != null && actual != null -> "类型不匹配：需要 $expected，收到 $actual"
                expected != null -> "类型不匹配：需要 $expected"
                else -> "字段格式不符合协议"
            }
        }
        return "decode-failed [$location${path?.let { " $it" } ?: ""}] ${cause::class.simpleName ?: "DecodeException"}: $detail"
    }

    private fun String?.safeLabel(): String? = this?.takeIf {
        it.length in 1..64 && it.all { char -> char.isLetterOrDigit() || char == '-' || char == '/' || char == '_' }
    }

    private fun String.safePath(): String {
        val known = setOf("event", "error", "events", "data", "items", "queues", "sessionStats",
            "tokenUsage", "contextPressure", "todos", "goal", "workspace", "presets", "groups",
            "current", "selection", "message", "source", "images", "attachment", "questions",
            "options", "sessionPermissions", "ui", "turn", "step", "text", "toolCalls", "arguments", "content",
            "sessionId", "seq", "time", "kind", "type", "projections", "values")
        return Regex("[A-Za-z_][A-Za-z0-9_]*|\\[\\d+]").findAll(this)
            .map { token -> if (token.value.startsWith("[")) token.value else if (token.value in known) token.value else "?" }
            .joinToString(".") { it }
            .replace(".[", "[")
            .let { "\$.$it" }
    }

    private fun findJsonType(root: JsonObject?, path: String): String? {
        var value: kotlinx.serialization.json.JsonElement = root ?: return null
        for (segment in Regex("[A-Za-z_][A-Za-z0-9_]*|\\[\\d+]").findAll(path).map { it.value }) {
            value = when {
                segment.startsWith("[") -> (value as? JsonArray)?.getOrNull(segment.drop(1).dropLast(1).toIntOrNull() ?: return null) ?: return null
                else -> (value as? JsonObject)?.get(segment) ?: return null
            }
        }
        return when (value) {
            is JsonObject -> "object"
            is JsonArray -> "array"
            is JsonPrimitive -> when {
                value.isString -> "string"
                value.content == "true" || value.content == "false" -> "boolean"
                value.content == "null" -> "null"
                else -> "number"
            }
        }
    }

    fun decode(text: String): GatewayFrame {
        val parsed = wireJson.parseToJsonElement(text)
        val objectValue = parsed as? JsonObject
            ?: return wireJson.decodeFromJsonElement(GatewayFrame.serializer(), parsed)
        var normalized = when {
            "kind" !in objectValue &&
                objectValue["sessionId"] is JsonPrimitive &&
                objectValue["seq"] is JsonPrimitive &&
                objectValue["event"] is JsonObject -> {
                JsonObject(objectValue + ("kind" to JsonPrimitive("event")))
            }
            "kind" !in objectValue && (
                objectValue["type"]?.let { (it as? JsonPrimitive)?.contentOrNull in setOf("question-requested", "ask_question", "question") } == true ||
                objectValue["questions"] is JsonArray
            ) -> {
                JsonObject(objectValue + ("kind" to JsonPrimitive("question-requested")))
            }
            "kind" !in objectValue && objectValue["type"] is JsonPrimitive -> {
                JsonObject(objectValue + ("kind" to objectValue.getValue("type")))
            }
            else -> objectValue
        }
        val kindStr = (normalized["kind"] as? JsonPrimitive)?.contentOrNull
        if (kindStr in setOf("ask_question", "question")) {
            normalized = JsonObject(normalized + ("kind" to JsonPrimitive("question-requested")))
        }
        if (normalized["kind"]?.let { (it as? JsonPrimitive)?.contentOrNull == "question-requested" } == true) {
            val rpcId = normalized["rpcId"] ?: normalized["rpc_id"] ?: normalized["id"] ?: normalized["callId"]
            if (rpcId != null && "rpcId" !in normalized) {
                normalized = JsonObject(normalized + ("rpcId" to rpcId))
            }
            if ("replay" !in normalized) {
                normalized = JsonObject(normalized + ("replay" to JsonPrimitive(false)))
            }
        }
        val questions = normalized["questions"] as? JsonArray
        if (questions != null) {
            normalized = JsonObject(normalized + ("questions" to JsonArray(questions.mapIndexed { index, item ->
                val qObj = item as? JsonObject ?: return@mapIndexed item
                val id = qObj["id"] ?: JsonPrimitive("q_${index + 1}")
                val questionText = qObj["question"] ?: qObj["prompt"] ?: qObj["text"] ?: qObj["header"] ?: JsonPrimitive("")
                val multiSelect = qObj["multiSelect"] ?: qObj["multi_select"] ?: qObj["is_multi_select"]
                val optionsArray = qObj["options"] as? JsonArray
                val normalizedOptions = optionsArray?.map { opt ->
                    when (opt) {
                        is JsonPrimitive -> JsonObject(mapOf("label" to opt))
                        is JsonObject -> {
                            val label = opt["label"] ?: opt["text"] ?: opt["title"] ?: opt["name"] ?: JsonPrimitive("")
                            JsonObject(opt + ("label" to label))
                        }
                        else -> opt
                    }
                }
                var updated = qObj + mapOf("id" to id, "question" to questionText)
                if (multiSelect != null && "multiSelect" !in updated) {
                    updated = updated + ("multiSelect" to multiSelect)
                }
                if (normalizedOptions != null) {
                    updated = updated + ("options" to JsonArray(normalizedOptions))
                }
                JsonObject(updated)
            })))
        }
        val presets = normalized["presets"] as? JsonArray
        if (presets != null) {
            normalized = JsonObject(normalized + ("presets" to JsonArray(presets.map { item ->
                val preset = item as? JsonObject ?: return@map item
                val broken = preset["broken"] as? JsonPrimitive
                if (broken?.isString == true) JsonObject(preset + mapOf(
                    "broken" to JsonPrimitive(broken.content.isNotBlank()),
                    "brokenReason" to broken
                )) else preset
            })))
        }
        return wireJson.decodeFromJsonElement(GatewayFrame.serializer(), normalized)
    }
}
