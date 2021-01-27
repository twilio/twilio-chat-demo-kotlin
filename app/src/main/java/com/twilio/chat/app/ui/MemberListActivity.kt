package com.twilio.chat.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.app.R
import com.twilio.chat.app.adapters.MemberListAdapter
import com.twilio.chat.app.common.SheetListener
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.common.injector
import kotlinx.android.synthetic.main.activity_members.*
import kotlinx.android.synthetic.main.view_member_details_screen.*
import timber.log.Timber

class MemberListActivity : AppCompatActivity() {

    private val sheetBehavior by lazy { BottomSheetBehavior.from(member_details_sheet) }
    private val sheetListener by lazy { SheetListener(sheet_background) }

    val memberListViewModel by lazyViewModel {
        injector.createMemberListViewModel(intent.getStringExtra(EXTRA_CHANNEL_SID)!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_members)

        initViews()
    }

    override fun onBackPressed() {
        if (sheetBehavior.isShowing()) {
            sheetBehavior.hide()
            return
        }
        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_member_list, menu)

        val filterMenuItem = menu.findItem(R.id.filter_members)
        if (memberListViewModel.memberFilter.isNotEmpty()) {
            filterMenuItem.expandActionView()
        }
        (filterMenuItem.actionView as SearchView).apply {
            queryHint = getString(R.string.member_filter_hint)
            if (memberListViewModel.memberFilter.isNotEmpty()) {
                setQuery(memberListViewModel.memberFilter, false)
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true

                override fun onQueryTextChange(newText: String): Boolean {
                    memberListViewModel.memberFilter = newText
                    return true
                }
            })
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.filter_members -> sheetBehavior.hide()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        setSupportActionBar(channel_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        channel_toolbar.setNavigationOnClickListener { onBackPressed() }
        sheetBehavior.addBottomSheetCallback(sheetListener)
        title = getString(R.string.member_title)
        val adapter = MemberListAdapter { member ->
            Timber.d("Member clicked: $member")
            memberListViewModel.selectedMemberIdentity = member.identity
            member_details_name.text = member.friendlyName
            member_details_status.setText(if (member.isOnline) R.string.member_online else R.string.member_offline)
            sheetBehavior.show()
        }

        memberRefresh.setOnRefreshListener { memberListViewModel.getChannelMembers() }
        memberList.adapter = adapter

        sheet_background.setOnClickListener {
            sheetBehavior.hide()
        }

        member_details_remove.setOnClickListener {
            Timber.d("Member remove clicked: ${memberListViewModel.selectedMemberIdentity}")
            memberListViewModel.selectedMemberIdentity?.let { identity ->
                memberListViewModel.removeMember(identity)
                sheetBehavior.hide()
            }
        }

        memberListViewModel.membersList.observe(this, { members ->
            Timber.d("Members received: $members")
            adapter.members = members
            memberRefresh.isRefreshing = false
        })
        memberListViewModel.onMemberError.observe(this, { error ->
            channelLayout.showSnackbar(getErrorMessage(error))
        })
    }

    companion object {

        private const val EXTRA_CHANNEL_SID = "ExtraChannelSid"

        fun start(context: Context, channelSid: String) =
            context.startActivity(getStartIntent(context, channelSid))

        fun getStartIntent(context: Context, channelSid: String) =
            Intent(context, MemberListActivity::class.java).putExtra(EXTRA_CHANNEL_SID, channelSid)
    }
}
