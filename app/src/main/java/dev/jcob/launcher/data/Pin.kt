package dev.jcob.launcher.data

import java.security.MessageDigest
import java.security.SecureRandom

/** Stored as "salt:sha256(salt+pin)". It's a deterrent against casual fiddling, not real security. */
object Pin {
    fun hash(pin: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
        return "$salt:${digest(salt, pin)}"
    }

    fun matches(pin: String, stored: String): Boolean {
        val (salt, hash) = stored.split(':').takeIf { it.size == 2 } ?: return false
        return digest(salt, pin) == hash
    }

    private fun digest(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
