package com.matanjerbi.cinematheque.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = TmdbClient.LANGUAGE,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): SearchMultiResponse

    /** One call per title: details and credits together. */
    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("language") language: String = TmdbClient.LANGUAGE,
        @Query("append_to_response") append: String? = "credits",
    ): DetailsDto

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("language") language: String = TmdbClient.LANGUAGE,
        @Query("append_to_response") append: String? = "credits",
    ): DetailsDto
}

class TmdbClient private constructor(val api: TmdbApi, val httpClient: OkHttpClient) {
    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE = "w342"
        const val LANGUAGE = "he-IL"
        const val FALLBACK_LANGUAGE = "en-US"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun create(apiKey: String, debugLogging: Boolean = false): TmdbClient {
            val http = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    // Only API calls carry the key; poster downloads from image.tmdb.org don't need it.
                    if (request.url.host != "api.themoviedb.org") return@addInterceptor chain.proceed(request)
                    val url = request.url.newBuilder().addQueryParameter("api_key", apiKey).build()
                    chain.proceed(request.newBuilder().url(url).build())
                }
                .apply {
                    if (debugLogging) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return TmdbClient(retrofit.create(TmdbApi::class.java), http)
        }
    }
}
