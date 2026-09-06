package com.psxtracker.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
    private var specificAlerts: Map<String, StockAlertPreferences.Thresholds> = emptyMap()
    private var allStocks: List<StockQuote> = emptyList()
    private var selectedSector: String? = null

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

        adapter = StockListAdapter(
            onBellClicked = { quote -> showSpecificAlertPicker(quote) },
            getSpecificThresholds = { symbol -> specificAlerts[symbol] }
        )
        binding.recyclerStocks.layoutManager = LinearLayoutManager(this)
        binding.recyclerStocks.adapter = adapter

        setupThresholdChips()
        setupBatteryHint()
        loadSpecificAlerts()

        binding.swipeRefresh.setOnRefreshListener {
            MarketFetchWorker.runNow(this, force = true)
            refreshFromNetwork()
        }

        refreshFromNetwork()
    }

    private fun loadSpecificAlerts() {
        lifecycleScope.launch {
            specificAlerts = StockAlertPreferences.getAllSpecificAlerts(this@MainActivity)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showSpecificAlertPicker(quote: StockQuote) {
        val current = specificAlerts[quote.symbol] ?: StockAlertPreferences.Thresholds(null, null)
        
        val dialogBinding = com.psxtracker.widget.databinding.DialogSpecificAlertBinding.inflate(layoutInflater)
        dialogBinding.tvTitle.text = getString(R.string.title_specific_alert, quote.symbol)
        
        val options = ALL_THRESHOLDS.map { "$it%" }
        val arrayAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        dialogBinding.spinnerUp.adapter = arrayAdapter
        dialogBinding.spinnerDown.adapter = arrayAdapter
        
        dialogBinding.cbUp.isChecked = current.up != null
        dialogBinding.spinnerUp.isEnabled = dialogBinding.cbUp.isChecked
        current.up?.let { dialogBinding.spinnerUp.setSelection(ALL_THRESHOLDS.indexOf(it)) }
        dialogBinding.cbUp.setOnCheckedChangeListener { _, isChecked -> 
            dialogBinding.spinnerUp.isEnabled = isChecked 
        }
        
        dialogBinding.cbDown.isChecked = current.down != null
        dialogBinding.spinnerDown.isEnabled = dialogBinding.cbDown.isChecked
        current.down?.let { dialogBinding.spinnerDown.setSelection(ALL_THRESHOLDS.indexOf(it)) }
        dialogBinding.cbDown.setOnCheckedChangeListener { _, isChecked -> 
            dialogBinding.spinnerDown.isEnabled = isChecked 
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newUp = if (dialogBinding.cbUp.isChecked) ALL_THRESHOLDS[dialogBinding.spinnerUp.selectedItemPosition] else null
                val newDown = if (dialogBinding.cbDown.isChecked) ALL_THRESHOLDS[dialogBinding.spinnerDown.selectedItemPosition] else null
                
                lifecycleScope.launch {
                    StockAlertPreferences.setThresholds(this@MainActivity, quote.symbol, StockAlertPreferences.Thresholds(newUp, newDown))
                    loadSpecificAlerts()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateMarketStatus()
        setupBatteryHint()
    }

    private fun setupThresholdChips() {
        binding.chipGroupThresholdsUp.removeAllViews()
        binding.chipGroupThresholdsDown.removeAllViews()
        lifecycleScope.launch {
            val enabledUp = AlertPreferences.getThresholdsUp(this@MainActivity)
            val enabledDown = AlertPreferences.getThresholdsDown(this@MainActivity)

            val colorUp = ContextCompat.getColor(this@MainActivity, R.color.colorPriceUp)
            val colorDown = ContextCompat.getColor(this@MainActivity, R.color.colorPriceDown)
            val colorWhite = android.graphics.Color.WHITE
            val colorTransparent = android.graphics.Color.TRANSPARENT

            ALL_THRESHOLDS.forEach { pct ->
                // Up chips
                val chipUp = Chip(this@MainActivity).apply {
                    text = "+${pct}%"
                    isCheckable = true
                    isChecked = enabledUp.contains(pct)
                    isCheckedIconVisible = false
                    chipStrokeWidth = 2f
                    
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    chipBackgroundColor = ColorStateList(states, intArrayOf(colorUp, colorTransparent))
                    chipStrokeColor = ColorStateList(states, intArrayOf(colorUp, colorUp))
                    setTextColor(ColorStateList(states, intArrayOf(colorWhite, colorUp)))
                }
                chipUp.setOnCheckedChangeListener { _, _ -> persistThresholds(true) }
                binding.chipGroupThresholdsUp.addView(chipUp)

                // Down chips
                val chipDown = Chip(this@MainActivity).apply {
                    text = "-${pct}%"
                    isCheckable = true
                    isChecked = enabledDown.contains(pct)
                    isCheckedIconVisible = false
                    chipStrokeWidth = 2f

                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    chipBackgroundColor = ColorStateList(states, intArrayOf(colorDown, colorTransparent))
                    chipStrokeColor = ColorStateList(states, intArrayOf(colorDown, colorDown))
                    setTextColor(ColorStateList(states, intArrayOf(colorWhite, colorDown)))
                }
                chipDown.setOnCheckedChangeListener { _, _ -> persistThresholds(false) }
                binding.chipGroupThresholdsDown.addView(chipDown)
            }
        }
    }

    private fun setupSectorChips(stocks: List<StockQuote>) {
        val sectors = stocks.map { it.sector }.distinct().sorted()
        val currentSectorsInGroup = (0 until binding.chipGroupSectors.childCount)
            .map { (binding.chipGroupSectors.getChildAt(it) as Chip).text.toString() }
            .toSet()

        if (sectors.toSet() == currentSectorsInGroup) return

        binding.chipGroupSectors.removeAllViews()
        sectors.forEach { sectorName ->
            val chip = Chip(this).apply {
                text = sectorName
                isCheckable = true
                isChecked = (sectorName == selectedSector)
                setChipBackgroundColorResource(android.R.color.transparent)
                setChipStrokeColorResource(R.color.colorPrimary)
                chipStrokeWidth = 2f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                selectedSector = if (isChecked) sectorName else null
                applyFilters()
            }
            binding.chipGroupSectors.addView(chip)
        }
    }

    private fun applyFilters() {
        val filtered = if (selectedSector == null) {
            allStocks
        } else {
            allStocks.filter { it.sector == selectedSector }
        }
        adapter.submitList(filtered)
    }

    private fun persistThresholds(isUp: Boolean) {
        val group = if (isUp) binding.chipGroupThresholdsUp else binding.chipGroupThresholdsDown
        val selected = (0 until group.childCount)
            .map { group.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString().replace("+", "").replace("-", "").removeSuffix("%").toInt() }
            .toSet()
        lifecycleScope.launch {
            if (isUp) {
                AlertPreferences.setThresholdsUp(this@MainActivity, selected)
            } else {
                AlertPreferences.setThresholdsDown(this@MainActivity, selected)
            }
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
                allStocks = stocks
                setupSectorChips(stocks)
                applyFilters()
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

