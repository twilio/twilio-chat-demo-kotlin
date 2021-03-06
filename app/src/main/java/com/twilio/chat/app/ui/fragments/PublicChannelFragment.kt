package com.twilio.chat.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.twilio.chat.app.R
import com.twilio.chat.app.adapters.ChannelListAdapter
import kotlinx.android.synthetic.main.fragment_channels_list.*

class PublicChannelFragment : ChannelFragment() {

    private val adapter = ChannelListAdapter(this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_channels_list, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelRefresh.setOnRefreshListener { channelListViewModel.getPublicChannels() }
        channelList.adapter = adapter
        channelListViewModel.publicChannelItems.observe(viewLifecycleOwner, {
            adapter.channels = it
            channelRefresh.isRefreshing = false
        })
    }
}
