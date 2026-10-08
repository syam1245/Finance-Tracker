package com.naseeb.salestracker.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.naseeb.salestracker.R
import com.naseeb.salestracker.data.csv.CsvCodec
import com.naseeb.salestracker.databinding.FragmentSettingsBinding
import com.naseeb.salestracker.databinding.ItemSettingsRowBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesViewModel by activityViewModels()

    private val importCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val context = context?.applicationContext ?: return@registerForActivityResult
            val state = viewModel.uiState.value
            val knownShops = (state.shopList + viewModel.uiState.value.allEntries.map { it.shop })
                .filter { it.isNotBlank() }
                .distinct()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val decoded = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        CsvCodec.decode(reader, knownShops, state.financeOptions)
                    }
                    if (decoded != null) viewModel.importCsvResult(decoded)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        observeUiState()
    }

    private fun setupButtons() {
        binding.btnAddShop.setOnClickListener {
            showAddShopDialog()
        }

        binding.btnAddFinanceOption.setOnClickListener {
            showAddFinanceOptionDialog()
        }

        binding.btnExportCsv.setOnClickListener {
            viewModel.exportCsv()
        }

        binding.btnImportCsv.setOnClickListener {
            importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
        }

        binding.btnWipeData.setOnClickListener {
            confirmWipe()
        }

        binding.btnResetDefaults.setOnClickListener {
            confirmResetDefaults()
        }

        binding.btnFooterEntry.setOnClickListener {
            viewModel.selectTab(0)
        }

        binding.btnFooterData.setOnClickListener {
            viewModel.selectTab(1)
        }
    }

    private fun showAddShopDialog() {
        val context = requireContext()
        val til = TextInputLayout(context).apply {
            hint = getString(R.string.hint_shop_name)
        }
        val et = TextInputEditText(til.context).apply {
            setSingleLine()
        }
        til.addView(et)

        val container = FrameLayout(context).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 4)
            addView(til)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_add_shop_title)
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val name = et.text?.toString() ?: ""
                if (name.isNotBlank()) {
                    viewModel.addShop(name)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showEditShopDialog(currentName: String) {
        val context = requireContext()
        val til = TextInputLayout(context).apply {
            hint = getString(R.string.hint_shop_name)
        }
        val et = TextInputEditText(til.context).apply {
            setText(currentName)
            setSelection(currentName.length)
            setSingleLine()
        }
        til.addView(et)

        val container = FrameLayout(context).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 4)
            addView(til)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_edit_shop_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = et.text?.toString() ?: ""
                if (newName.isNotBlank() && newName != currentName) {
                    viewModel.renameShop(currentName, newName)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteShop(shopName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_shop_message, shopName))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.removeShop(shopName)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddFinanceOptionDialog() {
        val context = requireContext()
        val til = TextInputLayout(context).apply {
            hint = getString(R.string.hint_finance_name)
        }
        val et = TextInputEditText(til.context).apply {
            setSingleLine()
        }
        til.addView(et)

        val container = FrameLayout(context).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 4)
            addView(til)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_add_finance_title)
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val name = et.text?.toString() ?: ""
                if (name.isNotBlank()) {
                    viewModel.addFinanceOption(name)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showEditFinanceOptionDialog(currentName: String) {
        val context = requireContext()
        val til = TextInputLayout(context).apply {
            hint = getString(R.string.hint_finance_name)
        }
        val et = TextInputEditText(til.context).apply {
            setText(currentName)
            setSelection(currentName.length)
            setSingleLine()
        }
        til.addView(et)

        val container = FrameLayout(context).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 4)
            addView(til)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_edit_finance_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = et.text?.toString() ?: ""
                if (newName.isNotBlank() && newName != currentName) {
                    viewModel.renameFinanceOption(currentName, newName)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteFinanceOption(optionName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_finance_message, optionName))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.removeFinanceOption(optionName)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmWipe() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_wipe_title)
            .setMessage(R.string.dialog_wipe_message)
            .setPositiveButton(R.string.action_wipe) { _, _ ->
                viewModel.wipeRecords()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmResetDefaults() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_reset_defaults_title)
            .setMessage(R.string.dialog_reset_defaults_message)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                viewModel.resetSettingsToDefaults()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { it.shopList }
                        .distinctUntilChanged()
                        .collect { shops ->
                            renderShopList(shops)
                        }
                }
                launch {
                    viewModel.uiState
                        .map { it.financeOptions }
                        .distinctUntilChanged()
                        .collect { finances ->
                            renderFinanceList(finances)
                        }
                }
            }
        }
    }

    private fun renderShopList(shops: List<String>) {
        binding.llShopListContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (i in shops.indices) {
            val shop = shops[i]
            val rowBinding = ItemSettingsRowBinding.inflate(inflater, binding.llShopListContainer, false)
            rowBinding.tvItemTitle.text = shop
            rowBinding.btnEditItem.setOnClickListener {
                showEditShopDialog(shop)
            }
            rowBinding.btnDeleteItem.setOnClickListener {
                confirmDeleteShop(shop)
            }
            binding.llShopListContainer.addView(rowBinding.root)

            if (i < shops.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_border))
                }
                binding.llShopListContainer.addView(divider)
            }
        }
    }

    private fun renderFinanceList(options: List<String>) {
        binding.llFinanceListContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (i in options.indices) {
            val opt = options[i]
            val rowBinding = ItemSettingsRowBinding.inflate(inflater, binding.llFinanceListContainer, false)
            rowBinding.tvItemTitle.text = opt
            rowBinding.btnEditItem.setOnClickListener {
                showEditFinanceOptionDialog(opt)
            }
            rowBinding.btnDeleteItem.setOnClickListener {
                confirmDeleteFinanceOption(opt)
            }
            binding.llFinanceListContainer.addView(rowBinding.root)

            if (i < options.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_border))
                }
                binding.llFinanceListContainer.addView(divider)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
