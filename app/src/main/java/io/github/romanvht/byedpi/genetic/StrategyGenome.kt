package io.github.romanvht.byedpi.genetic

import io.github.romanvht.byedpi.utility.shellSplit
import kotlin.random.Random

/**
 * Класс, представляющий геном стратегии ByeDPI.
 * Разбивает командную строку ByeDPI на модифицируемые гены (параметры).
 * Поддерживает как длинные (--split, --disorder), так и короткие (-s, -d, -o, -t, -r, -f) флаги ByeDPI.
 */
data class StrategyGenome(
    var splitMode: String = "", // --split 1+s, -s1+s, -s2+s, --split 1+s+m, etc.
    var disorderMode: String = "", // --disorder 1+s, -d1+s, -d3+s
    var oobMode: String = "", // --oob 1+s, -o1, -o2
    var ttlMode: String = "", // --ttl 3, -t3, -t5, --ttl 1+s
    var tlsrecMode: String = "", // --tlsrec 1+s, -r1+s, -r3+s
    var foolMode: String = "", // --fool md5, -f-1, -f-200, --fool badseq
    var fakeMode: String = "", // --fake 1, -q1, -q1+s
    var hostmix: Boolean = false, // --hostmix, -Mh
    var hostcase: Boolean = false, // --hostcase
    var hostspell: Boolean = false, // --hostspell
    var auto: String = "", // --auto=torst, -a1, -a1 -As
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
        if (fakeMode.isNotEmpty()) sb.append(" ").append(fakeMode)
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
            fakeMode = this.fakeMode,
            hostmix = this.hostmix,
            hostcase = this.hostcase,
            hostspell = this.hostspell,
            auto = this.auto,
            extraFlags = this.extraFlags.toMutableList()
        )
    }

    /**
     * Сравнивает с другим геномом и возвращает список измененных/мутировавших параметров.
     */
    fun getDiff(other: StrategyGenome): List<String> {
        val diffs = mutableListOf<String>()
        if (this.splitMode != other.splitMode && this.splitMode.isNotEmpty()) diffs.add(this.splitMode)
        if (this.disorderMode != other.disorderMode && this.disorderMode.isNotEmpty()) diffs.add(this.disorderMode)
        if (this.oobMode != other.oobMode && this.oobMode.isNotEmpty()) diffs.add(this.oobMode)
        if (this.ttlMode != other.ttlMode && this.ttlMode.isNotEmpty()) diffs.add(this.ttlMode)
        if (this.tlsrecMode != other.tlsrecMode && this.tlsrecMode.isNotEmpty()) diffs.add(this.tlsrecMode)
        if (this.foolMode != other.foolMode && this.foolMode.isNotEmpty()) diffs.add(this.foolMode)
        if (this.fakeMode != other.fakeMode && this.fakeMode.isNotEmpty()) diffs.add(this.fakeMode)
        if (this.hostmix != other.hostmix && this.hostmix) diffs.add("--hostmix")
        if (this.hostcase != other.hostcase && this.hostcase) diffs.add("--hostcase")
        if (this.hostspell != other.hostspell && this.hostspell) diffs.add("--hostspell")
        if (this.auto != other.auto && this.auto.isNotEmpty()) diffs.add(this.auto)
        return diffs
    }

    companion object {
        private val SPLIT_OPTIONS = listOf(
            "", "--split 1+s", "--split 2+s", "--split 3", "--split 1+m", "--split 2+m",
            "--split 1+s+m", "--split 1", "-s1+s", "-s2+s", "-s3+s", "-s1+m", "-s1+s,2+m"
        )
        private val DISORDER_OPTIONS = listOf(
            "", "--disorder 1+s", "--disorder 2", "--disorder 3+s", "--disorder 1+m",
            "--disorder 1", "-d1", "-d1+s", "-d3+s", "-d1+m", "-d2+s"
        )
        private val OOB_OPTIONS = listOf(
            "", "--oob 1+s", "--oob 2", "--oob 3", "--oob 1+m", "--oob 1",
            "-o1", "-o2", "-o1+s", "-o2+s"
        )
        private val TTL_OPTIONS = listOf(
            "", "--ttl 1", "--ttl 3", "--ttl 5", "--ttl 1+s", "--ttl 3+s", "--ttl 1+m",
            "-t1", "-t3", "-t5", "-t7", "-t8"
        )
        private val TLSREC_OPTIONS = listOf(
            "", "--tlsrec 1+s", "--tlsrec 2+s", "--tlsrec 1+m", "--tlsrec 3",
            "-r1+s", "-r3+s", "-r5+s", "-r1+sm"
        )
        private val FOOL_OPTIONS = listOf(
            "", "--fool md5", "--fool badseq", "--fool datanoack", "--fool badsum", "--fool hopbyhop",
            "-f-1", "-f-200", "-f1", "-f2"
        )
        private val FAKE_OPTIONS = listOf(
            "", "--fake 1", "-q1", "-q1+s", "-q2+s", "--fake 1+s"
        )
        private val AUTO_OPTIONS = listOf(
            "", "--auto=torst", "--auto=none", "--auto=torst,2", "-a1", "-a1 -As", "-a1 -At"
        )

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
                    token.startsWith("-s") && token.length > 2 && !token.startsWith("-s=") -> {
                        genome.splitMode = token
                    }
                    token == "-s" && i + 1 < tokens.size -> {
                        genome.splitMode = "-s ${tokens[i + 1]}"
                        i++
                    }

                    token == "--disorder" && i + 1 < tokens.size -> {
                        genome.disorderMode = "--disorder ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--disorder=") -> genome.disorderMode = token.replace("=", " ")
                    token.startsWith("-d") && token.length > 2 && !token.startsWith("-d=") -> {
                        genome.disorderMode = token
                    }
                    token == "-d" && i + 1 < tokens.size -> {
                        genome.disorderMode = "-d ${tokens[i + 1]}"
                        i++
                    }

                    token == "--oob" && i + 1 < tokens.size -> {
                        genome.oobMode = "--oob ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--oob=") -> genome.oobMode = token.replace("=", " ")
                    token.startsWith("-o") && token.length > 2 -> {
                        genome.oobMode = token
                    }
                    token == "-o" && i + 1 < tokens.size -> {
                        genome.oobMode = "-o ${tokens[i + 1]}"
                        i++
                    }

                    token == "--ttl" && i + 1 < tokens.size -> {
                        genome.ttlMode = "--ttl ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--ttl=") -> genome.ttlMode = token.replace("=", " ")
                    token.startsWith("-t") && token.length > 2 -> {
                        genome.ttlMode = token
                    }
                    token == "-t" && i + 1 < tokens.size -> {
                        genome.ttlMode = "-t ${tokens[i + 1]}"
                        i++
                    }

                    token == "--tlsrec" && i + 1 < tokens.size -> {
                        genome.tlsrecMode = "--tlsrec ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--tlsrec=") -> genome.tlsrecMode = token.replace("=", " ")
                    token.startsWith("-r") && token.length > 2 -> {
                        genome.tlsrecMode = token
                    }
                    token == "-r" && i + 1 < tokens.size -> {
                        genome.tlsrecMode = "-r ${tokens[i + 1]}"
                        i++
                    }

                    token == "--fool" && i + 1 < tokens.size -> {
                        genome.foolMode = "--fool ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--fool=") -> genome.foolMode = token.replace("=", " ")
                    token.startsWith("-f") && token.length > 2 -> {
                        genome.foolMode = token
                    }
                    token == "-f" && i + 1 < tokens.size -> {
                        genome.foolMode = "-f ${tokens[i + 1]}"
                        i++
                    }

                    token == "--fake" && i + 1 < tokens.size -> {
                        genome.fakeMode = "--fake ${tokens[i + 1]}"
                        i++
                    }
                    token.startsWith("--fake=") -> genome.fakeMode = token.replace("=", " ")
                    token.startsWith("-q") && token.length > 2 -> {
                        genome.fakeMode = token
                    }
                    token == "-q" && i + 1 < tokens.size -> {
                        genome.fakeMode = "-q ${tokens[i + 1]}"
                        i++
                    }

                    token == "--hostmix" || token == "-Mh" -> genome.hostmix = true
                    token == "--hostcase" -> genome.hostcase = true
                    token == "--hostspell" -> genome.hostspell = true
                    token.startsWith("--auto") || token.startsWith("-a") -> genome.auto = token
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
                mutated.fakeMode = FAKE_OPTIONS[rng.nextInt(FAKE_OPTIONS.size)]
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

            // Синергетические правила мутации: когда активен fake mode, полезно согласовать TTL / fool
            if (mutated.fakeMode.isNotEmpty() && mutated.ttlMode.isEmpty() && rng.nextFloat() < 0.60f) {
                mutated.ttlMode = listOf("-t3", "-t5", "-t8", "--ttl 3+s").random(rng)
            }
            if (mutated.fakeMode.isNotEmpty() && mutated.foolMode.isEmpty() && rng.nextFloat() < 0.50f) {
                mutated.foolMode = listOf("-f-1", "-f-200", "--fool md5").random(rng)
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
                fakeMode = if (rng.nextBoolean()) parent1.fakeMode else parent2.fakeMode,
                hostmix = if (rng.nextBoolean()) parent1.hostmix else parent2.hostmix,
                hostcase = if (rng.nextBoolean()) parent1.hostcase else parent2.hostcase,
                hostspell = if (rng.nextBoolean()) parent1.hostspell else parent2.hostspell,
                auto = if (rng.nextBoolean()) parent1.auto else parent2.auto,
                extraFlags = (if (rng.nextBoolean()) parent1.extraFlags else parent2.extraFlags).toMutableList()
            )
        }
    }
}
