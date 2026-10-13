package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import org.junit.Assert.*
import org.junit.Test

class GeneticEngineTest {

    @Test
    fun testStrategyGenomeParsingAndCommandLine() {
        val cmd = "-s1+s -d3+s -q1 -Mh -t5 --split 1+m"
        val genome = StrategyGenome.parse(cmd)

        assertTrue("Genome hostmix should be true", genome.hostmix)
        assertNotNull(genome.toCommandLine("google.com"))
    }

    @Test
    fun testGenomeMutationAndDiff() {
        val base = StrategyGenome.parse("-s1+s -d1+s")
        val mutated = StrategyGenome.mutate(base, mutationRate = 1.0f)
        val diff = base.getDiff(mutated)
        assertNotNull(diff)
    }

    @Test
    fun testFitnessCalculation() {
        val result = StrategyResult(command = "--split 1+s")
        result.totalRequests = 10
        result.successCount = 8
        result.siteResults.add(SiteResult("google.com", 8, 10))

        val fitness = GeneticMutationEngine.calculateFitness(result)
        assertTrue("Fitness should be greater than 0", fitness > 0.0)
        assertTrue("Fitness should be <= 1.0", fitness <= 1.0)
    }

    @Test
    fun testGenerationOfNextPopulation() {
        val res1 = StrategyResult(command = "--split 1+s").apply {
            totalRequests = 10
            successCount = 9
            siteResults.add(SiteResult("site1.com", 9, 10))
        }
        val res2 = StrategyResult(command = "--disorder 3+s").apply {
            totalRequests = 10
            successCount = 5
            siteResults.add(SiteResult("site2.com", 5, 10))
        }

        val nextGen = GeneticMutationEngine.generateNextGeneration(
            currentResults = listOf(res1, res2),
            targetCount = 5,
            baseMutationRate = 0.35f,
            sni = "google.com"
        )

        assertEquals(5, nextGen.size)
    }
}
