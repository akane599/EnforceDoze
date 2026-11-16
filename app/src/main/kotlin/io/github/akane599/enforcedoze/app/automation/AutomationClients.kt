// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.app.automation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The automation clients (ADR 0033): each is a package with a token bound to it. Only the token's SHA-256 hash is
 * kept, in a file outside backup; the token itself is shown to the owner once, when it is made.
 */
internal class AutomationClients(
    private val file: File,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    data class Client(val packageName: String, val hash: String, val created: Long)

    @Serializable
    private data class Saved(val clients: List<Client> = emptyList())

    /** The file as last read or written: every call checks a token, so the file is not parsed each time. */
    private var cached: Saved? = null

    @Synchronized
    fun clients(): List<Client> = load().clients

    /** A new token for [packageName], replacing the one it had: the old token stops working at once. */
    @Synchronized
    fun issue(packageName: String): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val others = load().clients.filterNot { it.packageName == packageName }
        save(Saved(others + Client(packageName, hex(sha256(token)), clock())))
        return token
    }

    /** Reset: every client goes, with its token. */
    @Synchronized
    fun clear() {
        file.delete()
        cached = Saved()
    }

    @Synchronized
    fun remove(packageName: String) = save(Saved(load().clients.filterNot { it.packageName == packageName }))

    /** The package [token] is bound to, or null. Hashes are compared in constant time. */
    @Synchronized
    fun match(token: String?): String? {
        if (token.isNullOrBlank() || token.length > MAX_TOKEN_LENGTH) return null
        val given = sha256(token.trim())
        return load().clients.firstOrNull { MessageDigest.isEqual(given, unhex(it.hash)) }?.packageName
    }

    private fun load(): Saved = cached ?: read().also { cached = it }

    /** An unreadable file counts as no clients: every token stops working, which fails closed. */
    private fun read(): Saved {
        if (!file.isFile) return Saved()
        return runCatching { JSON.decodeFromString<Saved>(file.readText()) }.getOrDefault(Saved())
    }

    /** The cache changes only once the file is written, so a failed write never leaves a token that is not on disk. */
    private fun save(saved: Saved) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSON.encodeToString(saved))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not write ${file.name}" }
        }
        cached = saved
    }

    private companion object {
        const val TOKEN_BYTES = 16
        const val MAX_TOKEN_LENGTH = 256
        val JSON = Json { ignoreUnknownKeys = true }

        fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun unhex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(HEX).toByte() }

        const val HEX = 16
    }
}
