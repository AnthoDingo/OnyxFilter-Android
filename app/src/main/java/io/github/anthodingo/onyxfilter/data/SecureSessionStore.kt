package io.github.anthodingo.onyxfilter.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SessionStore] basé sur les SharedPreferences : les jetons sont chiffrés (AES-GCM) avec une clé de
 * l'Android Keystore, qui ne quitte jamais l'appareil. L'adresse du serveur et le nom d'utilisateur
 * restent en clair : ils servent à préremplir l'écran de connexion.
 */
class SecureSessionStore(context: Context) : SessionStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): Session? {
        val serverUrl = preferences.getString(KEY_SERVER_URL, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        val encryptedTokens = preferences.getString(KEY_TOKENS, null) ?: return null

        val tokens = try {
            val plain = decrypt(Base64.decode(encryptedTokens, Base64.NO_WRAP))
            ApiJson.decodeFromString(StoredTokens.serializer(), plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            // Clé du Keystore absente ou invalidée (restauration sur un autre appareil, etc.) : les
            // jetons sont perdus, l'utilisateur devra se reconnecter.
            clear()
            return null
        } catch (e: ProviderException) {
            clear()
            return null
        } catch (e: IllegalArgumentException) {
            // Base64 ou JSON invalide.
            clear()
            return null
        }

        return Session(
            serverUrl = serverUrl,
            username = username,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresAtMillis = tokens.accessTokenExpiresAtMillis,
        )
    }

    override fun save(session: Session) {
        val tokens = StoredTokens(session.accessToken, session.refreshToken, session.accessTokenExpiresAtMillis)
        val encrypted = try {
            encrypt(ApiJson.encodeToString(StoredTokens.serializer(), tokens).encodeToByteArray())
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: ProviderException) {
            null
        }

        preferences.edit {
            putString(KEY_SERVER_URL, session.serverUrl)
            putString(KEY_USERNAME, session.username)
            // Keystore indisponible : la session reste valable jusqu'à la fermeture de l'application,
            // sans être conservée (jamais de jeton stocké en clair).
            if (encrypted != null) {
                putString(KEY_TOKENS, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            } else {
                remove(KEY_TOKENS)
            }
        }
    }

    override fun clear() {
        preferences.edit { remove(KEY_TOKENS) }
    }

    override fun loginHint(): LoginHint? {
        val serverUrl = preferences.getString(KEY_SERVER_URL, null) ?: return null
        return LoginHint(serverUrl, preferences.getString(KEY_USERNAME, null).orEmpty())
    }

    // Format : IV (12 octets) suivi du texte chiffré et de l'étiquette d'authentification GCM.
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        if (data.size <= IV_SIZE_BYTES) throw GeneralSecurityException("Données chiffrées tronquées")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, data, 0, IV_SIZE_BYTES))
        return cipher.doFinal(data, IV_SIZE_BYTES, data.size - IV_SIZE_BYTES)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Serializable
    private data class StoredTokens(
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresAtMillis: Long,
    )

    private companion object {
        const val PREFERENCES_NAME = "session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_TOKENS = "tokens"

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "onyxfilter_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
