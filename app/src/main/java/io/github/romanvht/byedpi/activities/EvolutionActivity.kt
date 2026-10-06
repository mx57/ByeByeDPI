package io.github.romanvht.byedpi.activities

import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.genetic.EvolutionState
import io.github.romanvht.byedpi.genetic.GeneticEvolutionService
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EvolutionActivity : BaseActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentTested: TextView
    private lateinit var tvBestCommand: TextView
    private lateinit var tvBestSuccessRate: TextView
    private lateinit var tvGenerationsInfo: TextView
    private lateinit var tvElapsedTime: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnApplyBest: MaterialButton

    private var currentBestCommand: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle("Эволюция стратегий ByeDPI")

        tvStatus = findViewById(R.id.tvStatus)
        tvCurrentTested = findViewById(R.id.tvCurrentTested)
        tvBestCommand = findViewById(R.id.tvBestCommand)
        tvBestSuccessRate = findViewById(R.id.tvBestSuccessRate)
        tvGenerationsInfo = findViewById(R.id.tvGenerationsInfo)
        tvElapsedTime = findViewById(R.id.tvElapsedTime)
        progressBar = findViewById(R.id.progressBar)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnApplyBest = findViewById(R.id.btnApplyBest)

        btnStartStop.setOnClickListener {
            if (GeneticEvolutionService.isRunning) {
                GeneticEvolutionService.stop()
            } else {
                GeneticEvolutionService.start(this)
            }
        }

        btnApplyBest.setOnClickListener {
            if (currentBestCommand.isNotBlank()) {
                val prefs = getPreferences()
                prefs.edit(commit = true) { putString("byedpi_cmd_args", currentBestCommand) }
                val mode = prefs.mode()
                ServiceManager.restart(this, mode)
                Toast.makeText(this, "Мутировавшая стратегия применена!", Toast.LENGTH_SHORT).show()
            }
        }

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

    private fun renderState(state: EvolutionState) {
        btnStartStop.text = if (state.isRunning) "Остановить" else "Запустить Эволюцию"
        progressBar.visibility = if (state.isRunning) View.VISIBLE else View.GONE

        tvStatus.text = if (state.statusMessage.isNotBlank()) state.statusMessage else "Готов к запуску"
        tvCurrentTested.text = if (state.currentTestedCommand.isNotBlank()) "Текущая проверка: ${state.currentTestedCommand}" else "Текущая проверка: -"

        if (state.bestCommand.isNotBlank()) {
            currentBestCommand = state.bestCommand
            tvBestCommand.text = state.bestCommand
            tvBestSuccessRate.text = "Проходимость: ${(state.bestSuccessRate * 100).toInt()}%"
            btnApplyBest.isEnabled = true
        } else {
            btnApplyBest.isEnabled = false
        }

        tvGenerationsInfo.text = "Поколение: ${state.currentGeneration} / ${if (state.totalGenerations > 0) state.totalGenerations else "∞"}"
        val minutes = state.elapsedTimeSeconds / 60
        val seconds = state.elapsedTimeSeconds % 60
        tvElapsedTime.text = String.format("Прошло времени: %02d:%02d", minutes, seconds)
    }
}
