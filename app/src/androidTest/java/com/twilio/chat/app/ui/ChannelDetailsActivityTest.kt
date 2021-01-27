package com.twilio.chat.app.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.app.R
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.setupTestInjector
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.createTestChannelDetailsViewItem
import com.twilio.chat.app.testUtil.WaitForViewMatcher
import com.twilio.chat.app.testUtil.waitUntilPopupStateChanged
import com.twilio.chat.app.viewModel.ChannelDetailsViewModel
import kotlinx.android.synthetic.main.activity_channel_details.*
import org.hamcrest.CoreMatchers.allOf
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelDetailsActivityTest {

    @get:Rule
    var activityRule: ActivityTestRule<ChannelDetailsActivity> = ActivityTestRule(ChannelDetailsActivity::class.java, false, false)

    private lateinit var channelDetailsViewModel: ChannelDetailsViewModel

    private val channelSid = "channelSid"
    private val memberSid = "memberSid"

    @Before
    fun setUp() {
        activityRule.launchActivity(ChannelDetailsActivity.getStartIntent(InstrumentationRegistry.getInstrumentation().targetContext, channelSid))
        ChatClientWrapper.recreateInstance()
        channelDetailsViewModel = activityRule.activity.channelDetailsViewModel
    }

    @Test
    fun addMemberSuccess() {
        WaitForViewMatcher.performOnView(withId(R.id.add_member_button), click())
        BottomSheetBehavior.from(activityRule.activity.add_member_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(withId(R.id.add_member_id_input), replaceText(memberSid), closeSoftKeyboard())
        WaitForViewMatcher.performOnView(withId(R.id.add_member_id_cancel_button), click())

        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onMemberAdded.value = memberSid
        }
        onView(withText(activityRule.activity.getString(R.string.member_added_message, memberSid)))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun addMemberFailed() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onDetailsError.value = ChatError.MEMBER_ADD_FAILED
        }
        onView(withText(R.string.err_failed_to_add_member))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun renameChannelSuccess() {
        val updatedChannelName = "updatedChannelName"
        val channelAuthor = "UITester"
        val channelCreatedDate = "23 May 2020"
        WaitForViewMatcher.performOnView(withId(R.id.channel_rename_button), scrollTo(), click())
        BottomSheetBehavior.from(activityRule.activity.rename_channel_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(withId(R.id.rename_channel_input), replaceText(updatedChannelName), closeSoftKeyboard())
        WaitForViewMatcher.performOnView(withId(R.id.rename_channel_cancel_button), click())
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.channelDetails.value = createTestChannelDetailsViewItem(channelName = updatedChannelName,
                createdBy = channelAuthor, createdOn = channelCreatedDate)
        }
        WaitForViewMatcher.assertOnView(allOf(
            withId(R.id.channel_details_holder),
            hasDescendant(withText(updatedChannelName)),
            hasDescendant(withText(activityRule.activity.getString(R.string.details_created_by, channelAuthor))),
            hasDescendant(withText(activityRule.activity.getString(R.string.details_created_date, channelCreatedDate)))
        ), matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun renameChannelFailed() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onDetailsError.value = ChatError.CHANNEL_RENAME_FAILED
        }
        onView(withText(R.string.err_failed_to_rename_channel))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun muteChannelSuccess() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.channelDetails.value = createTestChannelDetailsViewItem(isMuted = true)
        }
        WaitForViewMatcher.assertOnView(withText(R.string.details_unmute_channel), matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun unmuteChannelSuccess() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.channelDetails.value = createTestChannelDetailsViewItem(isMuted = false)
        }
        onView(withText(R.string.details_mute_channel))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun muteChannelFailed() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onDetailsError.value = ChatError.CHANNEL_MUTE_FAILED
        }
        onView(withText(R.string.err_failed_to_mute_channels))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun unmuteChannelFailed() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onDetailsError.value = ChatError.CHANNEL_UNMUTE_FAILED
        }
        onView(withText(R.string.err_failed_to_unmute_channel))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun deleteChannelSuccess() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onChannelRemoved.value = Unit
        }
        assert(activityRule.activity.isDestroyed)
    }

    @Test
    fun deleteChannelFailed() {
        UiThreadStatement.runOnUiThread {
            channelDetailsViewModel.onDetailsError.value = ChatError.CHANNEL_REMOVE_FAILED
        }
        onView(withText(R.string.err_failed_to_remove_channel))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupInjector() = setupTestInjector()
    }
}
