package com.twilio.chat.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.twilio.chat.app.data.models.MemberListViewItem
import com.twilio.chat.app.databinding.RowMemberItemBinding
import kotlin.properties.Delegates

class MemberListAdapter(private val onMemberClicked: (member: MemberListViewItem) -> Unit) : RecyclerView.Adapter<MemberListAdapter.ViewHolder>() {

    var members: List<MemberListViewItem> by Delegates.observable(emptyList()) { _, old, new ->
        DiffUtil.calculateDiff(ChannelDiff(old, new)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowMemberItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = members.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.member = members[position]
        holder.binding.memberItem.setOnClickListener {
            holder.binding.member?.let { onMemberClicked(it) }
        }
    }

    class ViewHolder(val binding: RowMemberItemBinding) : RecyclerView.ViewHolder(binding.root)

    class ChannelDiff(private val oldItems: List<MemberListViewItem>,
                            private val newItems: List<MemberListViewItem>
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
