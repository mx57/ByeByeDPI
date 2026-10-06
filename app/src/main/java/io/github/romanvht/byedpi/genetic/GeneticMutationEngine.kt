package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.data.StrategyResult

/**
 * Движок генетических мутаций для стратегий ByeDPI.
 * Позволяет эволюционировать наборы команд ByeDPI скрещиванием и мутациями.
 */
object GeneticMutationEngine {

    /**
     * Генерирует следующее поколение стратегий на основе успеваемости текущих результатов.
     */
    fun generateNextGeneration(
        currentResults: List<StrategyResult>,
        targetCount: Int = 20,
        mutationRate: Float = 0.35f,
        sni: String = "google.com"
    ): List<String> {
        val successful = currentResults.filter { it.successCount > 0 }.sortedByDescending { calculateFitness(it) }

        val newCommands = mutableSetOf<String>()

        // Elitism: сохраняем топ-3 успешных стратегий напрямую
        val topElites = successful.take(3)
        for (elite in topElites) {
            newCommands.add(elite.command)
        }

        val baseGenomes = if (successful.isNotEmpty()) {
            successful.map { StrategyGenome.parse(it.command) }
        } else {
            // Если успехов не было, берём варианты из всех текущих результатов
            currentResults.take(5).map { StrategyGenome.parse(it.command) }
        }

        if (baseGenomes.isEmpty()) {
            return currentResults.map { it.command }
        }

        var attempts = 0
        val maxAttempts = targetCount * 10

        while (newCommands.size < targetCount && attempts < maxAttempts) {
            attempts++
            val p1 = baseGenomes.random()
            val p2 = baseGenomes.random()

            // Crossover & Mutation
            val childGenome = if (kotlin.random.Random.nextFloat() < 0.60f) {
                StrategyGenome.crossover(p1, p2)
            } else {
                p1.copyGenome()
            }

            val mutatedGenome = StrategyGenome.mutate(childGenome, mutationRate)
            val cmd = mutatedGenome.toCommandLine(sni)

            if (cmd.isNotBlank()) {
                newCommands.add(cmd)
            }
        }

        // Если не набрали достаточно, дополняем исходными или мутированными
        while (newCommands.size < targetCount) {
            val p = baseGenomes.random()
            val m = StrategyGenome.mutate(p, 0.5f).toCommandLine(sni)
            if (m.isNotBlank()) {
                newCommands.add(m)
            } else {
                break
            }
        }

        return newCommands.toList()
    }

    /**
     * Функция приспособленности (Fitness function):
     * Учитывает процент успешных запросов и разнообразие рабочих доменов.
     */
    fun calculateFitness(result: StrategyResult): Double {
        if (result.totalRequests <= 0) return 0.0
        val successRatio = result.successCount.toDouble() / result.totalRequests.toDouble()

        // Бонус за разнообразие сайтов (количество сайтов, где хотя бы 1 успешный запрос)
        val workingSites = result.siteResults.count { it.successCount > 0 }
        val siteRatio = if (result.siteResults.isNotEmpty()) {
            workingSites.toDouble() / result.siteResults.size.toDouble()
        } else {
            0.0
        }

        // Взвешенная оценка (80% успех, 20% покрытие доменов)
        return (successRatio * 0.8 + siteRatio * 0.2).coerceIn(0.0, 1.0)
    }
}
