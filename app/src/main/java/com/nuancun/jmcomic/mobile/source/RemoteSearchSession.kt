package com.nuancun.jmcomic.mobile.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

data class SearchBatch(
    val hits: List<SearchHit>,
    val hasMore: Boolean,
    val query: SearchQuery,
    val notice: String? = null,
)

/** Owns query variants, per-variant paging, bounded retrieval and stable presentation. */
class RemoteSearchSession(input: String, private val source: SearchSource) {
    val query = QueryAnalyzer.analyze(input)
    private val plan = QueryAnalyzer.remoteQueries(query)
    private data class Branch(
        val query: String,
        var page: Int = 1,
        var exhausted: Boolean = false,
        var failures: Int = 0,
        var rows: Int = 0,
        val seen: MutableSet<String> = mutableSetOf(),
    )
    private data class Attempt(val branch: Branch, val page: Int, val result: Result<SearchPage>)
    private val branches = mutableListOf<Branch>()
    private val candidates = linkedMapOf<String, SearchHit>()
    private var displayed = emptyList<SearchHit>()
    private var loaded = false
    private var requests = 0
    private val mutex = Mutex()
    private val concurrency = Semaphore(2)

    suspend fun loadNext(): SearchBatch = mutex.withLock {
        var failed = false
        if (!loaded && plan.isNotEmpty()) {
            val primary = Branch(plan.first()).also(branches::add)
            failed = apply(fetch(listOf(primary)))
            if (!hasStrongTitle() && query.id == null) {
                val expansions = plan.drop(1).map(::Branch)
                branches.addAll(expansions)
                failed = apply(fetch(expansions)) || failed
            }
            if (query.id == null && !hasExactTitle() && !primary.exhausted && requests < FIRST_BATCH_BUDGET) {
                failed = apply(fetch(listOf(primary))) || failed
            }
        } else if (loaded) {
            failed = apply(fetch(branches.filterNot { it.exhausted }))
        }
        currentCoroutineContext().ensureActive()
        val ranked = SearchRanker.rank(query, candidates.values.toList())
        displayed = if (loaded) {
            val byId = ranked.associateBy { it.id }
            val oldIds = displayed.map { it.id }.toSet()
            displayed.mapNotNull { byId[it.id] } + ranked.filterNot { it.id in oldIds }
        } else ranked
        loaded = true
        SearchBatch(
            displayed,
            requests < SESSION_BUDGET && branches.any { !it.exhausted },
            query,
            when {
                failed && displayed.isEmpty() -> "暂时无法检索，请重试"
                failed -> "部分结果暂时无法加载，可重试或继续查看"
                else -> null
            },
        )
    }

    private suspend fun fetch(targets: List<Branch>): List<Attempt> {
        val selected = targets.filter { !it.exhausted }.take((SESSION_BUDGET - requests).coerceAtLeast(0))
        requests += selected.size
        return coroutineScope {
            selected.map { branch -> async {
                concurrency.withPermit {
                    val result = try {
                        Result.success(withTimeout(PAGE_TIMEOUT_MS) { source.searchPage(branch.query, branch.page) })
                    } catch (error: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        Result.failure(error)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    Attempt(branch, branch.page, result)
                }
            } }.awaitAll()
        }
    }

    private fun apply(attempts: List<Attempt>): Boolean {
        var failed = false
        attempts.forEach { attempt ->
            val branch = attempt.branch
            val page = attempt.result.getOrNull()
            if (page == null) {
                failed = true
                branch.failures++
                if (branch.failures >= 2) branch.exhausted = true
                return@forEach
            }
            branch.failures = 0
            val newInBranch = page.hits.any { it.id !in branch.seen }
            page.hits.forEachIndexed { position, hit ->
                val incoming = hit.copy(origins = hit.origins + SearchOrigin(branch.query, attempt.page, position))
                candidates[hit.id] = candidates[hit.id]?.let { merge(it, incoming) } ?: incoming
                branch.seen.add(hit.id)
            }
            branch.rows += page.rawCount
            branch.page++
            branch.exhausted = page.direct || page.hasMore == false || page.rawCount == 0 || !newInBranch
                || page.totalResults?.let { branch.rows >= it } == true || branch.page > MAX_PAGES_PER_BRANCH
        }
        return failed
    }

    private fun hasStrongTitle(): Boolean = candidates.values.any { hit ->
        val kind = SearchRanker.match(query, SearchDocument(hit.id, hit.title, hit.authorLabel, hit.tags)).kind
        kind >= MatchKind.TITLE_WORDS
    }

    private fun hasExactTitle(): Boolean = candidates.values.any { hit ->
        SearchRanker.match(query, SearchDocument(hit.id, hit.title, hit.authorLabel, hit.tags)).kind >= MatchKind.TITLE_EQUIVALENT
    }

    private fun merge(old: SearchHit, incoming: SearchHit): SearchHit {
        fun match(hit: SearchHit) = SearchRanker.match(query, SearchDocument(hit.id, hit.title, hit.authorLabel, hit.tags))
        val better = if (SearchRanker.matchOrder.compare(match(incoming), match(old)) < 0) incoming else old
        val authors = (old.authors + incoming.authors).filter(String::isNotBlank).distinct()
        return better.copy(
            author = authors.firstOrNull().orEmpty(), authors = authors,
            tags = (old.tags + incoming.tags).distinct(), origins = (old.origins + incoming.origins).distinct(),
        )
    }

    companion object {
        private const val FIRST_BATCH_BUDGET = 4
        private const val SESSION_BUDGET = 24
        private const val MAX_PAGES_PER_BRANCH = 8
        private const val PAGE_TIMEOUT_MS = 20_000L
    }
}
