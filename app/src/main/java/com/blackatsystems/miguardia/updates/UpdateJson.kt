package com.blackatsystems.miguardia.updates

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Small bounded JSON codec for the closed API; duplicate keys are never overwritten. */
internal object UpdateJson {
    fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun parse(text: String, limit: Int = 32768): Map<String, Any?> {
        require(text.length <= limit && text.toByteArray(Charsets.UTF_8).size <= limit)
        return Parser(text).parse()
    }

    fun encode(values: Map<String, Any?>): String = values.entries.joinToString(",", "{", "}") { (key, value) ->
        quote(key) + ":" + when (value) {
            null -> "null"
            is String -> quote(value)
            is Boolean, is Long, is Int -> value.toString()
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                encode(value as Map<String, Any?>)
            }
            else -> error("Unsupported JSON type")
        }
    }

    // Matches well-formed JSON.stringify, including isolated UTF-16 surrogates.
    fun quote(value: String): String = buildString {
        append('"')
        value.forEachIndexed { index, c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ' ||
                    (c.isHighSurrogate() && value.getOrNull(index + 1)?.isLowSurrogate() != true) ||
                    (c.isLowSurrogate() && value.getOrNull(index - 1)?.isHighSurrogate() != true)
                ) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }

    private class Parser(val source: String) {
        var position = 0
        fun parse(): Map<String, Any?> {
            val result = obj(1)
            space()
            require(position == source.length)
            return result
        }
        fun space() { while (source.getOrNull(position) in listOf(' ', '\r', '\n', '\t')) position++ }
        fun take(c: Char) { space(); require(source.getOrNull(position++) == c) }
        fun obj(depth: Int): Map<String, Any?> {
            require(depth <= 3)
            take('{')
            val map = linkedMapOf<String, Any?>()
            space()
            if (source.getOrNull(position) == '}') { position++; return map }
            while (true) {
                val key = string()
                require(!map.containsKey(key) && map.size < 32)
                take(':'); space()
                map[key] = when (source.getOrNull(position)) {
                    '"' -> string()
                    '{' -> obj(depth + 1)
                    't' -> literal("true", true)
                    'f' -> literal("false", false)
                    'n' -> literal("null", null)
                    else -> number()
                }
                space()
                if (source.getOrNull(position) == '}') { position++; return map }
                take(',')
            }
        }
        fun literal(word: String, value: Any?): Any? {
            require(source.startsWith(word, position)); position += word.length; return value
        }
        fun number(): Long {
            val start = position
            if (source.getOrNull(position) == '-') position++
            while (source.getOrNull(position) in '0'..'9') position++
            val token = source.substring(start, position)
            require(Regex("-?(0|[1-9][0-9]*)").matches(token))
            return token.toLong().also { require(it in -9_007_199_254_740_991L..9_007_199_254_740_991L) }
        }
        fun string(): String {
            take('"')
            return buildString {
                while (true) {
                    val c = source.getOrNull(position++) ?: error("Incomplete JSON")
                    if (c == '"') break
                    require(c >= ' ')
                    if (c != '\\') { append(c); continue }
                    append(when (source.getOrNull(position++)) {
                        '"' -> '"'; '\\' -> '\\'; '/' -> '/'
                        'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            val hex = source.substring(position, position + 4)
                            require(hex.all { it in "0123456789abcdefABCDEF" })
                            position += 4; hex.toInt(16).toChar()
                        }
                        else -> error("Invalid escape")
                    })
                }
            }
        }
    }
}

internal fun Map<String, Any?>.exact(vararg keys: String) { require(this.keys == keys.toSet()) }
internal fun Map<String, Any?>.string(key: String): String = get(key) as? String ?: error("Invalid text")
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?> =
    get(key) as? Map<String, Any?> ?: error("Invalid object")
