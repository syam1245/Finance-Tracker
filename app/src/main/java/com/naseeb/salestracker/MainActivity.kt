package com.naseeb.salestracker

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.naseeb.salestracker.databinding.ActivityMainBinding
import com.naseeb.salestracker.data.csv.CsvCodec
import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.ui.AddEntryFragment
import com.naseeb.salestracker.ui.SalesViewModel
import com.naseeb.salestracker.ui.ToastType
import com.naseeb.salestracker.ui.UiEvent
import com.naseeb.salestracker.ui.ViewDataFragment
import com.naseeb.salestracker.ui.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SalesViewModel by viewModels()

    private data class PendingCsvExport(
        val entries: List<SalesEntry>,
        val financeOptions: List<String>
    )

    private var pendingCsvExport: PendingCsvExport? = null

    private var alertDismissRunnable: Runnable? = null
    private val alertHandler = Handler(Looper.getMainLooper())

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val export = pendingCsvExport
            if (export != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val output = contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Unable to open destination")
                        output.use { stream ->
                            OutputStreamWriter(stream, StandardCharsets.UTF_8).buffered().use { writer ->
                                CsvCodec.encode(export.entries, export.financeOptions, writer)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            showCenteredAlert("CSV dataset exported successfully!", ToastType.SUCCESS)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showCenteredAlert("Failed to export CSV file.", ToastType.DANGER)
                        }
                    }
                }
            } else {
                showCenteredAlert("Export was interrupted. Please try again.", ToastType.DANGER)
            }
        }
        pendingCsvExport = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val barsAndIme = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(barsAndIme.left, barsAndIme.top, barsAndIme.right, barsAndIme.bottom)
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            binding.main,
            object : androidx.core.view.WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<androidx.core.view.WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val barsAndIme = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    )
                    binding.main.setPadding(
                        barsAndIme.left,
                        barsAndIme.top,
                        barsAndIme.right,
                        barsAndIme.bottom
                    )
                    return insets
                }
            }
        )

        setupViewPagerAndTabs()
        observeEvents()
        observeUiState()
    }

    private fun setupViewPagerAndTabs() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter

        val tabConfigs = listOf(
            Triple(R.string.tab_add_entry, R.drawable.ic_add, R.color.accent),
            Triple(R.string.tab_view_data, R.drawable.ic_data, R.color.primary),
            Triple(R.string.tab_settings, R.drawable.ic_settings, R.color.black)
        )

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val customView = layoutInflater.inflate(R.layout.custom_tab_item, null)
            val ivIcon = customView.findViewById<ImageView>(R.id.ivTabIcon)
            val tvText = customView.findViewById<TextView>(R.id.tvTabText)

            val (titleRes, iconRes, colorRes) = tabConfigs[position]
            tvText.text = getString(titleRes)
            ivIcon.setImageResource(iconRes)
            ivIcon.imageTintList = ContextCompat.getColorStateList(this, colorRes)

            tab.customView = customView
        }.attach()

        fun updateTabStyles() {
            for (i in 0 until binding.tabLayout.tabCount) {
                val tab = binding.tabLayout.getTabAt(i)
                val customView = tab?.customView ?: continue
                val tvText = customView.findViewById<TextView>(R.id.tvTabText)
                val isSelected = tab.isSelected
                tvText.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (isSelected) R.color.text_main else R.color.text_muted
                    )
                )
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { viewModel.selectTab(it) }
                updateTabStyles()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                updateTabStyles()
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        updateTabStyles()
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.ShowToast -> {
                            showCenteredAlert(event.message, event.type)
                        }
                        is UiEvent.SwitchTab -> {
                            binding.viewPager.currentItem = event.tabIndex
                        }
                        is UiEvent.ExportCsv -> {
                            pendingCsvExport = PendingCsvExport(event.entries, event.financeOptions)
                            createCsvLauncher.launch(event.filename)
                        }
                        is UiEvent.ScrollToTop -> {
                            // Handled by AddEntryFragment
                        }
                    }
                }
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.activeTab }
                    .distinctUntilChanged()
                    .collect { activeTab ->
                        if (binding.viewPager.currentItem != activeTab) {
                            binding.viewPager.currentItem = activeTab
                        }
                    }
            }
        }
    }

    private fun showCenteredAlert(message: String, type: ToastType) {
        alertDismissRunnable?.let { alertHandler.removeCallbacks(it) }

        val bgColor = when (type) {
            ToastType.SUCCESS -> Color.parseColor("#10B981") // Emerald Green
            ToastType.WARNING -> Color.parseColor("#F59E0B") // Amber Warning
            ToastType.DANGER -> Color.parseColor("#F43F5E")  // Rose Red
        }

        val iconRes = when (type) {
            ToastType.SUCCESS -> R.drawable.ic_check
            ToastType.WARNING -> R.drawable.ic_warning
            ToastType.DANGER -> R.drawable.ic_delete
        }

        binding.layoutAlertBg.setBackgroundColor(bgColor)
        binding.ivAlertIcon.setImageResource(iconRes)
        binding.tvAlertMessage.text = message

        binding.cardCenteredAlert.alpha = 0f
        binding.cardCenteredAlert.visibility = View.VISIBLE
        binding.cardCenteredAlert.animate()
            .alpha(1f)
            .setDuration(180)
            .start()

        binding.cardCenteredAlert.setOnClickListener {
            dismissCenteredAlert()
        }

        val runnable = Runnable {
            dismissCenteredAlert()
        }
        alertDismissRunnable = runnable
        alertHandler.postDelayed(runnable, 2800)
    }

    private fun dismissCenteredAlert() {
        alertDismissRunnable?.let { alertHandler.removeCallbacks(it) }
        binding.cardCenteredAlert.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                binding.cardCenteredAlert.visibility = View.GONE
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        alertDismissRunnable?.let { alertHandler.removeCallbacks(it) }
        alertHandler.removeCallbacksAndMessages(null)
    }

    private inner class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AddEntryFragment()
                1 -> ViewDataFragment()
                2 -> SettingsFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
