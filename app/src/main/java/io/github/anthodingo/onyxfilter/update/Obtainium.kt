package io.github.anthodingo.onyxfilter.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Mises à jour par [Obtainium](https://obtainium.imranr.dev) : il suit les releases GitHub du dépôt et en
 * installe l'APK. Android n'accepte la mise à jour que si elle est signée par la même clé que la version
 * installée.
 */
object Obtainium {
    const val APP_ID = "io.github.anthodingo.onyxfilter"
    const val REPOSITORY_URL = "https://github.com/AnthoDingo/OnyxFilter-Android"

    /** Site d'Obtainium, ouvert quand il n'est pas installé. */
    const val WEBSITE_URL = "https://obtainium.imranr.dev"

    /** Paquets d'Obtainium : version GitHub et version F-Droid. */
    val PACKAGES = setOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    /**
     * Lien d'import `obtainium://app/<configuration JSON URL-encodée>` : Obtainium affiche la configuration
     * et ajoute l'application après confirmation.
     */
    fun importLink(): String {
        val config = buildJsonObject {
            put("id", APP_ID)
            put("url", REPOSITORY_URL)
            put("author", "AnthoDingo")
            put("name", "OnyxFilter")
        }
        // Comme encodeURIComponent, qu'attend Obtainium : URLEncoder code l'espace en « + ».
        return "obtainium://app/" + URLEncoder.encode(config.toString(), "UTF-8").replace("+", "%20")
    }

    /** L'application a-t-elle été installée (ou mise à jour en dernier) par Obtainium ? */
    fun isInstaller(installerPackageName: String?): Boolean = installerPackageName in PACKAGES
}

/** Installateur de l'application (magasin, navigateur, gestionnaire de fichiers, Obtainium…), si connu. */
@Suppress("DEPRECATION") // getInstallerPackageName, avant Android 11.
internal fun Context.installerPackageName(): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(packageName).installingPackageName
    } else {
        packageManager.getInstallerPackageName(packageName)
    }
}.getOrNull()

/** Ajoute l'application à Obtainium ; sans Obtainium, ouvre son site pour l'installer. */
internal fun Context.openObtainium() {
    if (!view(Obtainium.importLink())) view(Obtainium.WEBSITE_URL)
}

private fun Context.view(uri: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    true
} catch (e: ActivityNotFoundException) {
    false
}
