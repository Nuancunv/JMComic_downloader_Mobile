package com.nuancun.jmcomic.mobile.source

import android.annotation.SuppressLint
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JmCrypto {
    fun md5Hex(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun token(timestamp: Long, secret: String): String = md5Hex("$timestamp$secret")

    // The upstream protocol mandates AES-ECB; it is not used for local storage or app secrets.
    @SuppressLint("GetInstance")
    fun decryptResponse(base64: String, timestamp: Long, secret: String = DATA_SECRET): String {
        val encrypted = Base64.getMimeDecoder().decode(base64)
        val key = md5Hex("$timestamp$secret").toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    const val DATA_SECRET = "185Hcomic3PAPP7R"
    const val CONTENT_TOKEN_SECRET = "18comicAPPContent"
}
