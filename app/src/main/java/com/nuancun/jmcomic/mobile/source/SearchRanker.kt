package com.nuancun.jmcomic.mobile.source

import java.text.Normalizer
import java.util.Locale

object SearchRanker {
    fun rank(query: String, hits: List<SearchHit>): List<SearchHit> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return hits
        val tokens = tokenize(normalizedQuery)
        return hits.withIndex().sortedWith(
            compareByDescending<IndexedValue<SearchHit>> { score(normalizedQuery, tokens, it.value) }
                .thenBy { it.index },
        ).map { it.value }
    }

    internal fun score(query: String, tokens: List<String>, hit: SearchHit): Long {
        val title = normalize(hit.title)
        val author = normalize(hit.author)
        val tags = normalize(hit.tags.joinToString(" "))
        val id = normalize(hit.id)
        if (query.all(Char::isDigit) && id == query) return 9_000_000_000_000L
        if (title == query) return 8_000_000_000_000L

        var score = 0L
        val titlePosition = title.indexOf(query)
        if (titlePosition >= 0) {
            score += 7_000_000_000_000L
            score += (10_000 - titlePosition.coerceAtMost(10_000)) * 1_000_000L
            score += (10_000 - (title.length - query.length).coerceAtLeast(0).coerceAtMost(10_000)) * 10_000L
        }

        val matchedTitleTokens = tokens.count { it in title }
        if (tokens.isNotEmpty() && matchedTitleTokens == tokens.size) score += 500_000_000_000L
        score += matchedTitleTokens * 10_000_000_000L / tokens.size.coerceAtLeast(1)
        score += overlap(query, title) * 100_000L
        score += tokens.count { it in author } * 20_000L
        score += tokens.count { it in tags } * 10_000L
        return score
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun tokenize(value: String): List<String> {
        val words = value.split(' ').filter(String::isNotBlank)
        val grams = words.flatMap { word ->
            if (word.length <= 2) listOf(word) else word.windowed(2, 1)
        }
        return (words + grams).distinct()
    }

    private fun overlap(query: String, title: String): Int {
        val queryGrams = query.replace(" ", "").let { if (it.length < 2) setOf(it) else it.windowed(2).toSet() }
        if (queryGrams.isEmpty()) return 0
        val titleGrams = title.replace(" ", "").let { if (it.length < 2) setOf(it) else it.windowed(2).toSet() }
        return queryGrams.count(titleGrams::contains) * 100 / queryGrams.size
    }
}
