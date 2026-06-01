package com.youtube.mini.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.parentDataStore by preferencesDataStore(name = "parent_prefs")

class ParentPrefs(private val context: Context) {

    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val blockedVideoIdsKey = stringPreferencesKey("blocked_video_ids")
    private val allowedVideoIdsKey = stringPreferencesKey("allowed_video_ids")
    private val useAllowListModeKey = booleanPreferencesKey("use_allowlist_mode")
    private val watchProgressKey = stringPreferencesKey("watch_progress")
    private val failedAttemptsKey = intPreferencesKey("failed_attempts")
    private val lockoutUntilKey = longPreferencesKey("lockout_until_ms")

    val pinHash: Flow<String?> = context.parentDataStore.data.map { it[pinHashKey] }
    val failedAttempts: Flow<Int> = context.parentDataStore.data.map { it[failedAttemptsKey] ?: 0 }
    val lockoutUntilMs: Flow<Long> = context.parentDataStore.data.map { it[lockoutUntilKey] ?: 0L }
    val useAllowListMode: Flow<Boolean> = context.parentDataStore.data.map { it[useAllowListModeKey] ?: false }

    val blockedVideoIds: Flow<Set<Long>> = context.parentDataStore.data.map { prefs ->
        val raw = prefs[blockedVideoIdsKey] ?: return@map emptySet()
        raw.split("|")
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    val allowedVideoIds: Flow<Set<Long>> = context.parentDataStore.data.map { prefs ->
        val raw = prefs[allowedVideoIdsKey] ?: return@map emptySet()
        raw.split("|")
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    val watchProgress: Flow<Map<Long, WatchProgressEntry>> = context.parentDataStore.data.map { prefs ->
        decodeWatchProgress(prefs[watchProgressKey].orEmpty())
    }

    suspend fun setPin(pin: String) {
        context.parentDataStore.edit { prefs ->
            prefs[pinHashKey] = derivePin(pin)
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
    }

    suspend fun isPinValid(pin: String): Boolean {
        val current = pinHash.first().orEmpty()
        return verifyPin(pin, current)
    }

    suspend fun setBlockedVideoIds(ids: Set<Long>) {
        context.parentDataStore.edit { prefs ->
            prefs[blockedVideoIdsKey] = ids.joinToString("|")
        }
    }

    suspend fun setAllowedVideoIds(ids: Set<Long>) {
        context.parentDataStore.edit { prefs ->
            prefs[allowedVideoIdsKey] = ids.joinToString("|")
        }
    }

    suspend fun setUseAllowListMode(enabled: Boolean) {
        context.parentDataStore.edit { prefs ->
            prefs[useAllowListModeKey] = enabled
        }
    }

    suspend fun saveWatchProgress(videoId: Long, positionMs: Long, updatedAtMs: Long) {
        context.parentDataStore.edit { prefs ->
            val current = decodeWatchProgress(prefs[watchProgressKey].orEmpty()).toMutableMap()
            current[videoId] = WatchProgressEntry(positionMs = positionMs, updatedAtMs = updatedAtMs)
            prefs[watchProgressKey] = encodeWatchProgress(current)
        }
    }

    suspend fun recordFailedAttempt(nowMs: Long): Long {
        var lockoutUntil = 0L
        context.parentDataStore.edit { prefs ->
            val attempts = (prefs[failedAttemptsKey] ?: 0) + 1
            prefs[failedAttemptsKey] = attempts
            lockoutUntil = when {
                attempts >= 10 -> nowMs + 60 * 60 * 1000L
                attempts >= 6 -> nowMs + 15 * 60 * 1000L
                attempts >= 3 -> nowMs + 60 * 1000L
                else -> 0L
            }
            prefs[lockoutUntilKey] = lockoutUntil
        }
        return lockoutUntil
    }

    suspend fun clearFailedAttempts() {
        context.parentDataStore.edit { prefs ->
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
    }

    private fun derivePin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        return "${salt.toHex()}:${hash.toHex()}"
    }

    private fun verifyPin(pin: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].fromHex() ?: return false
        val expected = parts[1].fromHex() ?: return false
        val actual = pbkdf2(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(keySpec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun decodeWatchProgress(raw: String): Map<Long, WatchProgressEntry> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val pos = parts[1].toLongOrNull() ?: return@mapNotNull null
                val updated = parts[2].toLongOrNull() ?: return@mapNotNull null
                id to WatchProgressEntry(pos, updated)
            }
            .toMap()
    }

    private fun encodeWatchProgress(data: Map<Long, WatchProgressEntry>): String {
        return data.entries.joinToString(";") {
            "${it.key}:${it.value.positionMs}:${it.value.updatedAtMs}"
        }
    }

    data class WatchProgressEntry(
        val positionMs: Long,
        val updatedAtMs: Long,
    )

}
