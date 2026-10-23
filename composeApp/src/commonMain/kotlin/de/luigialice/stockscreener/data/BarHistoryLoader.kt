package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.Bar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.coroutineContext

/** Owns the loaded price history and lazy chart downloads, separate from scan state. */
internal class BarHistoryLoader(
    private val api: YahooApi,
    private val cache: BarCache,
) {
    private val _bars = MutableStateFlow<Map<String, List<Bar>>>(emptyMap())
    val bars: StateFlow<Map<String, List<Bar>>> = _bars

    private val loadJobs = mutableMapOf<String, Job>()
    private var generation = 0

    fun clear() {
        cancelLoads()
        _bars.value = emptyMap()
    }

    fun cancelLoads() {
        generation++
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    fun replaceFromScan(bars: Map<String, List<Bar>>) {
        _bars.value = bars.toMap()
    }

    fun load(ticker: String, scope: CoroutineScope) {
        if (_bars.value[ticker] != null || loadJobs[ticker]?.isActive == true) return
        val requestGeneration = generation
        val loadJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val loaded = cache.freshBars(ticker)
                    ?: withContext(Dispatchers.Default) {
                        api.fetchBars(ticker).let { fetched ->
                            fetched.bars.also { if (it.isNotEmpty()) cache.store(ticker, it, fetched.name) }
                        }
                    }
                if (requestGeneration == generation && loaded.isNotEmpty()) {
                    _bars.update { it + (ticker to loaded) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A chart with no bars stays empty; scan errors are reported separately.
            } finally {
                if (requestGeneration == generation && loadJobs[ticker] === coroutineContext[Job]) {
                    loadJobs.remove(ticker)
                }
            }
        }
        loadJobs[ticker] = loadJob
        loadJob.start()
    }
}
