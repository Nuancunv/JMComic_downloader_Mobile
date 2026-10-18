package com.nuancun.jmcomic.mobile.source

import java.text.Normalizer
import java.util.Locale

enum class MatchKind(val label: String) {
    NONE(""), CHAPTER("匹配章节"), TAG("匹配标签"), AUTHOR("匹配作者"),
    FIELDS("匹配多个关键词"), TITLE_SIMILAR("书名近似匹配"), TITLE_WORDS("标题包含全部关键词"),
    TITLE_PHRASE("标题包含搜索词"), TITLE_EQUIVALENT("标题匹配（繁简或空格）"),
    TITLE_EXACT("标题完全匹配"), ID("作品 ID 完全匹配"),
}

data class MatchRange(val start: Int, val end: Int)

data class SearchMatch(
    val kind: MatchKind,
    val coverage: Int = 0,
    val position: Int = Int.MAX_VALUE,
    val extraLength: Int = Int.MAX_VALUE,
    val chapter: String? = null,
) {
    val reason: String get() = chapter?.let { "匹配章节：$it" } ?: kind.label
}

data class SearchDocument(
    val id: String,
    val title: String,
    val author: String,
    val tags: List<String>,
    val chapters: List<String> = emptyList(),
)

object SearchRanker {
    val matchOrder: Comparator<SearchMatch> = compareByDescending<SearchMatch> { it.kind.ordinal }
        .thenByDescending { it.coverage }.thenBy { it.position }.thenBy { it.extraLength }

    fun rank(query: String, hits: List<SearchHit>): List<SearchHit> = rank(QueryAnalyzer.analyze(query), hits)

    fun rank(query: SearchQuery, hits: List<SearchHit>): List<SearchHit> = hits.map { hit ->
        hit.copy(match = match(query, SearchDocument(hit.id, hit.title, hit.authorLabel, hit.tags)))
    }.sortedWith { a, b -> matchOrder.compare(checkNotNull(a.match), checkNotNull(b.match)) }

    fun match(query: SearchQuery, document: SearchDocument): SearchMatch {
        if (query.isEmpty) return SearchMatch(MatchKind.NONE)
        if (query.id == document.id) return SearchMatch(MatchKind.ID, 100, 0, 0)
        val title = QueryAnalyzer.normalize(document.title)
        val foldedTitle = ChineseScript.simplify(title)
        val compactTitle = QueryAnalyzer.compact(foldedTitle)
        if (title == query.key) return SearchMatch(MatchKind.TITLE_EXACT, 100, 0, 0)
        if (compactTitle == query.foldedCompact) return SearchMatch(MatchKind.TITLE_EQUIVALENT, 100, 0, 0)
        val position = foldedTitle.indexOf(query.foldedKey).takeIf { it >= 0 }
            ?: compactTitle.indexOf(query.foldedCompact).takeIf { it >= 0 }
        if (position != null) return SearchMatch(MatchKind.TITLE_PHRASE, 100, position,
            (compactTitle.length - query.foldedCompact.length).coerceAtLeast(0))
        val words = query.words.map(ChineseScript::simplify)
        if (containsWords(foldedTitle, words)) return SearchMatch(MatchKind.TITLE_WORDS, 100)
        if (query.foldedCompact.length in 4..128 && !query.key.any { it == '%' || it == '_' }) {
            val coverage = gramCoverage(query.foldedCompact, compactTitle)
            val distanceLimit = if (query.foldedCompact.length <= 8) 1 else 2
            if (near(query.foldedCompact, compactTitle, distanceLimit) || coverage >= 70) {
                return SearchMatch(MatchKind.TITLE_SIMILAR, coverage)
            }
        }
        val author = ChineseScript.simplify(QueryAnalyzer.normalize(document.author))
        val tags = ChineseScript.simplify(QueryAnalyzer.normalize(document.tags.joinToString(" ")))
        if (containsWords(author, words)) return SearchMatch(MatchKind.AUTHOR, 100)
        if (containsWords(tags, words)) return SearchMatch(MatchKind.TAG, 100)
        if (words.size > 1 && containsWords("$foldedTitle $author $tags", words)) {
            return SearchMatch(MatchKind.FIELDS, 100)
        }
        document.chapters.firstOrNull { chapter ->
            val key = ChineseScript.simplify(QueryAnalyzer.normalize(chapter))
            key.contains(query.foldedKey) || QueryAnalyzer.compact(key).contains(query.foldedCompact)
                || containsWords(key, words)
        }?.let { return SearchMatch(MatchKind.CHAPTER, 100, chapter = it) }
        return SearchMatch(MatchKind.NONE)
    }

    /** Ranges refer to the original UTF-16 text, including full-width/traditional glyphs. */
    fun highlights(value: String, query: SearchQuery): List<MatchRange> {
        if (query.isEmpty) return emptyList()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        val searchable = buildString {
            var index = 0
            while (index < value.length) {
                val width = Character.charCount(value.codePointAt(index))
                val piece = ChineseScript.simplify(Normalizer.normalize(value.substring(index, index + width),
                    Normalizer.Form.NFKC).lowercase(Locale.ROOT))
                piece.forEach { char ->
                    if (char.isLetterOrDigit() || char == '%' || char == '_') {
                        append(char); starts.add(index); ends.add(index + width)
                    }
                }
                index += width
            }
        }
        val ranges = query.words.map { ChineseScript.simplify(it).replace(" ", "") }
            .filter(String::isNotBlank).flatMap { word ->
                buildList {
                    var from = 0
                    while (from < searchable.length) {
                        val position = searchable.indexOf(word, from)
                        if (position < 0) break
                        add(MatchRange(starts[position], ends[position + word.length - 1]))
                        from = position + word.length
                    }
                }
            }.sortedBy { it.start }
        return ranges.fold(mutableListOf()) { merged, range ->
            val last = merged.lastOrNull()
            if (last != null && range.start <= last.end) merged[merged.lastIndex] = MatchRange(last.start, maxOf(last.end, range.end))
            else merged.add(range)
            merged
        }
    }

    private fun containsWords(value: String, words: List<String>): Boolean = words.isNotEmpty() && words.all(value::contains)

    private fun gramCoverage(query: String, title: String): Int {
        val grams = query.replace(" ", "").windowed(2).toSet()
        val titleGrams = title.replace(" ", "").windowed(2).toSet()
        return if (grams.isEmpty()) 0 else grams.count(titleGrams::contains) * 100 / grams.size
    }

    private fun near(query: String, title: String, limit: Int): Boolean {
        if (title.length > 128 || kotlin.math.abs(query.length - title.length) > limit) return false
        var previous = IntArray(title.length + 1) { it }
        query.forEachIndexed { index, char ->
            val current = IntArray(title.length + 1)
            current[0] = index + 1
            title.forEachIndexed { j, other ->
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (char == other) 0 else 1)
            }
            if (current.minOrNull()!! > limit) return false
            previous = current
        }
        return previous.last() <= limit
    }
}
