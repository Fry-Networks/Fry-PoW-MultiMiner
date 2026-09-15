package com.frynetworks.pow.data

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The worker name this install reports to the pool.
 *
 * Every install used to default to the literal `worker1`, so a pool saw all of one
 * user's devices as a single worker and its dashboard showed only one of them at a
 * time - whichever had submitted most recently. Deriving the name from values that
 * differ per device and survive a restart means each install authorizes as
 * `<wallet>.<its own worker>` and every device shows up separately.
 */
object DeviceWorkerName {

    /** The shared default that caused the collision. Migrated away from on read. */
    const val LEGACY_DEFAULT = "worker1"

    private const val MAX_MODEL_LENGTH = 24
    private const val ID_SUFFIX_LENGTH = 6
    private const val FALLBACK = "frypow"

    /**
     * A pool-safe token. Stratum logins are split on `.`, so that separator must never
     * survive, and pools disagree about what else they accept - stay inside
     * `[A-Za-z0-9_-]`.
     */
    fun sanitise(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '-' }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')

    /** Pure derivation, kept separate from the Android lookup so it is unit-testable. */
    fun derive(model: String, deviceId: String): String {
        val name = sanitise(model).take(MAX_MODEL_LENGTH).trim('-').ifEmpty { FALLBACK }
        val suffix = sanitise(deviceId).takeLast(ID_SUFFIX_LENGTH).trim('-')
        return if (suffix.isEmpty()) name else "$name-$suffix"
    }

    /**
     * The worker to use, given whatever is currently persisted.
     *
     * A name the user typed is always kept. Only a missing value or the exact legacy
     * default is replaced, so an existing install is repaired on upgrade without
     * overwriting a deliberate choice.
     */
    fun resolve(stored: String?, deviceWorker: String): String = when {
        stored.isNullOrBlank() -> deviceWorker
        stored == LEGACY_DEFAULT -> deviceWorker
        else -> stored
    }

    fun forDevice(context: Context): String {
        val deviceId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return derive(Build.MODEL.orEmpty(), deviceId)
    }
}
