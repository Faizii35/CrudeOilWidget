package com.psxtracker.widget

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StockListAdapter(
    private val onBellClicked: (StockQuote) -> Unit,
    private val getSpecificThreshold: (String) -> Int?
) : RecyclerView.Adapter<StockListAdapter.ViewHolder>() {

    private var items: List<StockQuote> = emptyList()

    fun submitList(newItems: List<StockQuote>) {
        // Sort by size of move first so the biggest movers surface at the top.
        items = newItems.sortedByDescending { kotlin.math.abs(it.changePercent) }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val symbol: TextView = view.findViewById(R.id.tv_symbol)
        val sector: TextView = view.findViewById(R.id.tv_sector)
        val price: TextView = view.findViewById(R.id.tv_price)
        val change: TextView = view.findViewById(R.id.tv_change)
        val bell: android.widget.ImageView = view.findViewById(R.id.iv_bell)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val indexTag = when {
            item.isKmi30 && item.isKmiAllShare -> "KMI30 \u00b7 KMI All Shr"
            item.isKmi30 -> "KMI30"
            else -> "KMI All Shr"
        }
        holder.symbol.text = item.symbol
        holder.sector.text = "${item.sector} \u00b7 $indexTag".trim()
        holder.price.text = item.priceFormatted
        holder.change.text = item.changeFormatted
        
        val specificThreshold = getSpecificThreshold(item.symbol)
        if (specificThreshold != null) {
            holder.bell.setColorFilter(Color.parseColor("#0FA968"))
            holder.bell.alpha = 1.0f
        } else {
            holder.bell.setColorFilter(Color.parseColor("#8B949E"))
            holder.bell.alpha = 0.5f
        }
        
        holder.bell.setOnClickListener { onBellClicked(item) }

        val color = when {
            item.change > 0 -> Color.parseColor("#4CAF50")
            item.change < 0 -> Color.parseColor("#F44336")
            else -> Color.parseColor("#BDBDBD")
        }
        holder.change.setTextColor(color)
    }

    override fun getItemCount(): Int = items.size
}
