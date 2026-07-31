package io.github.pawelwywiol.minervahelper.data.web

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * How a browser earns access: the device shows a six-digit code, the browser posts it back and gets
 * a long-lived token in return. There are no accounts in this app and inventing them would buy
 * nothing — the only claim being checked is "this browser was held by whoever was holding the
 * device".
 *
 * Tokens are stored as hashes. A prefs file lifted off the device then yields nothing that works,
 * for the same reason a server does not keep plaintext passwords.
 */
class Pairing(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("web_pairing", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    data class Offer(val pin: String, val expiresAt: Long)

    data class Client(val id: String, val agent: String, val pairedAt: Long)

    /** The code on screen right now; null when nothing is on offer. */
    var offer by mutableStateOf<Offer?>(null)
        private set

    var clients by mutableStateOf(readClients())
        private set

    private var failures = 0
    private var lockedUntil = 0L

    fun offerPin(): Offer {
        failures = 0
        lockedUntil = 0L
        val pin = "%06d".format(random.nextInt(1_000_000))
        return Offer(pin, System.currentTimeMillis() + PIN_TTL_MS).also { offer = it }
    }

    fun withdrawOffer() {
        offer = null
    }

    /** Seconds left on the current code, 0 once it has lapsed. */
    fun secondsLeft(): Int = offer
        ?.let { ((it.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt() }
        ?: 0

    /**
     * Null when the code is wrong, stale, absent, or pairing is locked out. Six digits fall to brute
     * force in minutes over a LAN, so a handful of misses burns the code rather than merely
     * rejecting the attempt.
     */
    fun redeem(pin: String, agent: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return null
        val current = offer ?: return null
        if (now > current.expiresAt) {
            offer = null
            return null
        }
        if (!MessageDigest.isEqual(pin.toByteArray(), current.pin.toByteArray())) {
            failures++
            if (failures >= MAX_FAILURES) {
                offer = null
                lockedUntil = now + LOCKOUT_MS
            }
            return null
        }
        offer = null
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        prefs.edit { putString(key(token), now.toString() + SEPARATOR + label(agent)) }
        clients = readClients()
        return token
    }

    fun isValid(token: String): Boolean = prefs.contains(key(token))

    fun revoke(id: String) {
        prefs.edit { remove(KEY_PREFIX + id) }
        clients = readClients()
    }

    /** Used when a browser signs itself out; it knows its token, not the id derived from it. */
    fun revokeToken(token: String) {
        prefs.edit { remove(key(token)) }
        clients = readClients()
    }

    fun revokeAll() {
        prefs.edit { clear() }
        clients = readClients()
    }

    /**
     * The list exists so the owner can tell which browser to unpair, and a raw user agent — 120
     * characters of version numbers, truncated on a 4.7" screen — cannot answer that. Anything
     * unrecognised keeps its first few words rather than pretending to know.
     */
    private fun label(agent: String): String {
        val browser = BROWSERS.firstOrNull { agent.contains(it.first) }?.second
        val system = SYSTEMS.firstOrNull { agent.contains(it.first) }?.second
        return when {
            browser != null && system != null -> "$browser on $system"
            browser != null -> browser
            agent.isBlank() -> "Unknown browser"
            else -> agent.take(AGENT_LIMIT)
        }
    }

    private fun key(token: String) = KEY_PREFIX + hash(token)

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun readClients(): List<Client> = prefs.all
        .filterKeys { it.startsWith(KEY_PREFIX) }
        .map { (key, value) ->
            val parts = value.toString().split(SEPARATOR)
            Client(
                id = key.removePrefix(KEY_PREFIX),
                agent = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Unknown browser",
                pairedAt = parts.firstOrNull()?.toLongOrNull() ?: 0L,
            )
        }
        .sortedByDescending { it.pairedAt }

    companion object {
        private const val KEY_PREFIX = "token/"

        /** A user agent is full of punctuation and spaces; only a control character is safe. */
        private const val SEPARATOR = "\u0000"
        private const val PIN_TTL_MS = 2 * 60 * 1000L
        private const val LOCKOUT_MS = 5 * 60 * 1000L
        private const val MAX_FAILURES = 5
        private const val TOKEN_BYTES = 32
        private const val AGENT_LIMIT = 40

        // Order matters: every Chromium browser also says "Chrome", and Chrome says "Safari".
        private val BROWSERS = listOf(
            "Edg/" to "Edge",
            "OPR/" to "Opera",
            "Firefox" to "Firefox",
            "Chrome" to "Chrome",
            "Safari" to "Safari",
            "curl" to "curl",
        )
        private val SYSTEMS = listOf(
            "Android" to "Android",
            "iPhone" to "iPhone",
            "iPad" to "iPad",
            "Macintosh" to "Mac",
            "Windows" to "Windows",
            "Linux" to "Linux",
        )

        /** The server and the settings screen must offer and check the same code. */
        @Volatile
        private var instance: Pairing? = null

        fun get(context: Context): Pairing =
            instance ?: synchronized(this) {
                instance ?: Pairing(context.applicationContext).also { instance = it }
            }
    }
}
