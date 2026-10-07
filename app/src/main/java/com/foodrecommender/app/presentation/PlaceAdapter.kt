package com.foodrecommender.app.presentation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.foodrecommender.app.databinding.ItemPlaceBinding
import com.foodrecommender.core.AnalyzedPlace

class PlaceAdapter : RecyclerView.Adapter<PlaceAdapter.Holder>() {
    private val items = mutableListOf<AnalyzedPlace>()

    fun submit(results: List<AnalyzedPlace>) {
        items.clear()
        items.addAll(results)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.placeName.text = item.place.name
        holder.binding.placeSummary.text = item.summary
    }

    class Holder(val binding: ItemPlaceBinding) : RecyclerView.ViewHolder(binding.root)
}
