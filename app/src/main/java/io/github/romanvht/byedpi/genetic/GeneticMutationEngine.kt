package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.data.StrategyResult

/**
 * Движок генетических мутаций для стратегий ByeDPI.
 * Позволяет эволюционировать наборы команд ByeDPI скрещиванием, мутациями и отбором.
 */
object GeneticMutationEngine {

    /**
     * Генерирует следующее поколение уникальных стратегий на основе успеваемости текущих результатов.
     *
     * @param currentResults результаты тестирования текущей популяции
     * @param targetCount требуемый размер новой популяции
     * @param baseMutationRate базовая вероятность мутации гена
     * @param sni домен для замены {sni}
     * @param testedHistory множество команд, которые уже проверялись ранее в этой сессии
     * @param stagnationGenerations количество поколений без улучшения наилучшего fitness
     */
    fun generateNextGeneration(
        currentResults: List<StrategyResult>,
        targetCount: Int = 20,
        baseMutationRate: Float = 0.35f,
        sni: String = "google.com",
        testedHistory: Set<String> = emptySet(),
        stagnationGenerations: Int = 0
    ): List<String> {
        // Адаптивная вероятность мутации: повышается при застое (стагнации)
        val adaptiveMutationRate = (baseMutationRate + (stagnationGenerations * 0.08f)).coerceIn(0.20f, 0.70f)

        val successful = currentResults.filter { it.successCount > 0 }.sortedByDescending { calculateFitness(it) }

        val newCommands = mutableSetOf<String>()

        // Элитизм: сохраняем лучшие уникальные успешные стратегии
        val topElites = successful.take(3)
        for (elite in topElites) {
            newCommands.add(elite.command)
        }

        val baseGenomes = if (successful.isNotEmpty()) {
            successful.map { StrategyGenome.parse(it.command) }
        } else {
            // Если в этом поколении успехов не было, используем любые доступные стратегии
            currentResults.map { StrategyGenome.parse(it.command) }.ifEmpty {
                listOf(StrategyGenome.parse("--split 1+s"))
            }
        }

        var attempts = 0
        val maxAttempts = targetCount * 30

        while (newCommands.size < targetCount && attempts < maxAttempts) {
            attempts++
            val p1 = baseGenomes.random()
            val p2 = baseGenomes.random()

            // Crossover или клонирование
            val childGenome = if (kotlin.random.Random.nextFloat() < 0.65f) {
                StrategyGenome.crossover(p1, p2)
            } else {
                p1.copyGenome()
            }

            // Mutate
            val mutatedGenome = StrategyGenome.mutate(childGenome, adaptiveMutationRate)
            val cmd = mutatedGenome.toCommandLine(sni)

            if (cmd.isNotBlank() && !testedHistory.contains(cmd)) {
                newCommands.add(cmd)
            }
        }

        // Если из-за истории проверок не удалось набрать уникальные мутации, разрешаем любые валидные варианты
        var fillAttempts = 0
        while (newCommands.size < targetCount && fillAttempts < 100) {
            fillAttempts++
            val p = baseGenomes.random()
            val m = StrategyGenome.mutate(p, adaptiveMutationRate + 0.1f).toCommandLine(sni)
            if (m.isNotBlank()) {
                newCommands.add(m)
            }
        }

        return newCommands.toList()
    }

    /**
     * Функция приспособленности (Fitness function):
     * Учитывает процент успешных запросов, разнообразие покрытых сайтов и лаконичность.
     */
    fun calculateFitness(result: StrategyResult): Double {
        if (result.totalRequests <= 0) return 0.0
        val successRatio = result.successCount.toDouble() / result.totalRequests.toDouble()

        // Покрытие сайтов (доля сайтов, где прошла хотя бы 1 проверка)
        val workingSites = result.siteResults.count { it.successCount > 0 }
        val siteRatio = if (result.siteResults.isNotEmpty()) {
            workingSites.toDouble() / result.siteResults.size.toDouble()
        } else {
            0.0
        }

        // Бонус за компактность команды (меньше флагов = выше скорость и стабильность)
        val lengthPenalty = (result.command.length / 250.0).coerceIn(0.0, 0.1)

        // Взвешенная оценка (70% удержания успехов, 25% покрытие сайтов, 5% бонус за компактность)
        val score = (successRatio * 0.70) + (siteRatio * 0.25) + 0.05 - lengthPenalty
        return score.coerceIn(0.0, 1.0)
    }
}
