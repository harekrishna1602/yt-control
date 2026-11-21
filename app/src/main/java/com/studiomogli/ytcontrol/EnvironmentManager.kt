package com.studiomogli.ytcontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EnvironmentProfile(
    val id: String,
    var name: String,
    var mode: String, // "WHITELIST" or "BLACKLIST"
    var dailyLimitMinutes: Int, // 0 = Unlimited
    var channels: MutableList<String>
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("mode", mode)
        obj.put("dailyLimitMinutes", dailyLimitMinutes)
        val arr = JSONArray()
        channels.forEach { arr.put(it) }
        obj.put("channels", arr)
        return obj
    }

    companion object {
        const val MODE_WHITELIST = "WHITELIST"
        const val MODE_BLACKLIST = "BLACKLIST"

        fun fromJson(obj: JSONObject): EnvironmentProfile {
            val chList = mutableListOf<String>()
            val arr = obj.optJSONArray("channels")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i, "").trim()
                    if (item.isNotEmpty()) chList.add(item)
                }
            }
            return EnvironmentProfile(
                id = obj.optString("id", "env_${System.currentTimeMillis()}"),
                name = obj.optString("name", "Custom"),
                mode = obj.optString("mode", MODE_BLACKLIST),
                dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 0),
                channels = chList
            )
        }
    }
}

class EnvironmentManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "ytcontrol_environments_prefs"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_USAGE_DATE = "usage_date"
        private const val KEY_USAGE_MAP = "usage_seconds_map"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfiles(): MutableList<EnvironmentProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) {
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<EnvironmentProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(EnvironmentProfile.fromJson(obj))
            }
            if (list.isEmpty()) {
                val defaults = createDefaultProfiles()
                saveProfiles(defaults)
                defaults
            } else {
                list
            }
        } catch (e: Exception) {
            val defaults = createDefaultProfiles()
            saveProfiles(defaults)
            defaults
        }
    }

    private fun createDefaultProfiles(): MutableList<EnvironmentProfile> {
        return mutableListOf(
            EnvironmentProfile(
                id = "study",
                name = "Study",
                mode = EnvironmentProfile.MODE_WHITELIST,
                dailyLimitMinutes = 0, // Unlimited
                channels = mutableListOf()
            ),
            EnvironmentProfile(
                id = "relax",
                name = "Relax",
                mode = EnvironmentProfile.MODE_BLACKLIST,
                dailyLimitMinutes = 45, // 45 min/day default
                channels = mutableListOf()
            )
        )
    }

    fun saveProfiles(profiles: List<EnvironmentProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun getActiveProfile(): EnvironmentProfile {
        val profiles = getProfiles()
        val activeId = prefs.getString(KEY_ACTIVE_ID, "study")
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, profileId).apply()
    }

    fun toggleNextProfile(): EnvironmentProfile {
        val profiles = getProfiles()
        val current = getActiveProfile()
        val idx = profiles.indexOfFirst { it.id == current.id }
        val next = profiles[(idx + 1) % profiles.size]
        setActiveProfile(next.id)
        return next
    }

    fun updateProfile(updated: EnvironmentProfile) {
        val profiles = getProfiles()
        val idx = profiles.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            profiles[idx] = updated
        } else {
            profiles.add(updated)
        }
        saveProfiles(profiles)
    }

    fun addChannelToActive(channelRaw: String): Boolean {
        val cleaned = channelRaw.trim()
        if (cleaned.isEmpty()) return false
        val profile = getActiveProfile()
        if (profile.channels.any { it.equals(cleaned, ignoreCase = true) }) return false
        profile.channels.add(cleaned)
        updateProfile(profile)
        return true
    }

    fun removeChannelFromActive(channelRaw: String) {
        val profile = getActiveProfile()
        profile.channels.removeAll { it.equals(channelRaw, ignoreCase = true) }
        updateProfile(profile)
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun ensureUsageDateCurrent() {
        val today = todayKey()
        val savedDate = prefs.getString(KEY_USAGE_DATE, "")
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_USAGE_DATE, today)
                .putString(KEY_USAGE_MAP, "{}")
                .apply()
        }
    }

    fun getUsedSecondsToday(profileId: String): Int {
        ensureUsageDateCurrent()
        val raw = prefs.getString(KEY_USAGE_MAP, "{}") ?: "{}"
        return try {
            JSONObject(raw).optInt(profileId, 0)
        } catch (e: Exception) {
            0
        }
    }

    fun incrementUsedSeconds(profileId: String, deltaSeconds: Int): Int {
        ensureUsageDateCurrent()
        val raw = prefs.getString(KEY_USAGE_MAP, "{}") ?: "{}"
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
        val updated = obj.optInt(profileId, 0) + deltaSeconds
        obj.put(profileId, updated)
        prefs.edit().putString(KEY_USAGE_MAP, obj.toString()).apply()
        return updated
    }

    fun getRemainingSecondsToday(profile: EnvironmentProfile): Int {
        if (profile.dailyLimitMinutes <= 0) return Int.MAX_VALUE
        val limitSec = profile.dailyLimitMinutes * 60
        val usedSec = getUsedSecondsToday(profile.id)
        return maxOf(0, limitSec - usedSec)
    }

    fun isProfileLocked(profile: EnvironmentProfile): Boolean {
        if (profile.dailyLimitMinutes <= 0) return false
        return getRemainingSecondsToday(profile) <= 0
    }

    fun buildConfigJsonForJs(profile: EnvironmentProfile): String {
        val obj = JSONObject()
        obj.put("mode", profile.mode)
        val arr = JSONArray()
        profile.channels.forEach { arr.put(it.lowercase(Locale.US)) }
        obj.put("channels", arr)
        return obj.toString()
    }
}
