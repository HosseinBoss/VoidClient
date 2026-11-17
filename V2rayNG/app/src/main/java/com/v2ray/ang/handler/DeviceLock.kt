package com.v2ray.ang.handler

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import com.v2ray.ang.AngApplication
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Device-bound ("HWID locked") config links.
 *
 * - HWID: a short code derived (hashed) from this device's Android ID. It is what a customer sends
 *   to the person who will give them a config.
 * - Locked link: "VoidTunnel://<token>". The token is the config's share link encrypted with
 *   AES-256-GCM, using a key derived from the HWID, so only a device with that HWID can open it.
 * - Imported locked configs carry an invisible marker in their name so the app can refuse to
 *   share, export or edit them.
 */
object DeviceLock {
    const val LINK_PREFIX = "VoidTunnel://"

    private const val MAGIC = "VoidTunnel|1|"
    private const val VERSION: Byte = 1
    private const val MARK = "\u200B\u2063\u200B"
    private const val MARK_ENCODED = "%E2%80%8B%E2%81%A3%E2%80%8B"
    private const val SALT = "VoidTunnel/hwid-lock/v1"
    private const val AAD = "VoidTunnel"
    private const val PBKDF2_ITERATIONS = 20_000
    private const val FALLBACK_SEED_KEY = "vt_hwid_seed"
    private const val EMULATOR_BAD_ID = "9774d56d682e549c"

    // ------------------------------------------------------------------ HWID

    /** This device's HWID, e.g. "A1B2-C3D4-E5F6-0718-293A-4B5C". */
    fun hwid(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        val seed = if (androidId.isNullOrBlank() || androidId == EMULATOR_BAD_ID) fallbackSeed() else androidId
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("VoidTunnel-HWID-v1|$seed|${context.packageName}".toByteArray(Charsets.UTF_8))
        val hex = digest.take(12).joinToString("") { String.format("%02X", it) }
        return hex.chunked(4).joinToString("-")
    }

    /** Returns the 24 upper-case hex characters of [input] (dashes/spaces ignored), or null if it is not a valid HWID. */
    fun normalizeHwid(input: String): String? {
        val cleaned = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        return if (cleaned.length == 24 && input.uppercase().all { it in '0'..'9' || it in 'A'..'F' || it == '-' || it.isWhitespace() }) cleaned else null
    }

    private fun fallbackSeed(): String {
        MmkvManager.decodeSettingsString(FALLBACK_SEED_KEY)?.takeIf { it.isNotBlank() }?.let { return it }
        val seed = UUID.randomUUID().toString()
        MmkvManager.encodeSettings(FALLBACK_SEED_KEY, seed)
        return seed
    }

    // ------------------------------------------------------- lock detection

    fun isLockedLink(text: String?): Boolean =
        text != null && text.lineSequence().any { it.trim().startsWith(LINK_PREFIX, ignoreCase = true) }

    fun isLockedRemarks(remarks: String?): Boolean = remarks != null && remarks.contains(MARK)

    fun isLockedProfile(profile: ProfileItem?): Boolean = isLockedRemarks(profile?.remarks)

    fun isLockedGuid(guid: String?): Boolean {
        if (guid.isNullOrBlank()) return false
        return isLockedProfile(MmkvManager.decodeServerConfig(guid))
    }

    // ------------------------------------------------------------ creating

    /** The plain share link (vless://, vmess://, ...) of [config], or null for unsupported or locked configs. */
    fun shareUriOf(config: ProfileItem): String? {
        if (isLockedProfile(config)) return null
        val body = when (config.configType) {
            EConfigType.VMESS -> VmessFmt.toUri(config)
            EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
            EConfigType.SOCKS -> SocksFmt.toUri(config)
            EConfigType.VLESS -> VlessFmt.toUri(config)
            EConfigType.TROJAN -> TrojanFmt.toUri(config)
            EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
            EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
            else -> return null
        }
        return config.configType.protocolScheme + body
    }

    /** Encrypts [shareUri] for the device with [hwidInput]. Returns null if the HWID is not valid. */
    fun createLockedLink(shareUri: String, hwidInput: String): String? {
        val hwid = normalizeHwid(hwidInput) ?: return null
        return try {
            val plain = (MAGIC + withLockMark(shareUri)).toByteArray(Charsets.UTF_8)
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(hwid), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(plain)
            val out = ByteArray(1 + nonce.size + encrypted.size)
            out[0] = VERSION
            System.arraycopy(nonce, 0, out, 1, nonce.size)
            System.arraycopy(encrypted, 0, out, 1 + nonce.size, encrypted.size)
            LINK_PREFIX + Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            null
        }
    }

    private fun withLockMark(link: String): String {
        if (link.startsWith("vmess://", ignoreCase = true)) {
            val payload = link.substring("vmess://".length)
            val decoded = decodeBase64Loose(payload)
            if (decoded != null) {
                try {
                    val json = JSONObject(String(decoded, Charsets.UTF_8))
                    json.put("ps", json.optString("ps") + MARK)
                    return "vmess://" + Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                } catch (_: Exception) {
                }
            }
            return link
        }
        val hash = link.indexOf('#')
        return if (hash >= 0) link + MARK_ENCODED else "$link#$MARK_ENCODED"
    }

    private fun decodeBase64Loose(text: String): ByteArray? {
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                return Base64.decode(text, flags)
            } catch (_: IllegalArgumentException) {
            }
        }
        return null
    }

    // ----------------------------------------------------------- opening

    /** Decrypts a "VoidTunnel://..." link for the device with [hwid]. Returns the inner share link, or null. */
    fun decryptLink(link: String, hwid: String): String? {
        val normalized = normalizeHwid(hwid) ?: return null
        return try {
            val token = link.trim().substring(LINK_PREFIX.length)
            val data = Base64.decode(token, Base64.URL_SAFE)
            if (data.size < 1 + 12 + 16 || data[0] != VERSION) return null
            val nonce = data.copyOfRange(1, 13)
            val encrypted = data.copyOfRange(13, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(normalized), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            val plain = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            if (plain.startsWith(MAGIC)) plain.substring(MAGIC.length) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Replaces every "VoidTunnel://..." line in [raw] with its decrypted share link, using this
     * device's HWID. Lines that cannot be opened (locked to another device) are dropped and the
     * user is told. Anything that is not a locked link is passed through untouched.
     * Safe to call from any thread.
     */
    fun unwrapForImport(raw: String?): String? {
        if (raw.isNullOrBlank() || !isLockedLink(raw)) return raw
        val context = try {
            AngApplication.application
        } catch (_: Throwable) {
            return raw
        }
        val hwid = hwid(context)
        val out = ArrayList<String>()
        var failed = 0
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(LINK_PREFIX, ignoreCase = true)) {
                val inner = decryptLink(trimmed, hwid)
                if (inner != null) out.add(inner) else failed++
            } else {
                out.add(line)
            }
        }
        if (failed > 0) {
            toast(context, "This config is locked to another device (HWID mismatch)")
        }
        return if (out.isEmpty()) null else out.joinToString("\n")
    }

    fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // --------------------------------------------------------------- key

    private fun deriveKey(hwid: String): ByteArray =
        pbkdf2Sha256(hwid.toByteArray(Charsets.UTF_8), SALT.toByteArray(Charsets.UTF_8), PBKDF2_ITERATIONS)

    /** PBKDF2-HMAC-SHA256, single 32-byte block (implemented by hand because the JDK factory needs API 26). */
    private fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val result = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }
}
