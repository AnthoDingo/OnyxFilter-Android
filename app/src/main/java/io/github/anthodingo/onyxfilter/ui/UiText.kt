package io.github.anthodingo.onyxfilter.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.OnyxFilterException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Texte à afficher, issu des ressources de l'application ou tel que renvoyé par le serveur. */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Raw -> text
}

/** Message d'erreur destiné à l'utilisateur. */
fun OnyxFilterException.toUiText(): UiText = when (this) {
    // Le message du serveur renvoie vers la page « Accès API » : plus utile qu'un texte générique.
    is OnyxFilterException.Unauthorized ->
        serverMessage?.let(UiText::Raw) ?: UiText.Resource(R.string.error_token_rejected)

    is OnyxFilterException.SessionEnded -> UiText.Resource(R.string.error_token_rejected)

    is OnyxFilterException.ApiNotAvailable -> UiText.Resource(R.string.error_api_not_available)

    is OnyxFilterException.Http ->
        if (serverMessage != null) {
            UiText.Resource(R.string.error_http_with_detail, listOf(code, serverMessage))
        } else {
            UiText.Resource(R.string.error_http, listOf(code))
        }

    is OnyxFilterException.Network -> when (cause) {
        is UnknownHostException -> UiText.Resource(R.string.error_unknown_host)
        is SSLException -> UiText.Resource(R.string.error_certificate)
        is SocketTimeoutException -> UiText.Resource(R.string.error_timeout)
        is ConnectException -> UiText.Resource(R.string.error_connection_refused)
        else -> UiText.Resource(R.string.error_network)
    }

    is OnyxFilterException.InvalidResponse -> UiText.Resource(R.string.error_invalid_response)
}
