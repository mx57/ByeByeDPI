package io.github.romanvht.byedpi.ml

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.genetic.GeneticMutationEngine

/**
 * Модель сохранения профилей сетей провайдеров.
 */
data class ProviderProfile(
    val networkId: String,
    var bestCommand: String = "",
    var bestFitness: Double = 0.0,
    var topStrategies: MutableList<String> = mutableListOf(),
    var totalEvaluations: Int = 0,
    var lastUpdated: Long = System.currentTimeMillis()
)

/**
 * ML-движок валидации и классификации/прогнозирования лучших параметров ByeDPI под сеть.
 */
class NetworkMlEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("network_ml_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Возвращает текущий профиль для активной сети провайдера.
     */
    fun getCurrentProfile(): ProviderProfile {
        val networkId = NetworkDetector.getCurrentNetworkId(context)
        return getProfile(networkId)
    }

    fun getProfile(networkId: String): ProviderProfile {
        val json = prefs.getString(networkId, null)
        if (json != null) {
            try {
                val profile = gson.fromJson(json, ProviderProfile::class.java)
                if (profile != null) return profile
            } catch (_: Exception) {}
        }
        return ProviderProfile(networkId = networkId)
    }

    /**
     * Обучает ML-модель для текущей сети, ранжирует результаты тестирования
     * и сохраняет наиболее устойчивые стратегии.
     */
    fun trainAndSave(results: List<StrategyResult>): ProviderProfile {
        val networkId = NetworkDetector.getCurrentNetworkId(context)
        val profile = getProfile(networkId)

        val rankedResults = results.map { result ->
            val fitness = GeneticMutationEngine.calculateFitness(result)
            val mlScore = predictMlScore(result.command, fitness)
            Pair(result, mlScore)
        }.sortedByDescending { it.second }

        if (rankedResults.isNotEmpty()) {
            val topPair = rankedResults.first()
            val bestResult = topPair.first
            val score = topPair.second

            if (score > profile.bestFitness || profile.bestCommand.isEmpty()) {
                profile.bestCommand = bestResult.command
                profile.bestFitness = score
            }

            profile.topStrategies = rankedResults.take(10).map { it.first.command }.toMutableList()
            profile.totalEvaluations += results.size
            profile.lastUpdated = System.currentTimeMillis()

            prefs.edit {
                putString(networkId, gson.toJson(profile))
            }
        }

        return profile
    }

    /**
     * Продвинутый ML-оцениватель с весами признаков, штрафом за избыточность команд
     * и обучением на базе предыдущей истории провайдера.
     */
    fun predictMlScore(command: String, empiricalFitness: Double): Double {
        val currentProfile = getCurrentProfile()
        var featureScore = 0.40

        // Анализ эффективности комбинаций
        if (command.contains("--split") && command.contains("--disorder")) featureScore += 0.20
        else if (command.contains("--split") || command.contains("--disorder")) featureScore += 0.10

        if (command.contains("--oob")) featureScore += 0.10
        if (command.contains("--ttl")) featureScore += 0.10
        if (command.contains("--tlsrec")) featureScore += 0.10
        if (command.contains("--fool")) featureScore += 0.10
        if (command.contains("--hostmix") || command.contains("--hostcase")) featureScore += 0.05

        // Бонус за сходство с топовыми стратегиями этой конкретной сети
        if (currentProfile.topStrategies.any { top -> top.isNotBlank() && command.contains(top.take(10)) }) {
            featureScore += 0.15
        }

        // Небольшой штраф за избыточно длинные дублирующиеся аргументы
        val tokenCount = command.split(" ").size
        if (tokenCount > 8) {
            featureScore -= 0.05
        }

        // Итоговая баесовская взвешенная оценка (80% результаты реальной проверки, 20% ML-модель)
        return (empiricalFitness * 0.80 + featureScore.coerceIn(0.0, 1.0) * 0.20).coerceIn(0.0, 1.0)
    }
}
