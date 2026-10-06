package io.github.romanvht.byedpi.genetic

/**
 * Настройки и критерии остановки работы движка эволюции.
 */
data class EvolutionSettings(
    val baseCommand: String = "",
    val timeLimitMinutes: Int = 10, // 0 - без лимита по времени
    val targetSuccessRatePercent: Int = 95, // Целевой % проходимости сайтов (1..100)
    val maxGenerations: Int = 20, // Лимит поколений (0 - без лимита)
    val populationSize: Int = 10, // Размер популяции в одном поколении
    val mutationRate: Float = 0.35f, // Вероятность мутации
    val sni: String = "google.com"
)

/**
 * Состояние сервиса непрерывной эволюции.
 */
data class EvolutionState(
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val currentGeneration: Int = 0,
    val totalGenerations: Int = 20,
    val bestCommand: String = "",
    val bestSuccessRate: Float = 0f,
    val currentTestedCommand: String = "",
    val elapsedTimeSeconds: Long = 0,
    val statusMessage: String = ""
)
