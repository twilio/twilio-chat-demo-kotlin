package com.twilio.chat.app.ui

import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.twilio.chat.app.*
import com.twilio.chat.app.adapters.ChannelListAdapter
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.asMemberListViewItems
import com.twilio.chat.app.common.setupTestInjector
import com.twilio.chat.app.common.testInjector
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.models.MemberListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.testUtil.WaitForViewMatcher
import com.twilio.chat.app.testUtil.atPosition
import com.twilio.chat.app.testUtil.waitUntilPopupStateChanged
import com.twilio.chat.app.viewModel.MemberListViewModel
import kotlinx.android.synthetic.main.activity_members.*
import kotlinx.coroutines.flow.flowOf
import org.hamcrest.CoreMatchers.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemberListActivityTest {

    @get:Rule
    var activityRule: ActivityTestRule<MemberListActivity> = ActivityTestRule(MemberListActivity::class.java, false, false)

    private lateinit var memberListViewModel: MemberListViewModel

    private val channelSid = "channelSid"
    private val memberName = "member"

    @Before
    fun setUp() {
        activityRule.launchActivity(MemberListActivity.getStartIntent(InstrumentationRegistry.getInstrumentation().targetContext, channelSid))
        ChatClientWrapper.recreateInstance()
        memberListViewModel = activityRule.activity.memberListViewModel
    }

    @Test
    fun membersListVisible() {
        val members = getMockedMembers(MEMBER_COUNT, memberName)
        updateAndValidateMembersList(members)
    }

    @Test
    fun memberListChanged() {
        // Given a list of user members
        val members: MutableList<MemberDataItem> = getMockedMembers(MEMBER_COUNT, memberName)
        updateAndValidateMembersList(members)

        // .. when new member is added
        val newMember = createTestMemberDataItem(friendlyName = "New Member")
        members.add(newMember)
        // .. then member list is updated
        updateAndValidateMembersList(members)

        // .. when a member is removed
        members.remove(newMember)
        // .. then member list is updated
        updateAndValidateMembersList(members)
    }

    @Test
    fun memberFetchFailed() {
        UiThreadStatement.runOnUiThread {
            memberListViewModel.onMemberError.value = ChatError.MEMBER_FETCH_FAILED
        }

        onView(withText(R.string.err_failed_to_fetch_members)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun memberListFilter() {
        val memberAbc = createTestMemberDataItem(friendlyName = "abc")
        val memberBcd = createTestMemberDataItem(friendlyName = "bcd")
        val memberCde = createTestMemberDataItem(friendlyName = "cde")
        val members = listOf(memberAbc, memberBcd, memberCde)
        testInjector.memberRepositoryResult = flowOf(RepositoryResult(members, RepositoryRequestStatus.COMPLETE))
        memberListViewModel.getChannelMembers()

        onView(withId(R.id.filter_members)).perform(click())
        onView(withId(R.id.search_src_text)).perform(typeTextIntoFocusedView("d"))

        validateMemberItems(listOf(memberBcd, memberCde).asMemberListViewItems())
    }

    @Test
    fun memberRemoved() {
        val members: MutableList<MemberDataItem> = getMockedMembers(MEMBER_COUNT, memberName)
        val memberToRemove = members[members.size - 1]
        updateAndValidateMembersList(members)

        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.member_item),
            hasDescendant(allOf(
                withId(R.id.member_name),
                withText(memberToRemove.friendlyName)
            ))
        ), click())

        // Espresso click() will work while the bottom sheet is animating and not trigger the click listener
        // UI tests should be run with disabled animations otherwise we have to sleep here to trigger the listener
        BottomSheetBehavior.from(activityRule.activity.member_details_sheet).waitUntilPopupStateChanged(
            BottomSheetBehavior.STATE_EXPANDED)
        WaitForViewMatcher.performOnView(allOf(
            withId(R.id.member_details_remove),
            isDisplayed()
        ), click())

        UiThreadStatement.runOnUiThread {
            memberListViewModel.onMemberRemoved.verifyCalled()
            members.remove(memberToRemove)
        }
        updateAndValidateMembersList(members)
    }

    private fun updateAndValidateMembersList(members: List<MemberDataItem>) {
        testInjector.memberRepositoryResult = flowOf(RepositoryResult(members, RepositoryRequestStatus.COMPLETE))
        memberListViewModel.getChannelMembers()

        validateMemberItems(members.asMemberListViewItems())
    }

    private fun validateMemberItems(members: List<MemberListViewItem>) =
        members.forEachIndexed { index, member ->
            validateChannelItem(index, member)
        }

    private fun validateChannelItem(index: Int, member: MemberListViewItem) {
        // Scroll to correct member list position
        WaitForViewMatcher.performOnView(
            allOf(withId(R.id.memberList), isDisplayed()),
            scrollToPosition<ChannelListAdapter.ViewHolder>(index)
        )

        // Validate member item
        WaitForViewMatcher.assertOnView(atPosition(index, allOf(
            // Given the list item
            withId(R.id.member_item),
            // Check for correct member name
            hasDescendant(allOf(
                allOf(
                    withId(R.id.member_name),
                    withText(member.friendlyName)
                )
            ))
        )), matches(isCompletelyDisplayed()))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupInjector() = setupTestInjector()
    }
}
