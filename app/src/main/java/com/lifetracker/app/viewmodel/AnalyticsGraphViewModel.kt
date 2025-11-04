package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.analytics.EventAnalyticsCalculations
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityBucket
import com.lifetracker.app.analytics.EventAnalyticsCalculations.PredictabilityDataStatus
import com.lifetracker.core.analytics.EventAnalyticsRepository
import com.lifetracker.core.analytics.RollingAveragePoint
import com.lifetracker.core.analytics.TagAggregate
import com.lifetracker.core.model.EventType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsGraphScreenState(
    val predictability: PredictabilityGraphUiState = PredictabilityGraphUiState(),
    val rollingAverage: RollingAverageGraphUiState = RollingAverageGraphUiState(),
    val quickLog: QuickLogLeaderboardUiState = QuickLogLeaderboardUiState(),
    val presentationMode: AnalyticsPresentationMode = AnalyticsPresentationMode.CHART,
    val lastUpdatedAt: Instant? = null
)

enum class AnalyticsPresentationMode {
    CHART,
    TEXT
}

data class PredictabilityGraphUiState(
    val isLoading: Boolean = false,
    val buckets: List<PredictabilityBucket> = emptyList(),
    val lastRange: ClosedRange<Instant>? = null,
    val errorMessage: String? = null,
    val insufficientBucketCount: Int = 0,
    val selectedRangeDays: Int = AnalyticsGraphViewModel.DEFAULT_RANGE_DAYS,
    val selectedBucketMinutes: Int = AnalyticsGraphViewModel.DEFAULT_BUCKET_MINUTES,
    val selectedMinSamples: Long = AnalyticsGraphViewModel.DEFAULT_MIN_SAMPLES,
    val selectedSmoothingAlpha: Double = AnalyticsGraphViewModel.DEFAULT_SMOOTHING_ALPHA,
    val availableRangeOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_RANGE_OPTIONS,
    val availableBucketOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_BUCKET_OPTIONS,
    val availableMinSampleOptions: List<Long> = AnalyticsGraphViewModel.DEFAULT_MIN_SAMPLE_OPTIONS,
    val availableSmoothingOptions: List<Double> = AnalyticsGraphViewModel.DEFAULT_SMOOTHING_OPTIONS
)

data class RollingAverageGraphUiState(
    val isLoading: Boolean = false,
    val points: List<RollingAveragePoint> = emptyList(),
    val lastRange: ClosedRange<Instant>? = null,
    val errorMessage: String? = null,
    val selectedRangeDays: Int = AnalyticsGraphViewModel.DEFAULT_RANGE_DAYS,
    val selectedWindowDays: Int = AnalyticsGraphViewModel.DEFAULT_ROLLING_WINDOW,
    val selectedType: EventType = AnalyticsGraphViewModel.DEFAULT_ROLLING_TYPES.first(),
    val availableRangeOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_RANGE_OPTIONS,
    val availableWindowOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_ROLLING_WINDOWS,
    val availableTypeOptions: List<EventType> = AnalyticsGraphViewModel.DEFAULT_ROLLING_TYPES
)

data class QuickLogLeaderboardUiState(
    val isLoading: Boolean = false,
    val items: List<TagAggregate> = emptyList(),
    val lastRange: ClosedRange<Instant>? = null,
    val errorMessage: String? = null,
    val selectedRangeDays: Int = AnalyticsGraphViewModel.DEFAULT_RANGE_DAYS,
    val limit: Int = AnalyticsGraphViewModel.DEFAULT_TAG_LIMIT,
    val availableRangeOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_RANGE_OPTIONS,
    val availableLimitOptions: List<Int> = AnalyticsGraphViewModel.DEFAULT_TAG_LIMIT_OPTIONS
)

class AnalyticsGraphViewModel(
    private val analyticsRepository: EventAnalyticsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val timeProvider: () -> Instant = { Instant.now() }
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsGraphScreenState())
    val screenState: StateFlow<AnalyticsGraphScreenState> = _state.asStateFlow()

    private val predictabilityCache = object : LinkedHashMap<PredictabilityRequest, CachedPredictability>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PredictabilityRequest, CachedPredictability>?): Boolean =
            size > CACHE_MAX_SIZE
    }
    private val rollingAverageCache = object : LinkedHashMap<RollingAverageRequest, CachedRollingAverage>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RollingAverageRequest, CachedRollingAverage>?): Boolean =
            size > CACHE_MAX_SIZE
    }
    private val quickLogCache = object : LinkedHashMap<QuickLogRequest, CachedQuickLog>(CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<QuickLogRequest, CachedQuickLog>?): Boolean =
            size > CACHE_MAX_SIZE
    }

    fun refresh(force: Boolean = false) {
        val now = timeProvider()
        loadPredictability(end = now, force = force)
        loadRollingAverage(end = now, force = force)
        loadQuickLogTags(end = now, force = force)
    }

    fun selectRange(rangeDays: Int) {
        val now = timeProvider()
        loadPredictability(rangeDays = rangeDays, end = now, force = true)
        loadRollingAverage(rangeDays = rangeDays, end = now, force = true)
        loadQuickLogTags(rangeDays = rangeDays, end = now, force = true)
    }

    fun selectBucketMinutes(minutes: Int) {
        loadPredictability(bucketMinutes = minutes, force = true)
    }

    fun selectMinSamples(minSamples: Long) {
        loadPredictability(minSamples = minSamples, force = true)
    }

    fun selectSmoothingAlpha(alpha: Double) {
        loadPredictability(smoothingAlpha = alpha, force = true)
    }

    fun selectRollingWindow(windowDays: Int) {
        loadRollingAverage(windowDays = windowDays, force = true)
    }

    fun selectRollingAverageType(type: EventType) {
        loadRollingAverage(type = type, force = true)
    }

    fun selectQuickLogLimit(limit: Int) {
        loadQuickLogTags(limit = limit, force = true)
    }

    fun togglePresentationMode() {
        _state.update { current ->
            val nextMode = when (current.presentationMode) {
                AnalyticsPresentationMode.CHART -> AnalyticsPresentationMode.TEXT
                AnalyticsPresentationMode.TEXT -> AnalyticsPresentationMode.CHART
            }
            current.copy(presentationMode = nextMode)
        }
    }

    private fun loadPredictability(
        rangeDays: Int = _state.value.predictability.selectedRangeDays,
        bucketMinutes: Int = _state.value.predictability.selectedBucketMinutes,
        minSamples: Long = _state.value.predictability.selectedMinSamples,
        smoothingAlpha: Double = _state.value.predictability.selectedSmoothingAlpha,
        end: Instant = timeProvider(),
        force: Boolean = false
    ) {
        if (rangeDays <= 0) {
            _state.update { state ->
                state.copy(
                    predictability = state.predictability.copy(
                        errorMessage = "期間は1日以上で指定してください"
                    )
                )
            }
            return
        }

        val sanitizedRange = rangeDays.coerceAtLeast(1)
        val sanitizedBucket = bucketMinutes.coerceAtLeast(MIN_BUCKET_MINUTES)
        val sanitizedSamples = minSamples.coerceAtLeast(MIN_SAMPLE_THRESHOLD)
        val sanitizedSmoothing = smoothingAlpha.coerceIn(MIN_SMOOTHING_ALPHA, MAX_SMOOTHING_ALPHA)

        val request = PredictabilityRequest(
            rangeDays = sanitizedRange,
            bucketMinutes = sanitizedBucket,
            minSamples = sanitizedSamples,
            smoothingAlpha = sanitizedSmoothing
        )

        val cached = synchronized(predictabilityCache) { predictabilityCache[request] }
        if (!force && cached != null && Duration.between(cached.generatedAt, end) <= CACHE_TTL) {
            _state.update { state ->
                state.copy(
                    predictability = state.predictability.copy(
                        isLoading = false,
                        errorMessage = null,
                        buckets = cached.buckets,
                        lastRange = cached.range,
                        insufficientBucketCount = cached.insufficientBucketCount,
                        selectedRangeDays = sanitizedRange,
                        selectedBucketMinutes = sanitizedBucket,
                        selectedMinSamples = sanitizedSamples,
                        selectedSmoothingAlpha = sanitizedSmoothing
                    ),
                    lastUpdatedAt = cached.generatedAt
                )
            }
            return
        }

        val start = end.minus(sanitizedRange.toLong(), ChronoUnit.DAYS)
        _state.update { state ->
            state.copy(
                predictability = state.predictability.copy(
                    isLoading = true,
                    errorMessage = null,
                    selectedRangeDays = sanitizedRange,
                    selectedBucketMinutes = sanitizedBucket,
                    selectedMinSamples = sanitizedSamples,
                    selectedSmoothingAlpha = sanitizedSmoothing
                )
            )
        }

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                analyticsRepository.taskCompletionTransitions(start, end)
            }.onSuccess { transitions ->
                val points = EventAnalyticsCalculations.predictabilityPoints(transitions)
                val buckets = EventAnalyticsCalculations.predictabilityBuckets(
                    points = points,
                    bucketMinutes = sanitizedBucket,
                    minSamplesForEstimate = sanitizedSamples,
                    smoothingAlpha = sanitizedSmoothing,
                    zoneId = zoneId
                )
                val insufficient = buckets.count { it.dataStatus == PredictabilityDataStatus.INSUFFICIENT_SAMPLES }
                val cachedValue = CachedPredictability(
                    buckets = buckets,
                    range = start..end,
                    insufficientBucketCount = insufficient,
                    generatedAt = end
                )
                synchronized(predictabilityCache) {
                    predictabilityCache[request] = cachedValue
                }
                _state.update { state ->
                    state.copy(
                        predictability = state.predictability.copy(
                            isLoading = false,
                            errorMessage = null,
                            buckets = buckets,
                            lastRange = start..end,
                            insufficientBucketCount = insufficient
                        ),
                        lastUpdatedAt = end
                    )
                }
            }.onFailure { throwable ->
                _state.update { state ->
                    state.copy(
                        predictability = state.predictability.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "予測可能性データの取得に失敗しました"
                        )
                    )
                }
            }
        }
    }

    private fun loadRollingAverage(
        rangeDays: Int = _state.value.rollingAverage.selectedRangeDays,
        windowDays: Int = _state.value.rollingAverage.selectedWindowDays,
        type: EventType = _state.value.rollingAverage.selectedType,
        end: Instant = timeProvider(),
        force: Boolean = false
    ) {
        if (rangeDays <= 0) {
            _state.update { state ->
                state.copy(
                    rollingAverage = state.rollingAverage.copy(
                        errorMessage = "期間は1日以上で指定してください"
                    )
                )
            }
            return
        }

        val sanitizedRange = rangeDays.coerceAtLeast(1)
        val sanitizedWindow = windowDays.coerceAtLeast(1)
        val request = RollingAverageRequest(
            rangeDays = sanitizedRange,
            windowDays = sanitizedWindow,
            eventType = type
        )

        val cached = synchronized(rollingAverageCache) { rollingAverageCache[request] }
        if (!force && cached != null && Duration.between(cached.generatedAt, end) <= CACHE_TTL) {
            _state.update { state ->
                state.copy(
                    rollingAverage = state.rollingAverage.copy(
                        isLoading = false,
                        errorMessage = null,
                        points = cached.points,
                        lastRange = cached.range,
                        selectedRangeDays = sanitizedRange,
                        selectedWindowDays = sanitizedWindow,
                        selectedType = type
                    ),
                    lastUpdatedAt = state.lastUpdatedAt ?: cached.generatedAt
                )
            }
            return
        }

        val start = end.minus(sanitizedRange.toLong(), ChronoUnit.DAYS)
        _state.update { state ->
            state.copy(
                rollingAverage = state.rollingAverage.copy(
                    isLoading = true,
                    errorMessage = null,
                    selectedRangeDays = sanitizedRange,
                    selectedWindowDays = sanitizedWindow,
                    selectedType = type
                )
            )
        }

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                analyticsRepository.rollingAverage(type, sanitizedWindow, start, end)
            }.onSuccess { points ->
                val cachedValue = CachedRollingAverage(
                    points = points,
                    range = start..end,
                    generatedAt = end
                )
                synchronized(rollingAverageCache) {
                    rollingAverageCache[request] = cachedValue
                }
                _state.update { state ->
                    state.copy(
                        rollingAverage = state.rollingAverage.copy(
                            isLoading = false,
                            errorMessage = null,
                            points = points,
                            lastRange = start..end
                        ),
                        lastUpdatedAt = state.lastUpdatedAt ?: end
                    )
                }
            }.onFailure { throwable ->
                _state.update { state ->
                    state.copy(
                        rollingAverage = state.rollingAverage.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "ローリング平均データの取得に失敗しました"
                        )
                    )
                }
            }
        }
    }

    private fun loadQuickLogTags(
        rangeDays: Int = _state.value.quickLog.selectedRangeDays,
        limit: Int = _state.value.quickLog.limit,
        end: Instant = timeProvider(),
        force: Boolean = false
    ) {
        if (rangeDays <= 0) {
            _state.update { state ->
                state.copy(
                    quickLog = state.quickLog.copy(
                        errorMessage = "期間は1日以上で指定してください"
                    )
                )
            }
            return
        }

        val sanitizedRange = rangeDays.coerceAtLeast(1)
        val sanitizedLimit = limit.coerceAtLeast(3)
        val request = QuickLogRequest(
            rangeDays = sanitizedRange,
            limit = sanitizedLimit
        )

        val cached = synchronized(quickLogCache) { quickLogCache[request] }
        if (!force && cached != null && Duration.between(cached.generatedAt, end) <= CACHE_TTL) {
            _state.update { state ->
                state.copy(
                    quickLog = state.quickLog.copy(
                        isLoading = false,
                        errorMessage = null,
                        items = cached.items,
                        lastRange = cached.range,
                        selectedRangeDays = sanitizedRange,
                        limit = sanitizedLimit
                    ),
                    lastUpdatedAt = state.lastUpdatedAt ?: cached.generatedAt
                )
            }
            return
        }

        val start = end.minus(sanitizedRange.toLong(), ChronoUnit.DAYS)
        _state.update { state ->
            state.copy(
                quickLog = state.quickLog.copy(
                    isLoading = true,
                    errorMessage = null,
                    selectedRangeDays = sanitizedRange,
                    limit = sanitizedLimit
                )
            )
        }

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                analyticsRepository.topQuickLogTags(sanitizedLimit, start, end)
            }.onSuccess { items ->
                val cachedValue = CachedQuickLog(
                    items = items,
                    range = start..end,
                    generatedAt = end
                )
                synchronized(quickLogCache) {
                    quickLogCache[request] = cachedValue
                }
                _state.update { state ->
                    state.copy(
                        quickLog = state.quickLog.copy(
                            isLoading = false,
                            errorMessage = null,
                            items = items,
                            lastRange = start..end
                        ),
                        lastUpdatedAt = state.lastUpdatedAt ?: end
                    )
                }
            }.onFailure { throwable ->
                _state.update { state ->
                    state.copy(
                        quickLog = state.quickLog.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "タグ集計の取得に失敗しました"
                        )
                    )
                }
            }
        }
    }

    companion object {
        internal const val DEFAULT_RANGE_DAYS: Int = 7
        internal const val DEFAULT_BUCKET_MINUTES: Int = 60
        internal const val DEFAULT_MIN_SAMPLES: Long = 3
        internal const val DEFAULT_SMOOTHING_ALPHA: Double = 0.3
        internal val DEFAULT_RANGE_OPTIONS: List<Int> = listOf(3, 7, 14, 28)
        internal val DEFAULT_BUCKET_OPTIONS: List<Int> = listOf(60, 45, 30)
        internal val DEFAULT_MIN_SAMPLE_OPTIONS: List<Long> = listOf(1, 3, 5, 10)
        internal val DEFAULT_SMOOTHING_OPTIONS: List<Double> = listOf(0.2, 0.3, 0.5, 0.7)

        internal const val DEFAULT_ROLLING_WINDOW: Int = 7
        internal val DEFAULT_ROLLING_WINDOWS: List<Int> = listOf(3, 7, 14)
        internal val DEFAULT_ROLLING_TYPES: List<EventType> = listOf(
            EventType.TASK_COMPLETED,
            EventType.HABIT_COMPLETED,
            EventType.LOG_QUICK
        )

        internal const val DEFAULT_TAG_LIMIT: Int = 10
        internal val DEFAULT_TAG_LIMIT_OPTIONS: List<Int> = listOf(5, 10, 15, 20)

        internal const val MIN_BUCKET_MINUTES: Int = 15
        internal const val MIN_SAMPLE_THRESHOLD: Long = 1
        internal const val MIN_SMOOTHING_ALPHA: Double = 0.05
        internal const val MAX_SMOOTHING_ALPHA: Double = 1.0

        private val CACHE_TTL: Duration = Duration.ofMinutes(10)
        private const val CACHE_MAX_SIZE: Int = 12
    }

    private data class PredictabilityRequest(
        val rangeDays: Int,
        val bucketMinutes: Int,
        val minSamples: Long,
        val smoothingAlpha: Double
    )

    private data class RollingAverageRequest(
        val rangeDays: Int,
        val windowDays: Int,
        val eventType: EventType
    )

    private data class QuickLogRequest(
        val rangeDays: Int,
        val limit: Int
    )

    private data class CachedPredictability(
        val buckets: List<PredictabilityBucket>,
        val range: ClosedRange<Instant>,
        val insufficientBucketCount: Int,
        val generatedAt: Instant
    )

    private data class CachedRollingAverage(
        val points: List<RollingAveragePoint>,
        val range: ClosedRange<Instant>,
        val generatedAt: Instant
    )

    private data class CachedQuickLog(
        val items: List<TagAggregate>,
        val range: ClosedRange<Instant>,
        val generatedAt: Instant
    )
}

