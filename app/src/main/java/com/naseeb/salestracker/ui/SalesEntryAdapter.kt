package com.naseeb.salestracker.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naseeb.salestracker.R
import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.databinding.ItemSalesEntryBinding

class SalesEntryAdapter(
    private val onEditClick: (SalesEntry) -> Unit,
    private val onDeleteClick: (SalesEntry) -> Unit
) : ListAdapter<SalesEntry, SalesEntryAdapter.ViewHolder>(DiffCallback) {

    var financeOptions: List<String> = emptyList()

    private var colorWhite: Int? = null
    private var colorAlt: Int? = null
    private var colWidthPx: Int? = null

    class ViewHolder(val binding: ItemSalesEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSalesEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val holder = ViewHolder(binding)

        binding.btnEdit.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < itemCount) {
                onEditClick(getItem(pos))
            }
        }

        binding.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < itemCount) {
                onDeleteClick(getItem(pos))
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val binding = holder.binding
        val context = holder.itemView.context

        // Alternating row background with cached color ints
        val white = colorWhite ?: ContextCompat.getColor(context, R.color.white).also { colorWhite = it }
        val alt = colorAlt ?: ContextCompat.getColor(context, R.color.table_row_alt).also { colorAlt = it }
        binding.rowLayout.setBackgroundColor(if (position % 2 == 0) white else alt)

        binding.tvDate.text = entry.date
        binding.tvShop.text = entry.shop
        binding.tvCd.text = entry.cd.toString()
        binding.tvMobile.text = entry.mobile.toString()
        binding.tvTotalCount.text = entry.totalCount.toString()

        // Efficient view recycling for dynamic finance columns (Zero destructive layout thrashing)
        val currentChildCount = binding.llRowFinanceColumns.childCount
        val requiredCount = financeOptions.size
        val colWidth = colWidthPx ?: (80 * context.resources.displayMetrics.density).toInt().also { colWidthPx = it }

        if (currentChildCount < requiredCount) {
            for (i in currentChildCount until requiredCount) {
                val tv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.text_main))
                    textSize = 13f
                }
                binding.llRowFinanceColumns.addView(tv)
            }
        } else if (currentChildCount > requiredCount) {
            binding.llRowFinanceColumns.removeViews(requiredCount, currentChildCount - requiredCount)
        }

        for (i in financeOptions.indices) {
            val tv = binding.llRowFinanceColumns.getChildAt(i) as TextView
            tv.text = entry.getFinanceValue(financeOptions[i]).toString()
        }

        binding.tvTotalFinance.text = entry.totalFinance.toString()
    }

    private object DiffCallback : DiffUtil.ItemCallback<SalesEntry>() {
        override fun areItemsTheSame(oldItem: SalesEntry, newItem: SalesEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SalesEntry, newItem: SalesEntry): Boolean {
            return oldItem == newItem
        }
    }
}
