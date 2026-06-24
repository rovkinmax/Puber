package com.kino.puber.profile

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Base64
import com.kino.puber.data.repository.CryptoPreferenceRepository

internal class BaselineProfileAuthProvider : ContentProvider() {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = requireNotNull(context)
        val repository = CryptoPreferenceRepository(context.applicationContext)
        val cursor = MatrixCursor(arrayOf(COLUMN_NAME, COLUMN_VALUE_BASE64))

        listOfNotNull(
            ACCESS_TOKEN to repository.getAccessToken(),
            REFRESH_TOKEN to repository.getRefreshToken(),
            USERNAME to repository.getUsername(),
            API_DOMAIN to repository.getApiDomain(),
        ).forEach { (name, value) ->
            if (!value.isNullOrBlank()) {
                cursor.addRow(arrayOf(name, value.base64()))
            }
        }

        return cursor
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = unsupported()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = unsupported()

    private fun String.base64(): String =
        Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun unsupported(): Nothing =
        error("Baseline profile auth provider is read-only")

    private companion object {
        private const val COLUMN_NAME = "name"
        private const val COLUMN_VALUE_BASE64 = "value_base64"

        private const val ACCESS_TOKEN = "access_token"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val USERNAME = "username"
        private const val API_DOMAIN = "api_domain"
    }
}
