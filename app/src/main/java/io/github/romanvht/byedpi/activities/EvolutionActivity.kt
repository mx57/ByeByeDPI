package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.StrategyResultAdapter
import io.github.romanvht.byedpi.genetic.EvolutionState
import io.github.romanvht.byedpi.genetic.GeneticEvolutionService
import io.github.romanvht.byedpi.ml.NetworkDetector
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EvolutionActivity : BaseActivity() {

    private lateinit var tvNetworkInfo: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvElapsedTime: TextView
    private lateinit var tvGenerationsInfo: TextView
    private lateinit var tvTotalTestedInfo: TextView
    private lateinit var tvBestSuccessRateHeader: TextView
    private lateinit var tvCurrentTested: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvBestCommand: TextView
    private lateinit var tvBestSuccessRate: TextView
    private lateinit var tvBestSiteDetails: TextView
    private lateinit var btnToggleDomains: MaterialButton
    private lateinit var btnApplyBest: MaterialButton
    private lateinit var btnCopyBest: MaterialButton
    private lateinit var btnStartStop: MaterialButton
    private lateinit var rvEvaluatedStrategies: RecyclerView
    private lateinit var strategyAdapter: StrategyResultAdapter

    private var isDomainsExpanded = false
    private var currentBestCommand: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle("Эволюция стратегий ByeDPI")

        tvNetworkInfo = findViewById(R.id.tvNetworkInfo)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvStatus = findViewById(R.id.tvStatus)
        tvElapsedTime = findViewById(R.id.tvElapsedTime)
        tvGenerationsInfo = findViewById(R.id.tvGenerationsInfo)
        tvTotalTestedInfo = findViewById(R.id.tvTotalTestedInfo)
        tvBestSuccessRateHeader = findViewById(R.id.tvBestSuccessRateHeader)
        tvCurrentTested = findViewById(R.id.tvCurrentTested)
        progressBar = findViewById(R.id.progressBar)
        tvBestCommand = findViewById(R.id.tvBestCommand)
        tvBestSuccessRate = findViewById(R.id.tvBestSuccessRate)
        tvBestSiteDetails = findViewById(R.id.tvBestSiteDetails)
        btnToggleDomains = findViewById(R.id.btnToggleDomains)
        btnApplyBest = findViewById(R.id.btnApplyBest)
        btnCopyBest = findViewById(R.id.btnCopyBest)
        btnStartStop = findViewById(R.id.btnStartStop)
        rvEvaluatedStrategies = findViewById(R.id.rvEvaluatedStrategies)

        strategyAdapter = StrategyResultAdapter(this, onApply = { cmd ->
            applyCommand(cmd)
        })

        rvEvaluatedStrategies.layoutManager = LinearLayoutManager(this)
        rvEvaluatedStrategies.adapter = strategyAdapter

        btnToggleDomains.setOnClickListener {
            isDomainsExpanded = !isDomainsExpanded
            if (isDomainsExpanded) {
                tvBestSiteDetails.visibility = View.VISIBLE
                btnToggleDomains.text = "Скрыть детали по доменам ▲"
            } else {
                tvBestSiteDetails.visibility = View.GONE
                btnToggleDomains.text = "Показать детали по доменам ▼"
            }
        }

        btnStartStop.setOnClickListener {
            if (GeneticEvolutionService.isRunning) {
                GeneticEvolutionService.stop()
            } else {
                GeneticEvolutionService.start(this)
            }
        }

        btnApplyBest.setOnClickListener {
            if (currentBestCommand.isNotBlank()) {
                applyCommand(currentBestCommand)
            }
        }

        btnCopyBest.setOnClickListener {
            if (currentBestCommand.isNotBlank()) {
                ClipboardUtils.copy(this, currentBestCommand, "command")
                Toast.makeText(this, "Команда скопирована", Toast.LENGTH_SHORT).show()
            }
        }

        loadPersistedBestCommand()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GeneticEvolutionService.state.collectLatest { state ->
                    renderState(state)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 101, 0, "Настройки")
            ?.setIcon(R.drawable.baseline_settings_24)
            ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            101 -> {
                startActivity(Intent(this, EvolutionSettingsActivity::class.java))
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyCommand(cmd: String) {
        val prefs = getPreferences()
        prefs.edit(commit = true) { putString("byedpi_cmd_args", cmd) }
        val mode = prefs.mode()
        ServiceManager.restart(this, mode)
        Toast.makeText(this, "Мутировавшая стратегия применена в ByeDPI!", Toast.LENGTH_SHORT).show()
    }

    private fun loadPersistedBestCommand() {
        val prefs = getPreferences()
        val savedCmd = prefs.getString("evolution_last_best_cmd", "") ?: ""
        val savedRate = prefs.getFloat("evolution_last_best_rate", 0f)
        val savedSummary = prefs.getString("evolution_last_best_summary", "") ?: ""

        if (savedCmd.isNotBlank()) {
            displayBestStrategy(savedCmd, savedRate, savedSummary)
        }
    }

    private fun displayBestStrategy(cmd: String, rate: Float, summary: String) {
        currentBestCommand = cmd
        tvBestCommand.text = cmd
        val passPercent = (rate * 100).toInt()
        tvBestSuccessRate.text = "$passPercent%"
        tvBestSuccessRateHeader.text = "$passPercent%"

        val badgeColor = when {
            passPercent >= 80 -> Color.parseColor("#388E3C")
            passPercent >= 40 -> Color.parseColor("#F57C00")
            else -> Color.parseColor("#D32F2F")
        }
        tvBestSuccessRate.setBackgroundColor(badgeColor)

        if (summary.isNotBlank()) {
            btnToggleDomains.visibility = View.VISIBLE
            tvBestSiteDetails.text = summary
            tvBestSiteDetails.visibility = if (isDomainsExpanded) View.VISIBLE else View.GONE
        } else {
            btnToggleDomains.visibility = View.GONE
            tvBestSiteDetails.visibility = View.GONE
        }

        btnApplyBest.isEnabled = true
        btnCopyBest.isEnabled = true
    }

    private fun renderState(state: EvolutionState) {
        btnStartStop.text = if (state.isRunning) "Остановить Эволюцию" else "Запустить Эволюцию"
        btnStartStop.setIconResource(if (state.isRunning) R.drawable.ic_close else R.drawable.ic_power)
        progressBar.visibility = if (state.isRunning) View.VISIBLE else View.GONE

        if (state.isRunning) {
            tvStatusBadge.text = "Запущена"
            tvStatusBadge.setBackgroundColor(Color.parseColor("#388E3C"))
        } else {
            tvStatusBadge.text = "Остановлена"
            tvStatusBadge.setBackgroundColor(Color.parseColor("#757575"))
        }

        val netId = if (state.networkId.isNotBlank()) state.networkId else NetworkDetector.getCurrentNetworkId(this)
        tvNetworkInfo.text = "Сеть: $netId"

        tvStatus.text = if (state.statusMessage.isNotBlank()) state.statusMessage else "Готов к запуску эволюции"
        tvCurrentTested.text = if (state.currentTestedCommand.isNotBlank()) state.currentTestedCommand else "—"

        if (state.bestCommand.isNotBlank()) {
            displayBestStrategy(state.bestCommand, state.bestSuccessRate, state.bestSiteResultsSummary)
        }

        val totalGenStr = if (state.totalGenerations > 0) state.totalGenerations.toString() else "∞"
        tvGenerationsInfo.text = "${state.currentGeneration} / $totalGenStr"
        tvTotalTestedInfo.text = "${state.totalTestedCount} проверено"

        val minutes = state.elapsedTimeSeconds / 60
        val seconds = state.elapsedTimeSeconds % 60
        tvElapsedTime.text = String.format("%02d:%02d", minutes, seconds)

        strategyAdapter.setTestingState(state.isRunning)
        strategyAdapter.updateStrategies(state.evaluatedStrategies)
    }
}
