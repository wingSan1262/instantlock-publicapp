package com.risyan.quickshutdownphone.base.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import android.content.Context
import android.content.SharedPreferences
import com.risyan.quickshutdownphone.base.toMinuteAndSecondFormat
import com.google.gson.Gson

@Parcelize
data class LockStatus(
    var startLock: Boolean,
    var endLock: Long,
    var lockIntervalTime: Int
) : Parcelable {
    fun getRemainingDurationTo(
        context: Context
    ): String {
        val value = endLock - System.currentTimeMillis()
        val message = value.toMinuteAndSecondFormat(context)
        return message
    }
}

// reminder to activate accessibility
@Parcelize
data class AccessibilityReminder(
    var reminderActiveDeadline : Long,
) : Parcelable {
    fun isCurrentTimeAfterReminderActiveDeadline(): Boolean {
        return System.currentTimeMillis() > reminderActiveDeadline
    }
}

class SharedPrefApi(context: Context) {

    private val PREFS_NAME = "com.risyan.quickshutdownphone"
    private val LOCK_STATUS_KEY = "lock_status"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLockStatus(lockStatus: LockStatus) {
        val editor = prefs.edit()
        val gson = Gson()
        val json = gson.toJson(lockStatus)
        editor.putString(LOCK_STATUS_KEY, json)
        editor.apply()
    }

    fun getLockStatus(): LockStatus? {
        val gson = Gson()
        val json = prefs.getString(LOCK_STATUS_KEY, null)
        return gson.fromJson(json, LockStatus::class.java)
    }

    fun removeLockStatus() {
        val editor = prefs.edit()
        editor.remove(LOCK_STATUS_KEY)
        editor.apply()
    }


    fun getCurrentBlankImageCounter(): Int {
        return prefs.getInt("blank_image_counter", 0)
    }

    fun setCurrentBlankImageCounter(counter: Int) {
        val editor = prefs.edit()
        editor.putInt("blank_image_counter", counter)
        editor.apply()
    }

    fun getCurrentNsfwCounter(): Int {
        return prefs.getInt("nsfw_image_counter", 0)
    }

    fun setCurrentNsfwCounter(counter: Int) {
        val editor = prefs.edit()
        editor.putInt("nsfw_image_counter", counter)
        editor.apply()
    }

    fun getCurrentSafeCounter(): Int {
        return prefs.getInt("safe_image_counter", 0)
    }
    fun setCurrentSafeCounter(counter: Int) {
        val editor = prefs.edit()
        editor.putInt("safe_image_counter", counter)
        editor.apply()
    }

    fun getCurrentSexyCounter(): Int {
        return prefs.getInt("sexy_image_counter", 0)
    }
    fun setCurrentSexyCounter(counter: Int) {
        val editor = prefs.edit()
        editor.putInt("sexy_image_counter", counter)
        editor.apply()
    }


    fun saveAccessibilityReminder() {
        // 10 minutes
        val reminder = AccessibilityReminder(System.currentTimeMillis()
//                + 10
                + 3
                * 60 * 1000
        )
        val editor = prefs.edit()
        val gson = Gson()
        val json = gson.toJson(reminder)
        editor.putString("accessibility_reminder", json)
        editor.apply()
    }
    fun removeAccessibilityReminder() {
        val editor = prefs.edit()
        editor.remove("accessibility_reminder")
        editor.apply()
    }

    fun getAccessibilityReminder(): AccessibilityReminder? {
        val gson = Gson()
        val json = prefs.getString("accessibility_reminder", null)
        return gson.fromJson(json, AccessibilityReminder::class.java)
    }

    fun setLastTimeUserDoNsfwCheck(time: Long = System.currentTimeMillis()) {
        val editor = prefs.edit()
        editor.putLong("last_time_user_do_nsfw_check", time)
        editor.apply()
    }

    fun getLastTimeUserDoNsfwCheck(): Long {
        return prefs.getLong("last_time_user_do_nsfw_check", 0L)
    }


     fun isAnyActiveLock(): Boolean {
        val lockStatus = getLockStatus()
        return lockStatus != null && lockStatus.startLock && lockStatus.endLock > System.currentTimeMillis()
    }
}