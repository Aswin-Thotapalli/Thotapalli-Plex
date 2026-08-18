package com.thotapalli.plex.core.session

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * Token storage for Windows.
 *
 * The value is encrypted with DPAPI through CryptProtectData scoped to the current user,
 * so the ciphertext is useless to any other account on the machine and to anyone who
 * copies the file elsewhere. See CLAUDE.md section 5 step 4.
 *
 * JNA's Crypt32Util wraps CryptProtectData and CryptUnprotectData directly.
 */
class DpapiSecureStore(
    private val directory: Path = defaultDirectory(),
) : SecureStore {

    init {
        Files.createDirectories(directory)
    }

    override fun getSecret(key: String): String? {
        val file = fileFor(key)
        if (!Files.exists(file)) return null
        return runCatching {
            val encoded = Files.readAllBytes(file)
            val cipherText = Base64.getDecoder().decode(encoded)
            val plain = Crypt32Util.cryptUnprotectData(cipherText)
            String(plain, StandardCharsets.UTF_8)
        }.getOrElse {
            // Unreadable means it was written by another user account or the profile was
            // rebuilt. Either way the token is gone; drop it rather than fail forever.
            runCatching { Files.deleteIfExists(file) }
            null
        }
    }

    override fun putSecret(key: String, value: String) {
        val cipherText = Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.getEncoder().encode(cipherText)
        val target = fileFor(key)
        // Written beside the target and moved into place, so an interrupted write cannot
        // leave a half token that decrypts to nothing.
        val temp = Files.createTempFile(directory, "tmp", ".dat")
        Files.write(temp, encoded)
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun removeSecret(key: String) {
        Files.deleteIfExists(fileFor(key))
    }

    override fun clear() {
        if (!Files.isDirectory(directory)) return
        Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(SUFFIX) }
                .forEach { runCatching { Files.delete(it) } }
        }
    }

    private fun fileFor(key: String): Path =
        directory.resolve(key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + SUFFIX)

    companion object {
        private const val SUFFIX = ".dpapi"

        fun defaultDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return Path.of(localAppData, "ThotapalliPlex", "secure")
        }
    }
}
