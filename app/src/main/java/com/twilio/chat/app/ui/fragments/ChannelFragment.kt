package com.twilio.chat.app.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.app.adapters.OnChannelEvent
import com.twilio.chat.app.common.SheetListener
import com.twilio.chat.app.common.extensions.hide
import com.twilio.chat.app.common.extensions.isShowing
import com.twilio.chat.app.common.extensions.lazyActivityViewModel
import com.twilio.chat.app.common.extensions.show
import com.twilio.chat.app.common.injector
import com.twilio.chat.app.ui.ChannelActivity
import kotlinx.android.synthetic.main.fragment_channels_list.*
import kotlinx.android.synthetic.main.view_channel_remove_screen.*

abstract class ChannelFragment : Fragment(), OnChannelEvent {

    private val sheetBehavior by lazy { BottomSheetBehavior.from(removeChannelSheet) }
    private val sheetListener by lazy { SheetListener(sheet_background) {} }

    val channelListViewModel by lazyActivityViewModel {
        injector.createChannelListViewModel(requireActivity().application)
    }

    private fun hideBottomSheet() {
        sheetBehavior.hide()
    }

    private fun showBottomSheet() {
        sheetBehavior.show()
    }

    open fun onBackPressed(): Boolean {
        if (sheetBehavior.isShowing()) {
            hideBottomSheet()
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sheetBehavior.addBottomSheetCallback(sheetListener)
        sheet_background.setOnClickListener { hideBottomSheet() }
        remove_channel_button.setOnClickListener {
            channelListViewModel.selectedChannelSid?.let {
                channelListViewModel.removeChannel(it)
            }
            hideBottomSheet()
        }
        leave_channel_button.setOnClickListener {
            channelListViewModel.selectedChannelSid?.let {
                channelListViewModel.leaveChannel(it)
            }
            hideBottomSheet()
        }
        if (channelListViewModel.selectedChannelSid == null) {
            hideBottomSheet()
        }
    }

    override fun onChannelClicked(channelSid: String) {
        if (channelListViewModel.isChannelJoined(channelSid)) {
            ChannelActivity.start(requireContext(), channelSid)
        } else {
            channelListViewModel.joinChannel(channelSid)
        }
    }

    override fun onChannelLongClicked(channelSid: String) {
        if (channelListViewModel.isChannelJoined(channelSid)) {
            channelListViewModel.selectedChannelSid = channelSid
            hideBottomSheet()
            showBottomSheet()
        }
    }

    override fun onChannelMuteClicked(channelSid: String) {
        if (channelListViewModel.isChannelMuted(channelSid)) {
            channelListViewModel.unmuteChannel(channelSid)
        } else {
            channelListViewModel.muteChannel(channelSid)
        }
    }
}
