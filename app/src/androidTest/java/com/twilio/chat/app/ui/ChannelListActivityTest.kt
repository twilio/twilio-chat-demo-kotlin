package com.twilio.chat.app.ui

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.rule.ActivityTestRule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.Channel
import com.twilio.chat.app.*
import com.twilio.chat.app.adapters.ChannelListAdapter
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.asChannelListViewItems
import com.twilio.chat.app.common.setupTestInjector
import com.twilio.chat.app.common.testInjector
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.models.ChannelListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.testUtil.*
import com.twilio.chat.app.viewModel.ChannelListViewModel
import kotlinx.android.synthetic.main.activity_channels_list.*
import kotlinx.android.synthetic.main.fragment_channels_list.*
import kotlinx.coroutines.flow.flowOf
import org.hamcrest.CoreMatchers.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelListActivityTest {

    @get:Rule
    var activityRule: ActivityTestRule<ChannelListActivity> = ActivityTestRule(ChannelListActivity::class.java)

    private lateinit var channelListViewModel: ChannelListViewModel
    private val userChannelCount = 5
    private val publicChannelCount = 3

    @Before
    fun setUp() {
        ChatClientWrapper.recreateInstance()
        channelListViewModel = activityRule.activity.channelsListViewModel
    }

    @Test
    fun userChannelsVisible() {
        val channels = getMockedChannels(userChannelCount, MOCK_USER_CHANNEL_NAME)
        updateAndValidateUserChannels(channels)
    }

    @Test
    fun publicChannelsVisible() {
        switchChannelTab(activityRule.activity.getString(R.string.txt_tab_public_channels))
        val channels = getMockedChannels(publicChannelCount, MOCK_PUBLIC_CHANNEL_NAME)
        updateAndValidatePublicChannels(channels)
    }

    @Test
    fun userChannelsChanged() {
        // Given a list of user channels
        val channels: MutableList<ChannelDataItem> = getMockedChannels(publicChannelCount, MOCK_USER_CHANNEL_NAME)
        updateAndValidateUserChannels(channels)

        // .. when new channel is added
        val newChannel = createTestChannelDataItem(
            friendlyName = "New User Channel",
            membersCount = 10,
            messagesCount = 99,
            unconsumedMessagesCount = 789
        )
        channels.add(newChannel)
        // .. then channel list is updated
        updateAndValidateUserChannels(channels)

        // .. when a channel is removed
        channels.remove(newChannel)
        // .. then channel list is updated
        updateAndValidateUserChannels(channels)
    }

    @Test
    fun publicChannelsChanged() {
        // Given a list of public channels
        switchChannelTab(activityRule.activity.getString(R.string.txt_tab_public_channels))
        val channels: MutableList<ChannelDataItem> = getMockedChannels(publicChannelCount, MOCK_PUBLIC_CHANNEL_NAME)
        updateAndValidatePublicChannels(channels)

        // .. when new channel is added
        val newChannel = createTestChannelDataItem(
            friendlyName = "New Public Channel",
            membersCount = 10,
            messagesCount = 99,
            unconsumedMessagesCount = 789
        )
        channels.add(newChannel)
        // .. then channel list is updated
        updateAndValidatePublicChannels(channels)

        // .. when a channel is removed
        channels.remove(newChannel)
        // .. then channel list is updated
        updateAndValidatePublicChannels(channels)
    }

    @Test
    fun userChannelAdded() {
        val spinnerValues = activityRule.activity.resources.getStringArray(R.array.channel_type)
        WaitForViewMatcher.performOnView(withId(R.id.show_channel_add), click())
        verifyAddChannelPopupVisible()

        BottomSheetBehavior.from(activityRule.activity.add_channel_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(withId(R.id.channel_type_dropdown), click())
        WaitForViewMatcher.performOnView(withText(spinnerValues[1]), click())
        WaitForViewMatcher.performOnView(withId(R.id.channel_name_input), replaceText(MOCK_USER_CHANNEL_NAME), closeKeyboard())
        WaitForViewMatcher.performOnView(withId(R.id.add_channel_button), click())
    }

    @Test
    fun publicChannelAdded() {
        WaitForViewMatcher.performOnView(withId(R.id.show_channel_add), click())
        verifyAddChannelPopupVisible()

        BottomSheetBehavior.from(activityRule.activity.add_channel_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(withId(R.id.channel_name_input), replaceText(MOCK_PUBLIC_CHANNEL_NAME), closeKeyboard())
        WaitForViewMatcher.performOnView(withId(R.id.add_channel_button), click())
    }

    @Test
    fun userChannelRemoved() {
        val channels = getMockedChannels(userChannelCount, MOCK_USER_CHANNEL_NAME, Channel.ChannelStatus.JOINED)
        val channelToRemove = channels[channels.size - 1]
        updateAndValidateUserChannels(channels)

        WaitForViewMatcher.performOnView(allOf(
                withId(R.id.channelItem),
                hasDescendant(allOf(
                    withId(R.id.channelName),
                    withText(channelToRemove.friendlyName)
                ))
            ), longClick())

        // Espresso click() will work while the bottom sheet is animating and not trigger the click listener
        // UI tests should be run with disabled animations otherwise we have to sleep here to trigger the listener
        BottomSheetBehavior.from(activityRule.activity.removeChannelSheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.remove_channel_button),
            isDisplayed()
        ), click())

        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelRemoved.verifyCalled()
            channels.remove(channelToRemove)
        }
        updateAndValidateUserChannels(channels)
    }

    @Test
    fun userChannelLeft() {
        val channels = getMockedChannels(userChannelCount, MOCK_USER_CHANNEL_NAME, Channel.ChannelStatus.JOINED)
        val channelToRemove = channels[channels.size - 1]
        updateAndValidateUserChannels(channels)

        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.channelItem),
            hasDescendant(allOf(
                withId(R.id.channelName),
                withText(channelToRemove.friendlyName)
            ))
        ), longClick())

        BottomSheetBehavior.from(activityRule.activity.removeChannelSheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.leave_channel_button),
            isDisplayed()
        ), click())

        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelLeft.verifyCalled()
            channels.remove(channelToRemove)
        }
        updateAndValidateUserChannels(channels)
    }

    @Test
    fun userChannelMuted() {
        val channels = getMockedChannels(userChannelCount, MOCK_USER_CHANNEL_NAME, Channel.ChannelStatus.JOINED)
        val channelToMute = channels[channels.size - 1]
        updateAndValidateUserChannels(channels)

        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.channelMute),
            withParent(allOf(
                withId(R.id.channelItem),
                hasDescendant(allOf(
                    withId(R.id.channelName),
                    withText(channelToMute.friendlyName)
                ))))
        ), click())

        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelMuted.awaitValue(true)
        }
    }

    @Test
    fun userChannelUnmuted() {
        val channels = getMockedChannels(userChannelCount, MOCK_USER_CHANNEL_NAME,
            Channel.ChannelStatus.JOINED, Channel.NotificationLevel.MUTED)
        val channelToMute = channels[channels.size - 1]
        updateAndValidateUserChannels(channels)

        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.channelMute),
            withParent(allOf(
                withId(R.id.channelItem),
                hasDescendant(allOf(
                    withId(R.id.channelName),
                    withText(channelToMute.friendlyName)
                ))))
        ), click())

        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelMuted.awaitValue(false)
        }
    }

    @Test
    fun userChannelFetchFailed() {
        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelError.value = ChatError.CHANNEL_FETCH_USER_FAILED
        }

        onView(withText(R.string.err_failed_to_fetch_user_channels)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun publicChannelFetchFailed() {
        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelError.value = ChatError.CHANNEL_FETCH_PUBLIC_FAILED
        }

        onView(withText(R.string.err_failed_to_fetch_public_channels)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun userChannelFilter() {
        val channelAbc = createTestChannelDataItem(friendlyName = "abc")
        val channelBcd = createTestChannelDataItem(friendlyName = "bcd")
        val channelCde = createTestChannelDataItem(friendlyName = "cde")
        val channels = listOf(channelAbc, channelBcd, channelCde)
        testInjector.userChannelRepositoryResult = flowOf(RepositoryResult(channels,
            RepositoryRequestStatus.COMPLETE))
        channelListViewModel.getUserChannels()

        onView(withId(R.id.filter_channels)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeTextIntoFocusedView("d"), closeKeyboard())
        Espresso.pressBack()

        validateChannelItems(listOf(channelBcd, channelCde).asChannelListViewItems())
    }

    @Test
    fun publicChannelFilter() {
        val channelAbc = createTestChannelDataItem(friendlyName = "abc")
        val channelBcd = createTestChannelDataItem(friendlyName = "bcd")
        val channelCde = createTestChannelDataItem(friendlyName = "cde")
        val channels = listOf(channelAbc, channelBcd, channelCde)
        testInjector.publicChannelRepositoryResult = flowOf(RepositoryResult(channels,
            RepositoryRequestStatus.COMPLETE))
        channelListViewModel.getPublicChannels()

        switchChannelTab(activityRule.activity.getString(R.string.txt_tab_public_channels))
        onView(withId(R.id.filter_channels)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeTextIntoFocusedView("d"), closeKeyboard())
        Espresso.pressBack()

        validateChannelItems(listOf(channelBcd, channelCde).asChannelListViewItems())
    }

    @Test
    fun channelFilterPreserved() {
        // Verifies that the filter string that's entered on the user channel list is preserved
        // and applied correctly when moving to the public channels tab
        val channelAbc = createTestChannelDataItem(friendlyName = "abc")
        val channelBcd = createTestChannelDataItem(friendlyName = "bcd")
        val channelCde = createTestChannelDataItem(friendlyName = "cde")
        testInjector.userChannelRepositoryResult = flowOf(RepositoryResult(listOf(channelAbc, channelBcd),
            RepositoryRequestStatus.COMPLETE))
        testInjector.publicChannelRepositoryResult = flowOf(RepositoryResult(listOf(channelBcd, channelCde),
            RepositoryRequestStatus.COMPLETE))
        channelListViewModel.getPublicChannels()
        channelListViewModel.getUserChannels()

        onView(withId(R.id.filter_channels)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeTextIntoFocusedView("b"), closeKeyboard())
        onView(withId(R.id.search_close_btn)).perform(click())
        Espresso.pressBack()
        switchChannelTab(activityRule.activity.getString(R.string.txt_tab_public_channels))

        validateChannelItems(listOf(channelBcd).asChannelListViewItems())
    }

    @Test
    fun userFriendlyNameChanged() {
        val identity = "identity"
        val friendlyName1 = "friendly name 1"
        val friendlyName2 = "friendly name 2"

        UiThreadStatement.runOnUiThread {
            channelListViewModel.selfUser.value = createTestUserViewItem(friendlyName = friendlyName1, identity = identity)
        }

        WaitForViewMatcher.performOnView(withId(R.id.channel_drawer_layout), DrawerActions.open())
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.drawer_member_name), withText(friendlyName1), isDisplayed()))
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.drawer_member_info), withText(identity), isDisplayed()))
        WaitForViewMatcher.performOnView(withId(R.id.drawer_settings_button), click())

        BottomSheetBehavior.from(activityRule.activity.user_profile_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.user_profile_friendly_name), withText(friendlyName1), isDisplayed()))
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.user_profile_identity), withText(identity), isDisplayed()))
        WaitForViewMatcher.performOnView(withId(R.id.user_profile_friendly_name), replaceText(friendlyName2), pressImeActionButton())
        WaitForViewMatcher.performOnView(withId(R.id.user_profile_update_button), scrollTo(), click())

        UiThreadStatement.runOnUiThread {
            channelListViewModel.selfUser.value = createTestUserViewItem(friendlyName = friendlyName2, identity = identity)
        }

        WaitForViewMatcher.performOnView(withId(R.id.channel_drawer_layout), DrawerActions.open())
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.drawer_member_name), withText(friendlyName2), isDisplayed()))
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.drawer_member_info), withText(identity), isDisplayed()))
        WaitForViewMatcher.performOnView(withId(R.id.drawer_settings_button), click())

        BottomSheetBehavior.from(activityRule.activity.user_profile_sheet).waitUntilPopupStateChanged(BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.user_profile_friendly_name), withText(friendlyName2), isDisplayed()))
        WaitForViewMatcher.assertOnView(allOf(withId(R.id.user_profile_identity), withText(identity), isDisplayed()))
    }

    @Test
    fun userFriendlyNameChangeFailed() {
        UiThreadStatement.runOnUiThread {
            channelListViewModel.onChannelError.value = ChatError.USER_UPDATE_FAILED
        }

        onView(withText(R.string.err_failed_to_update_user)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    private fun verifyAddChannelPopupVisible() {
        WaitForViewMatcher.assertOnView(allOf(
            withId(R.id.channel_name_input),
            withText("")
        ), matches(isDisplayed()))

        WaitForViewMatcher.assertOnView(
            withId(R.id.channel_type_dropdown),
            matches(withSpinnerText(activityRule.activity.resources.getStringArray(R.array.channel_type)[0])))
    }

    private fun updateAndValidateUserChannels(channels: List<ChannelDataItem>) {
        testInjector.userChannelRepositoryResult = flowOf(RepositoryResult(channels,
            RepositoryRequestStatus.COMPLETE))
        channelListViewModel.getUserChannels()

        validateChannelItems(channels.asChannelListViewItems())
    }

    private fun updateAndValidatePublicChannels(channels: List<ChannelDataItem>) {
        testInjector.publicChannelRepositoryResult = flowOf(RepositoryResult(channels,
            RepositoryRequestStatus.COMPLETE))
        channelListViewModel.getPublicChannels()

        validateChannelItems(channels.asChannelListViewItems())
    }

    private fun validateChannelItems(channels: List<ChannelListViewItem>) =
        channels.forEachIndexed { index, channel ->
            validateChannelItem(index, channel)
        }

    private fun validateChannelItem(index: Int, channel: ChannelListViewItem) {
        // Scroll to correct channel list position
        WaitForViewMatcher.performOnView(
            allOf(withId(R.id.channelList), isDisplayed()),
            scrollToPosition<ChannelListAdapter.ViewHolder>(index)
        )

        // Validate channel item
        WaitForViewMatcher.assertOnView(atPosition(index, allOf(
            // Given the list item
            withId(R.id.channelItem),
            // Check for correct channel name
            hasDescendant(allOf(
                allOf(
                    withId(R.id.channelName),
                    withText(channel.name)
                ),
                // Check for correct channel subtitle text
                hasSibling(allOf(
                    withId(R.id.channelInfo),
                    withText(activityRule.activity.getString(R.string.channel_info, channel.memberCount, channel.dateCreated)))
                )
            )),
            // Check for correct lock icon visibility
            hasDescendant(allOf(
                withId(R.id.channelLock),
                if (channel.isLocked) isDisplayed() else not(isDisplayed())
            )),
            // Check for channel update time
            hasDescendant(allOf(
                withId(R.id.channelUpdateTime),
                withText(channel.dateUpdated)
            )),
            // Check unread message count
            hasDescendant(allOf(
                withId(R.id.channelUnreadCount),
                isDisplayed(),
                withText(channel.messageCount)
            ))
        )), matches(isCompletelyDisplayed()))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupInjector() = setupTestInjector()

        const val MOCK_USER_CHANNEL_NAME = "Test User Channel"
        const val MOCK_PUBLIC_CHANNEL_NAME = "Test Public Channel"
    }
}
