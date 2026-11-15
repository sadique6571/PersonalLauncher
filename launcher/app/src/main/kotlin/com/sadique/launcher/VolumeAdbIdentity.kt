package com.sadique.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import javax.crypto.KeyGenerator
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** Software ADB RSA identity encrypted at rest by a non-exportable Android Keystore AES key. */
internal class VolumeAdbIdentity private constructor(
    val key: PrivateKey,
    val certificate: Certificate
) {
    companion object {
        private const val ALIAS = "personal_volume_adb_identity_v1"

        @Synchronized
        fun load(context: Context): VolumeAdbIdentity {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            if (!store.containsAlias(ALIAS)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
                )
                generator.generateKey()
            }
            val wrapping = store.getKey(ALIAS, null) as SecretKey
            val file = AtomicFile(File(context.noBackupFilesDir, "volume-adb-identity.bin"))
            if (file.baseFile.exists()) {
                val sealed = file.readFully()
                if (sealed.size < 30 || sealed.size > 16384 || sealed[0].toInt() != 12) throw IOException("Invalid saved ADB identity")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrapping, GCMParameterSpec(128, sealed, 1, 12))
                val clear = cipher.doFinal(sealed, 13, sealed.size - 13)
                try {
                    DataInputStream(ByteArrayInputStream(clear)).use { input ->
                        val keyBytes = part(input)
                        val certificateBytes = part(input)
                        return VolumeAdbIdentity(
                            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes)),
                            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificateBytes))
                        )
                    }
                } finally {
                    clear.fill(0)
                }
            }
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()
            val name = X500Name("CN=PersonalLauncher local volume")
            val now = System.currentTimeMillis()
            val der = X509v3CertificateBuilder(
                name, BigInteger(128, SecureRandom()), Date(now - 86400000L),
                Date(now + 315360000000L), name, SubjectPublicKeyInfo.getInstance(pair.public.encoded)
            )
                .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)).encoded
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der))
            val clear = ByteArrayOutputStream()
            DataOutputStream(clear).use { output ->
                write(output, pair.private.encoded)
                write(output, der)
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapping)
            val plain = clear.toByteArray()
            val sealed: ByteArray
            try {
                sealed = cipher.doFinal(plain)
            } finally {
                plain.fill(0)
            }
            var stream: FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(cipher.iv.size)
                stream.write(cipher.iv)
                stream.write(sealed)
                file.finishWrite(stream)
            } catch (error: IOException) {
                if (stream != null) file.failWrite(stream)
                throw error
            }
            return VolumeAdbIdentity(pair.private, certificate)
        }

        private fun part(input: DataInputStream): ByteArray {
            val size = input.readInt()
            if (size <= 0 || size > 8192) throw IOException("Invalid identity part")
            val data = ByteArray(size)
            input.readFully(data)
            return data
        }

        private fun write(output: DataOutputStream, bytes: ByteArray) {
            output.writeInt(bytes.size)
            output.write(bytes)
        }
    }
}
