package com.twilio.chat.app.common.extensions

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.iid.InstanceIdResult
import com.twilio.chat.app.R
import com.twilio.chat.app.common.enums.ChatError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun FragmentActivity.hideKeyboard() {
    val view = currentFocus ?: window.decorView
    val token = view.windowToken
    view.clearFocus()
    ContextCompat.getSystemService(this, InputMethodManager::class.java)?.hideSoftInputFromWindow(token, 0)
}

fun BottomSheetBehavior<View>.isShowing() = state == BottomSheetBehavior.STATE_EXPANDED

fun BottomSheetBehavior<View>.show() {
    if (!isShowing()) {
        state = BottomSheetBehavior.STATE_EXPANDED
    }
}

fun BottomSheetBehavior<View>.hide() {
    if (state != BottomSheetBehavior.STATE_HIDDEN) {
        state = BottomSheetBehavior.STATE_HIDDEN
    }
}

fun Context.getErrorMessage(error: ChatError): String {
    return when (error) {
        ChatError.CHANNEL_CREATE_FAILED -> getString(R.string.err_failed_to_create_channel)
        ChatError.CHANNEL_JOIN_FAILED -> getString(R.string.err_failed_to_join_channel)
        ChatError.CHANNEL_REMOVE_FAILED -> getString(R.string.err_failed_to_remove_channel)
        ChatError.CHANNEL_LEAVE_FAILED -> getString(R.string.err_failed_to_leave_channel)
        ChatError.CHANNEL_FETCH_USER_FAILED -> getString(R.string.err_failed_to_fetch_user_channels)
        ChatError.CHANNEL_FETCH_PUBLIC_FAILED -> getString(R.string.err_failed_to_fetch_public_channels)
        ChatError.CHANNEL_MUTE_FAILED -> getString(R.string.err_failed_to_mute_channels)
        ChatError.CHANNEL_UNMUTE_FAILED -> getString(R.string.err_failed_to_unmute_channel)
        ChatError.CHANNEL_RENAME_FAILED-> getString(R.string.err_failed_to_rename_channel)
        ChatError.REACTION_UPDATE_FAILED -> getString(R.string.err_failed_to_update_reaction)
        ChatError.MEMBER_FETCH_FAILED -> getString(R.string.err_failed_to_fetch_members)
        ChatError.MEMBER_ADD_FAILED -> getString(R.string.err_failed_to_add_member)
        ChatError.MEMBER_REMOVE_FAILED -> getString(R.string.err_failed_to_remove_member)
        ChatError.USER_UPDATE_FAILED -> getString(R.string.err_failed_to_update_user)
        ChatError.MESSAGE_MEDIA_DOWNLOAD_FAILED -> getString(R.string.err_failed_to_download_media)
        else -> getString(R.string.err_channel_generic_error)
    }
}

fun CoordinatorLayout.showSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()
}

fun AppCompatActivity.showToast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}

fun ContentResolver.getString(uri: Uri, columnName: String): String? {
    val cursor = query(uri, arrayOf(columnName), null, null, null)
    return cursor?.let {
        it.moveToFirst()
        val name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        it.close()
        return@let name
    }
}

fun Cursor.getInt(columnName: String): Int = getInt(getColumnIndex(columnName))

fun Cursor.getLong(columnName: String): Long = getLong(getColumnIndex(columnName))

fun Cursor.getString(columnName: String): String = getString(getColumnIndex(columnName))

fun DownloadManager.queryById(id: Long): Cursor =
    query(DownloadManager.Query().apply {
        setFilterById(id)
    })

suspend fun Task<InstanceIdResult>.retrieveToken() = suspendCoroutine<String> { continuation ->
    addOnCompleteListener { task ->
        try {
            task.result?.let { continuation.resume(it.token) }
                ?: continuation.resumeWithException(ChatException(ChatError.TOKEN_ERROR))
        } catch (e: Exception) {
            // TOO_MANY_REGISTRATIONS thrown on devices with too many Firebase instances
            continuation.resumeWithException(ChatException(ChatError.TOKEN_ERROR))
        }
    }
}
