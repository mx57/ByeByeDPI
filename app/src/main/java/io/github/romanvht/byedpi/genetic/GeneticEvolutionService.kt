package io.github.romanvht.byedpi.genetic

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.EvolutionActivity
import io.github.romanvht.byedpi.data.Configuration
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.ml.NetworkMlEngine
import io.github.romanvht.byedpi.services.NativeEngine
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select

class GeneticEvolutionService : Service() {

    companion object {
        private const val TAG = "GeneticEvolutionService"
        private const val CHANNEL = "Evolution Channel"
        private const val NOTIFICATION_ID = 5
        private const val START_ACTION = "io.github.romanvht.byedpi.action.START_EVOLUTION"
        private const val STOP_ACTION = "io.github.romanvht.byedpi.action.STOP_EVOLUTION"

        private val mutableState = MutableStateFlow(EvolutionState())
        val state: StateFlow<EvolutionState> = mutableState.asStateFlow()
        val isRunning: Boolean get() = state.value.isRunning

        fun start(context: Context) {
            if (isRunning) return
            mutableState.value = EvolutionState(isRunning = true, isStopping = false, statusMessage = "Запуск эволюции...")
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GeneticEvolutionService::class.java).setAction(START_ACTION)
                )
            } catch (e: Exception) {
                mutableState.value = EvolutionState(isRunning = false, statusMessage = "Ошибка запуска")
                Log.e(TAG, "Failed to start evolution service", e)
            }
        }

        fun stop() {
            if (!isRunning) return
            mutableState.value = state.value.copy(isStopping = true, statusMessage = "Остановка...")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var evolutionJob: Job? = null
    private var engine: NativeEngine? = null

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, CHANNEL, R.string.app_name)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_ACTION -> {
                startForeground()
                if (evolutionJob == null) {
                    startEvolutionLoop()
                }
            }
            STOP_ACTION -> {
                stop()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startEvolutionLoop() {
        evolutionJob = scope.launch(Dispatchers.IO) {
            val settings = loadSettings()
            val netId = io.github.romanvht.byedpi.ml.NetworkDetector.getCurrentNetworkId(this@GeneticEvolutionService)
            val startTime = System.currentTimeMillis()
            var currentGeneration = 1
            var bestCmd = settings.baseCommand
            var bestRatio = 0f
            var bestSiteSummary = ""

            // Загружаем топы из предыдущих проверок подборщика
            val testResults = loadTestResults()
            val topFromTest = testResults.sortedByDescending { if (it.totalRequests > 0) it.successCount.toFloat() / it.totalRequests else 0f }
                .take(settings.topParentsCount)
                .map { it.command }

            val initialGenomes = if (topFromTest.isNotEmpty()) {
                topFromTest.map { StrategyGenome.parse(it) }
            } else {
                listOf(StrategyGenome.parse(settings.baseCommand))
            }

            var currentPopulation = mutableListOf<StrategyResult>()

            val populationCmds = mutableSetOf<String>()
            populationCmds.addAll(topFromTest)
            populationCmds.add(settings.baseCommand)

            var attempts = 0
            while (populationCmds.size < settings.populationSize && attempts < 100) {
                attempts++
                val p = initialGenomes.random()
                populationCmds.add(StrategyGenome.mutate(p, settings.mutationRate).toCommandLine(settings.sni))
            }

            val curCmds = populationCmds.toMutableList()

            try {
                while (isActive && !mutableState.value.isStopping) {
                    val elapsedTimeSec = (System.currentTimeMillis() - startTime) / 1000

                    // Критерий 1: Ограничение по времени
                    if (settings.timeLimitMinutes > 0 && elapsedTimeSec >= settings.timeLimitMinutes * 60) {
                        updateState { it.copy(statusMessage = "Достигнут лимит времени") }
                        break
                    }

                    // Критерий 2: Ограничение по поколениям
                    if (settings.maxGenerations > 0 && currentGeneration > settings.maxGenerations) {
                        updateState { it.copy(statusMessage = "Достигнут лимит поколений") }
                        break
                    }

                    // Критерий 3: Достижение целевого % успеха
                    if (settings.targetSuccessRatePercent > 0 && bestRatio >= (settings.targetSuccessRatePercent / 100f)) {
                        updateState { it.copy(statusMessage = "Достигнут целевой % успеха (${(bestRatio * 100).toInt()}%)") }
                        break
                    }

                    updateState {
                        it.copy(
                            networkId = netId,
                            currentGeneration = currentGeneration,
                            totalGenerations = settings.maxGenerations,
                            bestCommand = bestCmd,
                            bestSuccessRate = bestRatio,
                            bestSiteResultsSummary = bestSiteSummary,
                            elapsedTimeSeconds = elapsedTimeSec,
                            statusMessage = "Поколение $currentGeneration: тестирование ${curCmds.size} мутаций"
                        )
                    }

                    val genResults = mutableListOf<StrategyResult>()

                    for ((idx, cmd) in curCmds.withIndex()) {
                        if (!isActive || mutableState.value.isStopping) break

                        updateState {
                            it.copy(
                                currentTestedCommand = cmd,
                                statusMessage = "Поколение $currentGeneration [${idx + 1}/${curCmds.size}]: $cmd"
                            )
                        }

                        val result = evaluateStrategy(cmd, settings)
                        genResults.add(result)

                        val ratio = if (result.totalRequests > 0) result.successCount.toFloat() / result.totalRequests else 0f
                        if (ratio >= bestRatio) {
                            bestRatio = ratio
                            bestCmd = cmd
                            bestSiteSummary = result.siteResults.joinToString("\n") { site -> "${site.site}: ${site.successCount}/${site.totalCount}" }
                        }

                        updateState {
                            it.copy(
                                evaluatedStrategies = genResults.toList(),
                                bestCommand = bestCmd,
                                bestSuccessRate = bestRatio,
                                bestSiteResultsSummary = bestSiteSummary
                            )
                        }
                    }

                    currentPopulation = genResults

                    // Сохраняем лучший результат в ML-модель
                    val mlEngine = NetworkMlEngine(this@GeneticEvolutionService)
                    mlEngine.trainAndSave(genResults)

                    // Генерация следующего поколения мутаций
                    val nextGenCmds = GeneticMutationEngine.generateNextGeneration(
                        currentResults = currentPopulation,
                        targetCount = settings.populationSize,
                        mutationRate = settings.mutationRate,
                        sni = settings.sni
                    )

                    curCmds.clear()
                    curCmds.addAll(nextGenCmds)

                    currentGeneration++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Evolution loop error", e)
            } finally {
                withContext(NonCancellable) {
                    stopEngine()
                    updateState {
                        it.copy(
                            isRunning = false,
                            isStopping = false,
                            bestCommand = bestCmd,
                            bestSuccessRate = bestRatio,
                            statusMessage = "Эволюция завершена. Лучший результат: ${(bestRatio * 100).toInt()}%"
                        )
                    }
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun evaluateStrategy(cmd: String, settings: EvolutionSettings): StrategyResult {
        val result = StrategyResult(command = cmd)
        val prefs = getPreferences()
        DomainListUtils.syncLists(this)
        val sites = DomainListUtils.getActiveDomains(this).toList()
        val requestsCount = prefs.getIntStringNotNull("byedpi_proxytest_requests", 1).coerceAtLeast(1)
        val requestTimeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5).coerceAtLeast(1)
        val requestLimit = prefs.getIntStringNotNull("byedpi_proxytest_limit", 20).coerceAtLeast(1)
        val delaySec = prefs.getIntStringNotNull("byedpi_proxytest_delay", 1).coerceAtLeast(0)
        val host = prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
        val port = prefs.getIntStringNotNull("byedpi_proxy_port", 1080)

        result.totalRequests = sites.size * requestsCount

        try {
            val config = testConfiguration(this, cmd, host, port)
            val nativeEng = startEngine(config)

            supervisorScope {
                val engineExit = async { nativeEng.awaitExit() }
                val check = async {
                    delay(delaySec * 500L)
                    val connectHost = when (config.host) {
                        "0.0.0.0" -> "127.0.0.1"
                        "::", "[::]" -> "::1"
                        else -> config.host
                    }
                    SiteCheckUtils(connectHost, config.port).checkSitesAsync(
                        sites = sites,
                        requestsCount = requestsCount,
                        requestTimeout = requestTimeout,
                        concurrentRequests = requestLimit,
                        fullLog = true,
                        onSiteChecked = { site, successCount, countRequests ->
                            result.currentProgress += countRequests
                            result.successCount += successCount
                            result.siteResults.add(io.github.romanvht.byedpi.data.SiteResult(site, successCount, countRequests))
                        }
                    )
                    true
                }

                select {
                    engineExit.onAwait { false }
                    check.onAwait { it }
                }

                engineExit.cancel()
                check.cancel()
            }
        } catch (_: Exception) {} finally {
            stopEngine()
        }

        return result
    }

    private fun loadTestResults(): List<StrategyResult> {
        return try {
            val file = android.util.AtomicFile(java.io.File(filesDir, "proxy_test_results.json"))
            file.openRead().bufferedReader().use { reader ->
                val type = object : com.google.gson.reflect.TypeToken<List<StrategyResult>>() {}.type
                com.google.gson.Gson().fromJson<List<StrategyResult>>(reader, type) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun startEngine(configuration: Configuration): NativeEngine {
        val current = NativeEngine(applicationContext, Mode.Proxy, foreground = false)
        engine = current
        current.start(configuration)
        return current
    }

    private suspend fun stopEngine() {
        val current = engine ?: return
        try {
            current.stop()
            engine = null
        } catch (_: Exception) {}
    }

    private fun loadSettings(): EvolutionSettings {
        val prefs = getPreferences()
        val baseCmd = prefs.getString("byedpi_cmd_args", "--split 1+s") ?: "--split 1+s"
        val topParents = prefs.getIntStringNotNull("evolution_top_parents_count", 5)
        val timeLimit = prefs.getIntStringNotNull("evolution_time_limit", 10)
        val targetRate = prefs.getIntStringNotNull("evolution_target_rate", 95)
        val maxGen = prefs.getIntStringNotNull("evolution_max_gen", 20)
        val popSize = prefs.getIntStringNotNull("evolution_pop_size", 8)
        val sni = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")

        DomainListUtils.syncLists(this)
        return EvolutionSettings(
            baseCommand = baseCmd,
            topParentsCount = topParents,
            timeLimitMinutes = timeLimit,
            targetSuccessRatePercent = targetRate,
            maxGenerations = maxGen,
            populationSize = popSize,
            sni = sni
        )
    }

    private fun updateState(block: (EvolutionState) -> EvolutionState) {
        val newState = block(mutableState.value)
        mutableState.value = newState
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(newState))
    }

    private fun notification(state: EvolutionState): Notification {
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Эволюция стратегий ByeDPI")
            .setContentText(state.statusMessage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, EvolutionActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "Остановить", PendingIntent.getService(this, 0, Intent(this, GeneticEvolutionService::class.java).setAction(STOP_ACTION), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(state.value))
        }
    }
}
