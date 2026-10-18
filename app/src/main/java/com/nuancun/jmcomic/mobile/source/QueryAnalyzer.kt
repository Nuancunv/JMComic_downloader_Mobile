package com.nuancun.jmcomic.mobile.source

import java.text.Normalizer
import java.util.Locale

data class SearchQuery(
    val text: String,
    val key: String,
    val foldedKey: String,
    val compactKey: String,
    val foldedCompact: String,
    val words: List<String>,
    val id: String?,
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/** The same analysis is used before retrieval and when matching returned metadata. */
object QueryAnalyzer {
    const val MAX_QUERY_LENGTH = 256
    private val whitespace = Regex("\\s+")
    private val separators = Regex("[^\\p{L}\\p{N}%_]+")
    private val numericId = Regex("(?i)^(?:JM)?([0-9]{1,12})$")
    private val linkedId = Regex("(?i)/(?:album|photo)/([0-9]{1,12})(?:[/?.#]|$)")

    fun analyze(input: String): SearchQuery {
        val text = Normalizer.normalize(input.take(MAX_QUERY_LENGTH), Normalizer.Form.NFKC)
            .trim().replace(whitespace, " ")
        val id = numericId.matchEntire(text)?.groupValues?.get(1)
            ?: linkedId.find(text)?.groupValues?.get(1)
        val key = normalize(id ?: text)
        val compact = compact(key)
        return SearchQuery(
            text, key, ChineseScript.simplify(key), compact, ChineseScript.simplify(compact),
            key.split(' ').filter(String::isNotBlank).distinct(), id,
        )
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(separators, " ").trim().replace(whitespace, " ")
        .ifBlank { value.trim().lowercase(Locale.ROOT) }

    fun compact(value: String): String = if (value.any(::isHan)) value.replace(" ", "") else value

    fun remoteQueries(query: SearchQuery): List<String> {
        if (query.isEmpty) return emptyList()
        query.id?.let { return listOf(it) }
        return listOf(query.text, query.key, ChineseScript.simplify(query.key),
            ChineseScript.traditionalize(query.key), query.compactKey,
            ChineseScript.simplify(query.compactKey), ChineseScript.traditionalize(query.compactKey))
            .filter(String::isNotBlank).distinct().take(3)
    }

    private fun isHan(char: Char): Boolean = Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
}

/** Character dictionaries only: this is not OpenCC's phrase/context conversion engine. */
internal object ChineseScript {
    private val simplified by lazy { dictionary("TSCharacters.txt") }
    private val traditional by lazy { dictionary("STCharacters.txt") }

    fun simplify(value: String): String = convert(value, simplified)
    fun traditionalize(value: String): String = convert(value, traditional)

    private fun convert(value: String, table: Map<Int, String>): String = buildString {
        var index = 0
        while (index < value.length) {
            val point = value.codePointAt(index)
            append(table[point] ?: String(Character.toChars(point)))
            index += Character.charCount(point)
        }
    }

    private fun dictionary(name: String): Map<Int, String> {
        val stream = checkNotNull(ChineseScript::class.java.getResourceAsStream("/search/$name")) {
            "缺少搜索繁简字典"
        }
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            buildMap {
                lines.filterNot { it.startsWith('#') || it.isBlank() }.forEach { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2 && parts[0].codePointCount(0, parts[0].length) == 1) {
                        put(parts[0].codePointAt(0), parts[1].substringBefore(' '))
                    }
                }
            }
        }
    }
}
