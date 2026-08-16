package com.psxtracker.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.psxtracker.widget.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: StockListAdapter

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msg = if (granted) "Notifications enabled" else "Notifications disabled - alerts won't show."
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "PSX \u00b7 KMI Tracker"

        NotificationHelper.createChannel(this)
        requestNotifPermission()
        MarketFetchWorker.schedule(this)

        adapter = StockListAdapter()
        binding.recyclerStocks.layoutManager = LinearLayoutManager(this)
        binding.recyclerStocks.adapter = adapter

        setupThresholdChips()
        setupBatteryHint()

        binding.swipeRefresh.setOnRefreshListener {
            MarketFetchWorker.runNow(this, force = true)
            refreshFromNetwork()
        }

        refreshFromNetwork()
    }

    override fun onResume() {
        super.onResume()
        updateMarketStatus()
        setupBatteryHint()
    }

    private fun setupThresholdChips() {
        binding.chipGroupThresholds.removeAllViews()
        lifecycleScope.launch {
            val enabled = AlertPreferences.getThresholds(this@MainActivity)
            ALL_THRESHOLDS.forEach { pct ->
                val chip = Chip(this@MainActivity).apply {
                    text = "${pct}%"
                    isCheckable = true
                    isChecked = enabled.contains(pct)
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 2f
                    setTextColor(android.graphics.Color.WHITE)
                }
                chip.setOnCheckedChangeListener { _, _ -> persistThresholds() }
                binding.chipGroupThresholds.addView(chip)
            }
        }
    }

    private fun persistThresholds() {
        val selected = (0 until binding.chipGroupThresholds.childCount)
            .map { binding.chipGroupThresholds.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString().removeSuffix("%").toInt() }
            .toSet()
        lifecycleScope.launch {
            AlertPreferences.setThresholds(this@MainActivity, selected)
        }
    }

    private fun setupBatteryHint() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryHint.visibility = if (ignoring) View.GONE else View.VISIBLE
        binding.tvBatteryHint.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun updateMarketStatus() {
        val open = MarketHours.isMarketOpen()
        binding.tvMarketStatus.text = if (open)
            "Market OPEN \u00b7 checking every ~15 min"
        else
            "Market CLOSED \u00b7 background checks paused until next session"
    }

    private fun refreshFromNetwork() {
        updateMarketStatus()
        lifecycleScope.launch {
            val indicesResult = MarketRepository.fetchTrackedIndices()
            indicesResult.getOrNull()?.let { indices ->
                val kmi30 = indices.firstOrNull { it.name == "KMI30" }
                val kmiAll = indices.firstOrNull { it.name == "KMIALLSHR" }
                binding.tvKmi30Summary.text = "KMI30: ${kmi30?.let { String.format("%,.2f", it.current) } ?: "\u2014"}"
                binding.tvKmiallSummary.text = "KMI All Shr: ${kmiAll?.let { String.format("%,.2f", it.current) } ?: "\u2014"}"
            }

            val stocksResult = MarketRepository.fetchTrackedStocks()
            stocksResult.onSuccess { stocks ->
                adapter.submitList(stocks)
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Couldn't load data: ${err.message}", Toast.LENGTH_LONG).show()
            }

            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
