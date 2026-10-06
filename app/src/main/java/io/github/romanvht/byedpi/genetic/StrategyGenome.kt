package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.utility.shellSplit
import kotlin.random.Random

/**
 * Класс, представляющий геном стратегии ByeDPI.
 * Разбивает командную строку ByeDPI на модифицируемые гены (параметры).
 */
data class StrategyGenome(
    var splitMode: String = "", // --split 1+s, --split 2, --split 1+s+m, etc.
    var disorderMode: String = "", // --disorder 1+s, --disorder 3
    var oobMode: String = "", // --oob 1+s, --oob 3
    var ttlMode: String = "", // --ttl 3, --ttl 1+s
    var tlsrecMode: String = "", // --tlsrec 1+s, --tlsrec 3
    var foolMode: String = "", // --fool md5, --fool badseq, --fool datanoack
    var hostmix: Boolean = false, // --hostmix
    var hostcase: Boolean = false, // --hostcase
    var hostspell: Boolean = false, // --hostspell
    var auto: String = "", // --auto=torst, --auto=none
    var extraFlags: MutableList<String> = mutableListOf()
) {
    fun toCommandLine(sni: String = "google.com"): String {
        val sb = StringBuilder()
        if (splitMode.isNotEmpty()) sb.append(" ").append(splitMode)
        if (disorderMode.isNotEmpty()) sb.append(" ").append(disorderMode)
        if (oobMode.isNotEmpty()) sb.append(" ").append(oobMode)
        if (ttlMode.isNotEmpty()) sb.append(" ").append(ttlMode)
        if (tlsrecMode.isNotEmpty()) sb.append(" ").append(tlsrecMode)
        if (foolMode.isNotEmpty()) sb.append(" ").append(foolMode)
        if (hostmix) sb.append(" --hostmix")
        if (hostcase) sb.append(" --hostcase")
        if (hostspell) sb.append(" --hostspell")
        if (auto.isNotEmpty()) sb.append(" ").append(auto)

        for (flag in extraFlags) {
            sb.append(" ").append(flag)
        }

        var res = sb.toString().trim()
        if (res.contains("{sni}")) {
            res = res.replace("{sni}", "\"$sni\"")
        }
        return res
    }

    fun copyGenome(): StrategyGenome {
        return StrategyGenome(
            splitMode = this.splitMode,
            disorderMode = this.disorderMode,
            oobMode = this.oobMode,
            ttlMode = this.ttlMode,
            tlsrecMode = this.tlsrecMode,
            foolMode = this.foolMode,
            hostmix = this.hostmix,
            hostcase = this.hostcase,
            hostspell = this.hostspell,
            auto = this.auto,
            extraFlags = this.extraFlags.toMutableList()
        )
    }

    companion object {
        private val SPLIT_OPTIONS = listOf("", "--split 1+s", "--split 2+s", "--split 3", "--split 1+m", "--split 2+m", "--split 1+s+m", "--split 1")
        private val DISORDER_OPTIONS = listOf("", "--disorder 1+s", "--disorder 2", "--disorder 3+s", "--disorder 1+m", "--disorder 1")
        private val OOB_OPTIONS = listOf("", "--oob 1+s", "--oob 2", "--oob 3", "--oob 1+m", "--oob 1")
        private val TTL_OPTIONS = listOf("", "--ttl 1", "--ttl 3", "--ttl 5", "--ttl 1+s", "--ttl 3+s", "--ttl 1+m")
        private val TLSREC_OPTIONS = listOf("", "--tlsrec 1+s", "--tlsrec 2+s", "--tlsrec 1+m", "--tlsrec 3")
        private val FOOL_OPTIONS = listOf("", "--fool md5", "--fool badseq", "--fool datanoack", "--fool badsum", "--fool hopbyhop")
        private val AUTO_OPTIONS = listOf("", "--auto=torst", "--auto=none", "--auto=torst,2")

        fun parse(commandLine: String): StrategyGenome {
            val tokens = shellSplit(commandLine)
            val genome = StrategyGenome()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                when {
                    token == "--split" && i + 1 < tokens.size -> {
                        genome.splitMode = "--split ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--split=") -> genome.splitMode = token.replace("=", " ")
                    token == "--disorder" && i + 1 < tokens.size -> {
                        genome.disorderMode = "--disorder ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--disorder=") -> genome.disorderMode = token.replace("=", " ")
                    token == "--oob" && i + 1 < tokens.size -> {
                        genome.oobMode = "--oob ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--oob=") -> genome.oobMode = token.replace("=", " ")
                    token == "--ttl" && i + 1 < tokens.size -> {
                        genome.ttlMode = "--ttl ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--ttl=") -> genome.ttlMode = token.replace("=", " ")
                    token == "--tlsrec" && i + 1 < tokens.size -> {
                        genome.tlsrecMode = "--tlsrec ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--tlsrec=") -> genome.tlsrecMode = token.replace("=", " ")
                    token == "--fool" && i + 1 < tokens.size -> {
                        genome.foolMode = "--fool ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--fool=") -> genome.foolMode = token.replace("=", " ")
                    token == "--hostmix" -> genome.hostmix = true
                    token == "--hostcase" -> genome.hostcase = true
                    token == "--hostspell" -> genome.hostspell = true
                    token.startsWith("--auto") -> genome.auto = token
                    else -> genome.extraFlags.add(token)
                }
                i++
            }
            return genome
        }

        fun mutate(parent: StrategyGenome, mutationRate: Float = 0.35f): StrategyGenome {
            val mutated = parent.copyGenome()
            val rng = Random.Default

            if (rng.nextFloat() < mutationRate) {
                mutated.splitMode = SPLIT_OPTIONS[rng.nextInt(SPLIT_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.disorderMode = DISORDER_OPTIONS[rng.nextInt(DISORDER_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.oobMode = OOB_OPTIONS[rng.nextInt(OOB_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.ttlMode = TTL_OPTIONS[rng.nextInt(TTL_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.tlsrecMode = TLSREC_OPTIONS[rng.nextInt(TLSREC_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.foolMode = FOOL_OPTIONS[rng.nextInt(FOOL_OPTIONS.size)]
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.hostmix = rng.nextBoolean()
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.hostcase = rng.nextBoolean()
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.hostspell = rng.nextBoolean()
            }
            if (rng.nextFloat() < mutationRate) {
                mutated.auto = AUTO_OPTIONS[rng.nextInt(AUTO_OPTIONS.size)]
            }

            return mutated
        }

        fun crossover(parent1: StrategyGenome, parent2: StrategyGenome): StrategyGenome {
            val rng = Random.Default
            return StrategyGenome(
                splitMode = if (rng.nextBoolean()) parent1.splitMode else parent2.splitMode,
                disorderMode = if (rng.nextBoolean()) parent1.disorderMode else parent2.disorderMode,
                oobMode = if (rng.nextBoolean()) parent1.oobMode else parent2.oobMode,
                ttlMode = if (rng.nextBoolean()) parent1.ttlMode else parent2.ttlMode,
                tlsrecMode = if (rng.nextBoolean()) parent1.tlsrecMode else parent2.tlsrecMode,
                foolMode = if (rng.nextBoolean()) parent1.foolMode else parent2.foolMode,
                hostmix = if (rng.nextBoolean()) parent1.hostmix else parent2.hostmix,
                hostcase = if (rng.nextBoolean()) parent1.hostcase else parent2.hostcase,
                hostspell = if (rng.nextBoolean()) parent1.hostspell else parent2.hostspell,
                auto = if (rng.nextBoolean()) parent1.auto else parent2.auto,
                extraFlags = (if (rng.nextBoolean()) parent1.extraFlags else parent2.extraFlags).toMutableList()
            )
        }
    }
}
