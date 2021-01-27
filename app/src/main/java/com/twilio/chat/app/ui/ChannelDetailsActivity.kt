package com.twilio.chat.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.app.R
import com.twilio.chat.app.common.SheetListener
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.common.injector
import com.twilio.chat.app.databinding.ActivityChannelDetailsBinding
import kotlinx.android.synthetic.main.activity_channel_details.*
import kotlinx.android.synthetic.main.view_add_member_screen.*
import kotlinx.android.synthetic.main.view_channel_rename_screen.*
import timber.log.Timber

class ChannelDetailsActivity : AppCompatActivity() {

    private val renameChannelSheet by lazy { BottomSheetBehavior.from(rename_channel_sheet) }
    private val addMemberSheet by lazy { BottomSheetBehavior.from(add_member_sheet) }
    private val sheetListener by lazy { SheetListener(sheet_background) { hideKeyboard() } }
    private val progressDialog: AlertDialog by lazy {
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(R.layout.view_loading_dialog)
            .create()
    }

    val channelDetailsViewModel by lazyViewModel {
        injector.createChannelDetailsViewModel(intent.getStringExtra(EXTRA_CHANNEL_SID)!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil
            .setContentView<ActivityChannelDetailsBinding>(this, R.layout.activity_channel_details)
            .apply {
            lifecycleOwner = this@ChannelDetailsActivity
        }

        initViews(binding)
    }

    override fun onBackPressed() {
        if (renameChannelSheet.isShowing()) {
            renameChannelSheet.hide()
            return
        }
        if (addMemberSheet.isShowing()) {
            addMemberSheet.hide()
            return
        }
        super.onBackPressed()
    }

    private fun initViews(binding: ActivityChannelDetailsBinding) {
        setSupportActionBar(binding.channelDetailsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.channelDetailsToolbar.setNavigationOnClickListener { onBackPressed() }
        renameChannelSheet.addBottomSheetCallback(sheetListener)
        addMemberSheet.addBottomSheetCallback(sheetListener)
        title = getString(R.string.details_title)

        binding.addMemberButton.setOnClickListener {
            Timber.d("Add member clicked")
            add_member_id_input.text?.clear()
            addMemberSheet.show()
        }

        binding.membersListButton.setOnClickListener {
            Timber.d("Show member list clicked")
            MemberListActivity.start(this, channelDetailsViewModel.channelSid)
        }

        binding.channelRenameButton.setOnClickListener {
            Timber.d("Show rename channel popup clicked")
            renameChannelSheet.show()
        }

        binding.channelMuteButton.setOnClickListener {
            Timber.d("Channel mute clicked")
            if (channelDetailsViewModel.isChannelMuted()) {
                channelDetailsViewModel.unmuteChannel()
            } else {
                channelDetailsViewModel.muteChannel()
            }
        }

        binding.channelDeleteButton.setOnClickListener {
            Timber.d("Channel delete clicked")
            channelDetailsViewModel.removeChannel()
        }

        sheet_background.setOnClickListener {
            renameChannelSheet.hide()
            addMemberSheet.hide()
        }

        rename_channel_cancel_button.setOnClickListener {
            renameChannelSheet.hide()
        }

        rename_channel_button.setOnClickListener {
            Timber.d("Channel rename clicked")
            renameChannelSheet.hide()
            channelDetailsViewModel.renameChannel(rename_channel_input.text.toString())
        }

        add_member_id_cancel_button.setOnClickListener {
            addMemberSheet.hide()
        }

        add_member_id_button.setOnClickListener {
            Timber.d("Add member clicked")
            addMemberSheet.hide()
            channelDetailsViewModel.addMember(add_member_id_input.text.toString())
        }

        channelDetailsViewModel.isShowProgress.observe(this, { show ->
            if (show) {
                progressDialog.show()
            } else {
                progressDialog.hide()
            }
        })

        channelDetailsViewModel.channelDetails.observe(this, { channelDetails ->
            Timber.d("Channel details received: $channelDetails")
            binding.details = channelDetails
            rename_channel_input.setText(channelDetails.channelName)
        })

        channelDetailsViewModel.onDetailsError.observe(this, { error ->
            if (error == ChatError.CHANNEL_GET_FAILED) {
                showToast(R.string.err_failed_to_get_channel)
                finish()
            }
            channelLayout.showSnackbar(getErrorMessage(error))
        })

        channelDetailsViewModel.onChannelRemoved.observe(this, {
            ChannelListActivity.start(this)
            finish()
        })

        channelDetailsViewModel.onMemberAdded.observe(this, { identity ->
            channelLayout.showSnackbar(getString(R.string.member_added_message, identity))
        })
    }

    companion object {

        private const val EXTRA_CHANNEL_SID = "ExtraChannelSid"

        fun start(context: Context, channelSid: String) =
            context.startActivity(getStartIntent(context, channelSid))

        fun getStartIntent(context: Context, channelSid: String) =
            Intent(context, ChannelDetailsActivity::class.java).putExtra(EXTRA_CHANNEL_SID, channelSid)
    }
}
