package com.twilio.chat.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.Channel
import com.twilio.chat.app.R
import com.twilio.chat.app.adapters.ChannelFragmentAdapter
import com.twilio.chat.app.common.SheetListener
import com.twilio.chat.app.common.enums.CrashIn
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.common.injector
import com.twilio.chat.app.ui.fragments.ChannelFragment
import com.twilio.chat.app.ui.fragments.PublicChannelFragment
import com.twilio.chat.app.ui.fragments.UserChannelFragment
import kotlinx.android.synthetic.main.activity_channels_list.*
import kotlinx.android.synthetic.main.view_channel_add_screen.*
import kotlinx.android.synthetic.main.view_drawer_header.view.*
import kotlinx.android.synthetic.main.view_user_profile_screen.*
import timber.log.Timber
import java.lang.RuntimeException

class ChannelListActivity : AppCompatActivity() {

    private val addChannelSheet by lazy { BottomSheetBehavior.from(add_channel_sheet) }
    private val userProfileSheet by lazy { BottomSheetBehavior.from(user_profile_sheet) }
    private val sheetListener by lazy { SheetListener(sheet_background) { hideKeyboard() } }
    private val progressDialog: AlertDialog by lazy {
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(R.layout.view_loading_dialog)
            .create()
    }
    private lateinit var toggle: ActionBarDrawerToggle

    val channelsListViewModel by lazyViewModel { injector.createChannelListViewModel(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels_list)

        initViews()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_channel_list, menu)

        val filterMenuItem = menu.findItem(R.id.filter_channels)
        if (channelsListViewModel.channelFilter.isNotEmpty()) {
            filterMenuItem.expandActionView()
        }
        (filterMenuItem.actionView as SearchView).apply {
            queryHint = getString(R.string.channel_filter_hint)
            if (channelsListViewModel.channelFilter.isNotEmpty()) {
                setQuery(channelsListViewModel.channelFilter, false)
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true

                override fun onQueryTextChange(newText: String): Boolean {
                    channelsListViewModel.channelFilter = newText
                    return true
                }

            })
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_channel_add -> switchChannelAddDialog()
        }
        return if (toggle.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        toggle.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        var handled = false
        supportFragmentManager.fragments.forEach {
            if (it is ChannelFragment && it.onBackPressed()) {
                handled = true
            }
        }
        if (addChannelSheet.isShowing()) {
            hideChannelAddSheet()
        } else if (userProfileSheet.isShowing()) {
            userProfileSheet.hide()
        } else if (channel_drawer_layout.isDrawerOpen(GravityCompat.START)) {
            channel_drawer_layout.closeDrawers()
        } else if (!handled) {
            super.onBackPressed()
        }
    }

    private fun initViews() {
        setSupportActionBar(channel_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.activity_title_channels_list)
        toggle = ActionBarDrawerToggle(this, channel_drawer_layout, channel_toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        toggle.isDrawerIndicatorEnabled = true
        toggle.syncState()

        channel_drawer_layout.addDrawerListener(toggle)
        channel_drawer_layout.addDrawerListener(object: DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                hideKeyboard()
            }
        })

        val adapter = ChannelFragmentAdapter(supportFragmentManager)
        adapter.addFragment(UserChannelFragment(), getString(R.string.txt_tab_my_channels))
        adapter.addFragment(PublicChannelFragment(), getString(R.string.txt_tab_public_channels))

        channel_pager.adapter = adapter
        channel_tabs.setupWithViewPager(channel_pager)

        addChannelSheet.addBottomSheetCallback(sheetListener)
        userProfileSheet.addBottomSheetCallback(sheetListener)
        sheet_background.setOnClickListener {
            hideChannelAddSheet()
            userProfileSheet.hide()
        }

        user_profile_update_button.setOnClickListener {
            channelsListViewModel.setFriendlyName(user_profile_friendly_name.text.toString())
        }

        user_profile_cancel_button.setOnClickListener {
            userProfileSheet.hide()
        }

        add_channel_button.setOnClickListener {
            val channelName = channel_name_input.text.toString()
            val channelType = if (channel_type_dropdown.selectedItemPosition == 0)
                Channel.ChannelType.PUBLIC else Channel.ChannelType.PRIVATE
            channelsListViewModel.createChannel(channelName, channelType)
        }

        add_channel_cancel_button.setOnClickListener {
            userProfileSheet.hide()
        }

        drawer_sign_out_button.setOnClickListener {
            Timber.d("Sign out clicked")
            channel_drawer_layout.closeDrawers()
            channelsListViewModel.signOut()
        }

        drawer_java_crash.setOnClickListener {
            throw RuntimeException("Simulated crash in ChannelListActivity.kt")
        }

        drawer_tm_crash.setOnClickListener {
            channelsListViewModel.simulateCrash(CrashIn.TM_CLIENT_CPP)
        }

        drawer_chat_crash.setOnClickListener {
            channelsListViewModel.simulateCrash(CrashIn.CHAT_CLIENT_CPP)
        }

        channel_drawer_menu.getHeaderView(0).drawer_settings_button.setOnClickListener {
            Timber.d("Drawer settings clicked")
            channel_drawer_layout.closeDrawers()
            userProfileSheet.show()
        }

        channelsListViewModel.onChannelCreated.observe(this, {
            hideChannelAddSheet()
        })

        channelsListViewModel.onChannelError.observe(this, { error ->
            channels_list_layout.showSnackbar(getErrorMessage(error))
            hideChannelAddSheet()
            userProfileSheet.hide()
        })

        channelsListViewModel.isDataLoading.observe(this, { showLoading ->
            if (showLoading) {
                progressDialog.show()
            } else {
                progressDialog.hide()
            }
        })

        channelsListViewModel.selfUser.observe(this, { user ->
            Timber.d("Self user received: ${user.friendlyName} ${user.identity}")
            channel_drawer_menu.getHeaderView(0).drawer_member_name.text = user.friendlyName
            channel_drawer_menu.getHeaderView(0).drawer_member_info.text = user.identity
            user_profile_friendly_name.setText(user.friendlyName)
            user_profile_identity.text = user.identity
        })

        channelsListViewModel.onUserUpdated.observe(this, {
            userProfileSheet.hide()
        })

        channelsListViewModel.onSignedOut.observe(this, {
            LoginActivity.start(this)
        })
    }

    private fun switchChannelAddDialog() {
        if (!addChannelSheet.isShowing()) {
            addChannelSheet.show()
        } else {
            hideChannelAddSheet()
        }
    }

    private fun hideChannelAddSheet() {
        channel_name_input.text?.clear()
        addChannelSheet.hide()
    }

    companion object {

        fun start(context: Context) {
            val intent = getStartIntent(context)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        }

        fun getStartIntent(context: Context) =
            Intent(context, ChannelListActivity::class.java)
    }
}
