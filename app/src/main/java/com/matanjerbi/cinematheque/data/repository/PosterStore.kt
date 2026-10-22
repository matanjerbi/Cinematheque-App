package com.matanjerbi.cinematheque.data.repository

import android.content.Context
import com.matanjerbi.cinematheque.data.local.entity.MediaType
import com.matanjerbi.cinematheque.data.remote.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads each poster once into app storage, so the diary and collections
 * render offline. Search results use remote URLs and are never stored.
 */
class PosterStore(context: Context, private val http: OkHttpClient) {

    private val dir = File(context.filesDir, "posters").apply { mkdirs() }

    fun fileFor(type: MediaType, tmdbId: Int) = File(dir, "${type.name.lowercase()}_$tmdbId.jpg")

    /** Returns the absolute local path, or null if there's no poster or the download failed. */
    suspend fun download(type: MediaType, tmdbId: Int, remotePath: String?): String? {
        if (remotePath.isNullOrBlank()) return null
        val target = fileFor(type, tmdbId)
        if (target.exists() && target.length() > 0) return target.absolutePath

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(remoteUrl(remotePath)).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val tmp = File(dir, "${target.name}.part")
                    response.body?.byteStream()?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    if (!tmp.renameTo(target)) { tmp.delete(); return@runCatching null }
                    target.absolutePath
                }
            }.getOrNull()
        }
    }

    companion object {
        fun remoteUrl(path: String, size: String = TmdbClient.POSTER_SIZE) =
            "${TmdbClient.IMAGE_BASE_URL}$size$path"
    }
}
