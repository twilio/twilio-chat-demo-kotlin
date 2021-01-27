package com.twilio.chat.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.twilio.chat.app.data.models.ChannelListViewItem
import com.twilio.chat.app.databinding.RowChannelItemBinding
import kotlin.properties.Delegates

class ChannelListAdapter(private val callback: OnChannelEvent) : RecyclerView.Adapter<ChannelListAdapter.ViewHolder>() {

    var channels: List<ChannelListViewItem> by Delegates.observable(emptyList()) { _, old, new ->
        DiffUtil.calculateDiff(ChannelDiff(old, new)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowChannelItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.channel = channels[position]
        holder.binding.channelItem.setOnClickListener {
            holder.binding.channel?.sid?.let { callback.onChannelClicked(it) }
        }
        holder.binding.channelItem.setOnLongClickListener {
            holder.binding.channel?.sid?.let { callback.onChannelLongClicked(it) }
            true
        }
        holder.binding.channelMute.setOnClickListener {
            holder.binding.channel?.sid?.let { callback.onChannelMuteClicked(it) }
        }
    }

    class ViewHolder(val binding: RowChannelItemBinding) : RecyclerView.ViewHolder(binding.root)

    class ChannelDiff(private val oldItems: List<ChannelListViewItem>,
                            private val newItems: List<ChannelListViewItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].sid == newItems[newItemPosition].sid
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}

interface OnChannelEvent {

    fun onChannelClicked(channelSid: String)

    fun onChannelLongClicked(channelSid: String)

    fun onChannelMuteClicked(channelSid: String)
}
