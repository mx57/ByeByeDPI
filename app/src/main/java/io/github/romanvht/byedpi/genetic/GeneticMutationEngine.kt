package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.data.StrategyResult

/**
 * Движок генетических мутаций для стратегий ByeDPI.
 * Позволяет эволюционировать наборы команд ByeDPI скрещиванием, турнирным отбором, мутациями и инъекцией шаблонов.
 */
object GeneticMutationEngine {

    /**
     * Эффективные шаблоны обхода DPI для инъекции при застое эволюции или генерации стартовой популяции.
     */
    private val SEED_STRATEGY_TEMPLATES = listOf(
        "-f-1 -q1 -s1+s -d3+s -t5 -Mh",
        "-s1:5+sm -a1 -As -d1 -s3+s",
        "-r1+s -s2+s -f-200 -q1+s -t3",
        "--split 1+s --disorder 3+s --fake 1 --ttl 3 --hostmix",
        "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s",
        "-q1+s -s29+s -s30+s -s14+s -o5+s -f-1 -S",
        "-f1+nme -t6 -a1 -As -s1:6+sm -d3 -q7 -r6 -Mh",
        "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -r1+s -S -a1",
        "-n {sni} -Qr -f-204 -s1:5+sm -a1 -As -d1 -s3+s -s5+s -q7"
    )

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

        val ratedResults = currentResults.map { Pair(it, calculateFitness(it)) }
            .sortedByDescending { it.second }

        val successful = ratedResults.filter { it.first.successCount > 0 }

        val newCommands = mutableSetOf<String>()

        // Элитизм: сохраняем лучшие уникальные успешные стратегии
        val topElites = successful.take(3)
        for (elite in topElites) {
            newCommands.add(elite.first.command)
        }

        // Выбор геномов родительской популяции
        val parentPool = if (successful.isNotEmpty()) {
            successful.map { Pair(StrategyGenome.parse(it.first.command), it.second) }
        } else {
            currentResults.map { Pair(StrategyGenome.parse(it.command), calculateFitness(it)) }.ifEmpty {
                listOf(Pair(StrategyGenome.parse("--split 1+s"), 0.5))
            }
        }

        // Если застой больше 2 поколений — инъецируем шаблоны успешных стратегий
        if (stagnationGenerations >= 2) {
            for (tpl in SEED_STRATEGY_TEMPLATES) {
                if (newCommands.size < targetCount) {
                    val mutatedSeed = StrategyGenome.mutate(StrategyGenome.parse(tpl), adaptiveMutationRate)
                        .toCommandLine(sni)
                    if (mutatedSeed.isNotBlank() && !testedHistory.contains(mutatedSeed)) {
                        newCommands.add(mutatedSeed)
                    }
                }
            }
        }

        var attempts = 0
        val maxAttempts = targetCount * 30

        while (newCommands.size < targetCount && attempts < maxAttempts) {
            attempts++

            // Турнирный отбор родителей (k = 2)
            val p1 = selectParentTournament(parentPool)
            val p2 = selectParentTournament(parentPool)

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

        // Если из-за истории проверок не удалось набрать уникальные мутации, заполняем инъекциями и варьированными мутациями
        var fillAttempts = 0
        while (newCommands.size < targetCount && fillAttempts < 100) {
            fillAttempts++
            val seedTpl = SEED_STRATEGY_TEMPLATES.random()
            val m = StrategyGenome.mutate(StrategyGenome.parse(seedTpl), adaptiveMutationRate + 0.1f).toCommandLine(sni)
            if (m.isNotBlank()) {
                newCommands.add(m)
            }
        }

        return newCommands.toList()
    }

    /**
     * Турнирный отбор родителя (k = 2).
     */
    private fun selectParentTournament(pool: List<Pair<StrategyGenome, Double>>): StrategyGenome {
        if (pool.size == 1) return pool[0].first
        val c1 = pool.random()
        val c2 = pool.random()
        return if (c1.second >= c2.second) c1.first else c2.first
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
