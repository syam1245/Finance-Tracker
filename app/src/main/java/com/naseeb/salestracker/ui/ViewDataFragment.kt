package com.naseeb.salestracker.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.naseeb.salestracker.R
import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.databinding.FragmentViewDataBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ViewDataFragment : Fragment() {

    private var _binding: FragmentViewDataBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesViewModel by activityViewModels()
    private lateinit var adapter: SalesEntryAdapter

    private var currentFilterShops: List<String> = emptyList()
    private var currentFinanceHeaderOptions: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilters()
        setupFooterNavigation()

        observeUiState()
    }

    private fun setupFooterNavigation() {
        binding.btnFooterEntry.setOnClickListener {
            viewModel.selectTab(0)
        }
        binding.btnFooterSettings.setOnClickListener {
            viewModel.selectTab(2)
        }
    }

    private fun setupRecyclerView() {
        adapter = SalesEntryAdapter(
            onEditClick = { entry -> viewModel.editEntry(entry) },
            onDeleteClick = { entry -> confirmDelete(entry) }
        )
        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter
    }

    private fun setupFilters() {
        binding.actvFilterShop.setOnItemClickListener { _, _, position, _ ->
            val filterOptions = listOf(getString(R.string.filter_all_stores)) + currentFilterShops
            val selected = if (position == 0) "ALL" else filterOptions.getOrElse(position) { "ALL" }
            viewModel.setShopFilter(selected)
        }

        val datePickerClickListener = View.OnClickListener {
            showDateRangePicker()
        }
        binding.etFilterDateRange.setOnClickListener(datePickerClickListener)
        binding.tilFilterDateRange.setOnClickListener(datePickerClickListener)

        binding.tilFilterDateRange.setEndIconOnClickListener {
            if (viewModel.uiState.value.startDateFilter != null || viewModel.uiState.value.endDateFilter != null) {
                viewModel.clearDateRangeFilter()
            } else {
                showDateRangePicker()
            }
        }
    }

    private fun updateShopFilterDropdown(shops: List<String>, currentSelected: String) {
        if (shops == currentFilterShops) return
        currentFilterShops = shops.toList()
        val filterOptions = listOf(getString(R.string.filter_all_stores)) + shops
        val dropdownAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, filterOptions)
        binding.actvFilterShop.setAdapter(dropdownAdapter)

        val displayText = if (currentSelected == "ALL") filterOptions[0] else currentSelected
        binding.actvFilterShop.setText(displayText, false)
    }

    private fun updateFinanceHeaders(options: List<String>) {
        if (options == currentFinanceHeaderOptions && binding.llHeaderFinanceColumns.childCount > 0) return
        currentFinanceHeaderOptions = options.toList()
        binding.llHeaderFinanceColumns.removeAllViews()

        val context = requireContext()
        val density = resources.displayMetrics.density
        val colWidth = (80 * density).toInt()

        for (opt in options) {
            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.text_label))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                text = opt.uppercase()
            }
            binding.llHeaderFinanceColumns.addView(tv)
        }
    }

    private fun showDateRangePicker() {
        val currentState = viewModel.uiState.value
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.label_date_range_picker_title)

        val startStr = currentState.startDateFilter
        val endStr = currentState.endDateFilter

        if (!startStr.isNullOrBlank() && !endStr.isNullOrBlank()) {
            try {
                val startMilli = LocalDate.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endMilli = LocalDate.parse(endStr, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                builder.setSelection(Pair(startMilli, endMilli))
            } catch (_: Exception) {
            }
        }

        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selectionPair ->
            if (selectionPair?.first != null && selectionPair.second != null) {
                val startDate = Instant.ofEpochMilli(selectionPair.first)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                val endDate = Instant.ofEpochMilli(selectionPair.second)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                viewModel.setDateRangeFilter(startDate, endDate)
            }
        }
        datePicker.show(childFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun confirmDelete(entry: SalesEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteEntry(entry.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private data class ViewDataSubState(
        val entries: List<SalesEntry>,
        val shopList: List<String>,
        val selectedShopFilter: String,
        val financeOptions: List<String>,
        val startDateFilter: String?,
        val endDateFilter: String?,
        val dateRangeDisplayText: String
    )

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map {
                        ViewDataSubState(
                            entries = it.entries,
                            shopList = it.shopList,
                            selectedShopFilter = it.selectedShopFilter,
                            financeOptions = it.financeOptions,
                            startDateFilter = it.startDateFilter,
                            endDateFilter = it.endDateFilter,
                            dateRangeDisplayText = it.dateRangeDisplayText
                        )
                    }
                    .distinctUntilChanged()
                    .collect { state ->
                        updateShopFilterDropdown(state.shopList, state.selectedShopFilter)
                        updateFinanceHeaders(state.financeOptions)

                        adapter.financeOptions = state.financeOptions
                        adapter.submitList(state.entries)

                        if (state.entries.isEmpty()) {
                            binding.tvEmptyState.visibility = View.VISIBLE
                            binding.rvRecords.visibility = View.GONE
                        } else {
                            binding.tvEmptyState.visibility = View.GONE
                            binding.rvRecords.visibility = View.VISIBLE
                        }

                        val dateText = state.dateRangeDisplayText
                        if (binding.etFilterDateRange.text.toString() != dateText) {
                            binding.etFilterDateRange.setText(dateText)
                        }

                        if (state.startDateFilter != null || state.endDateFilter != null) {
                            binding.tilFilterDateRange.endIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                            binding.tilFilterDateRange.setEndIconTintList(ContextCompat.getColorStateList(requireContext(), R.color.danger))
                            binding.tilFilterDateRange.setEndIconContentDescription(R.string.action_clear_date_range)
                        } else {
                            binding.tilFilterDateRange.endIconDrawable = null
                            binding.tilFilterDateRange.setEndIconTintList(null)
                            binding.tilFilterDateRange.setEndIconContentDescription(null)
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
