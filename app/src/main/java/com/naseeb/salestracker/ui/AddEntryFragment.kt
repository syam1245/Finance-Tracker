package com.naseeb.salestracker.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.naseeb.salestracker.R
import com.naseeb.salestracker.databinding.FragmentAddEntryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class AddEntryFragment : Fragment() {

    private var _binding: FragmentAddEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesViewModel by activityViewModels()

    private var isUpdatingFromViewModel = false
    private var isScrollingToTop = false
    private var currentRenderedFinanceOptions: List<String> = emptyList()
    private var currentRenderedShops: List<String> = emptyList()
    private val financeEditTextMap = mutableMapOf<String, TextInputEditText>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupShopDropdown()
        setupDatePicker()
        setupFixedTextWatchers()
        setupAutoScroll()
        setupButtons()

        observeEvents()
        observeUiState()
    }

    private fun setupShopDropdown() {
        binding.actvShopName.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as String
            viewModel.updateShop(selected)
            binding.etCdCount.requestFocus()
            smoothScrollToFocusedView(binding.etCdCount)
        }

        binding.actvShopName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingFromViewModel) {
                    viewModel.updateShop(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateShopDropdown(shops: List<String>) {
        if (shops == currentRenderedShops) return
        currentRenderedShops = shops.toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, shops)
        binding.actvShopName.setAdapter(adapter)
    }

    private fun setupDatePicker() {
        val clickListener = View.OnClickListener {
            showDatePicker()
        }
        binding.etEntryDate.setOnClickListener(clickListener)
        binding.tilEntryDate.setOnClickListener(clickListener)
        binding.tilEntryDate.setEndIconOnClickListener(clickListener)
    }

    private fun showDatePicker() {
        val currentDateStr = viewModel.uiState.value.formState.date
        val initialEpoch = try {
            val localDate = LocalDate.parse(currentDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            MaterialDatePicker.todayInUtcMilliseconds()
        }

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.label_entry_date)
            .setSelection(initialEpoch)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            viewModel.updateDate(date)
            binding.actvShopName.requestFocus()
            smoothScrollToFocusedView(binding.actvShopName)
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    private fun setupFixedTextWatchers() {
        fun createWatcher(updateAction: (String) -> Unit) = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingFromViewModel) {
                    updateAction(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etCdCount.addTextChangedListener(createWatcher { viewModel.updateCd(it) })
        binding.etMobileCount.addTextChangedListener(createWatcher { viewModel.updateMobile(it) })
    }

    private fun buildFinanceFields(options: List<String>) {
        if (options == currentRenderedFinanceOptions && binding.llFinanceContainer.childCount > 0) return
        currentRenderedFinanceOptions = options.toList()
        financeEditTextMap.clear()
        binding.llFinanceContainer.removeAllViews()

        val context = requireContext()
        val density = resources.displayMetrics.density

        for (i in options.indices step 2) {
            val rowLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (i > 0) topMargin = (8 * density).toInt()
                }
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            val opt1 = options[i]
            val til1 = createFinanceTil(context, opt1, isRight = false, hasSibling = (i + 1 < options.size))
            val et1 = til1.editText as TextInputEditText
            financeEditTextMap[opt1] = et1
            rowLayout.addView(til1)

            if (i + 1 < options.size) {
                val opt2 = options[i + 1]
                val til2 = createFinanceTil(context, opt2, isRight = true, hasSibling = true)
                val et2 = til2.editText as TextInputEditText
                financeEditTextMap[opt2] = et2
                rowLayout.addView(til2)
            }

            binding.llFinanceContainer.addView(rowLayout)
        }

        setupAutoScroll()
    }

    private fun createFinanceTil(
        context: Context,
        optionName: String,
        isRight: Boolean,
        hasSibling: Boolean
    ): TextInputLayout {
        val density = resources.displayMetrics.density
        val til = TextInputLayout(
            ContextThemeWrapper(context, R.style.Widget_SalesTracker_TextInputLayout_Curved)
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (!isRight && hasSibling) {
                    marginEnd = (8 * density).toInt()
                }
            }
            hint = optionName
        }

        val et = TextInputEditText(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingFromViewModel) {
                    viewModel.updateFinance(optionName, s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        til.addView(et)
        return til
    }

    private fun setupAutoScroll() {
        val inputViews = mutableListOf<View>(
            binding.actvShopName,
            binding.etCdCount,
            binding.etMobileCount
        )
        val financeViews = currentRenderedFinanceOptions.mapNotNull { financeEditTextMap[it] }
        inputViews.addAll(financeViews)

        for (input in inputViews) {
            input.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    smoothScrollToFocusedView(v)
                }
            }
            input.setOnClickListener { v ->
                smoothScrollToFocusedView(v)
            }
        }

        financeViews.lastOrNull()?.let { lastEt ->
            lastEt.imeOptions = EditorInfo.IME_ACTION_DONE
            lastEt.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard()
                    lastEt.clearFocus()
                    true
                } else {
                    false
                }
            }
        }

        binding.nestedScrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (isScrollingToTop) return@addOnLayoutChangeListener
            val focused = binding.nestedScrollView.findFocus()
            if (focused is EditText && _binding != null) {
                val targetView = findParentInputLayout(focused) ?: focused
                if (!targetView.isAttachedToWindow || !binding.nestedScrollView.isAttachedToWindow) return@addOnLayoutChangeListener
                val rect = Rect()
                try {
                    targetView.getDrawingRect(rect)
                    binding.nestedScrollView.offsetDescendantRectToMyCoords(targetView, rect)
                } catch (_: IllegalArgumentException) {
                    return@addOnLayoutChangeListener
                }
                val visibleHeight = binding.nestedScrollView.height
                if (rect.bottom > binding.nestedScrollView.scrollY + visibleHeight ||
                    rect.top < binding.nestedScrollView.scrollY
                ) {
                    smoothScrollToFocusedView(focused)
                }
            }
        }
    }

    private fun findParentInputLayout(view: View): View? {
        var current: View? = view
        while (current != null) {
            if (current is TextInputLayout) {
                return current
            }
            current = current.parent as? View
        }
        return null
    }

    private fun smoothScrollToFocusedView(view: View) {
        val scrollView = _binding?.nestedScrollView ?: return
        scrollView.postDelayed({
            if (_binding == null || isScrollingToTop || !isAdded) return@postDelayed
            val currentScrollView = _binding?.nestedScrollView ?: return@postDelayed
            if (!currentScrollView.isAttachedToWindow || !view.isAttachedToWindow) return@postDelayed
            val targetView = findParentInputLayout(view) ?: view
            if (!targetView.isAttachedToWindow) return@postDelayed
            val rect = Rect()
            try {
                targetView.getDrawingRect(rect)
                currentScrollView.offsetDescendantRectToMyCoords(targetView, rect)
            } catch (_: IllegalArgumentException) {
                return@postDelayed
            }
            val offset = (32 * resources.displayMetrics.density).toInt()
            val targetY = (rect.top - offset).coerceAtLeast(0)
            currentScrollView.smoothScrollTo(0, targetY)
        }, 150)
    }

    private fun setupButtons() {
        binding.btnSubmit.setOnClickListener {
            hideKeyboard()
            activity?.currentFocus?.clearFocus()
            binding.root.clearFocus()
            viewModel.submitEntry()
        }

        binding.btnCancelEdit.setOnClickListener {
            hideKeyboard()
            viewModel.cancelEdit()
        }
    }

    private fun hideKeyboard() {
        val w = activity?.window ?: return
        WindowCompat.getInsetsController(w, binding.root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is UiEvent.ScrollToTop) {
                        isScrollingToTop = true
                        hideKeyboard()
                        activity?.currentFocus?.clearFocus()
                        binding.root.clearFocus()
                        binding.nestedScrollView.clearFocus()
                        binding.nestedScrollView.scrollTo(0, 0)
                        binding.nestedScrollView.post {
                            binding.nestedScrollView.smoothScrollTo(0, 0)
                        }
                        binding.nestedScrollView.postDelayed({
                            if (_binding != null) {
                                binding.nestedScrollView.scrollTo(0, 0)
                                isScrollingToTop = false
                            }
                        }, 250)
                    }
                }
            }
        }
    }

    private data class FormSubState(
        val formState: FormState,
        val shopList: List<String>,
        val financeOptions: List<String>
    )

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { FormSubState(it.formState, it.shopList, it.financeOptions) }
                    .distinctUntilChanged()
                    .collect { subState ->
                        val form = subState.formState
                        isUpdatingFromViewModel = true

                        updateShopDropdown(subState.shopList)
                        buildFinanceFields(subState.financeOptions)

                        setTextIfDifferent(binding.etEntryDate, form.date)
                        setTextIfDifferent(binding.actvShopName, form.shop)
                        setTextIfDifferent(binding.etCdCount, form.cd)
                        setTextIfDifferent(binding.etMobileCount, form.mobile)

                        for (opt in subState.financeOptions) {
                            val strVal = form.financeMap[opt] ?: ""
                            financeEditTextMap[opt]?.let { et ->
                                setTextIfDifferent(et, strVal)
                            }
                        }

                        binding.etTotalCount.setText(form.totalCount.toString())
                        binding.etTotalFinance.setText(form.totalFinance.toString())

                        if (form.isEditing) {
                            binding.btnSubmit.setText(R.string.btn_update_entry)
                            binding.btnSubmit.setIconResource(R.drawable.ic_edit)
                            binding.btnCancelEdit.visibility = View.VISIBLE
                        } else {
                            binding.btnSubmit.setText(R.string.btn_commit_entry)
                            binding.btnSubmit.setIconResource(R.drawable.ic_check)
                            binding.btnCancelEdit.visibility = View.GONE
                        }

                        isUpdatingFromViewModel = false
                    }
            }
        }
    }

    private fun setTextIfDifferent(view: EditText, text: String) {
        if (view.text.toString() != text) {
            if (view is AutoCompleteTextView) {
                view.setText(text, false)
            } else {
                view.setText(text)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
