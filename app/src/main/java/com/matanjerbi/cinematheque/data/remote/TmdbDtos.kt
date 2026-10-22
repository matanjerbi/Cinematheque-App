package com.matanjerbi.cinematheque.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields the app stores. Json is configured with ignoreUnknownKeys.

@Serializable
data class SearchMultiResponse(
    val results: List<SearchItemDto> = emptyList(),
)

/** /search/multi mixes movies, series and people; movies use title/release_date, series name/first_air_date. */
@Serializable
data class SearchItemDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String)

@Serializable
data class CastDto(
    val id: Int,
    val name: String,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 999,
)

@Serializable
data class CrewDto(
    val id: Int,
    val name: String,
    @SerialName("profile_path") val profilePath: String? = null,
    val job: String? = null,
)

@Serializable
data class CreditsDto(
    val cast: List<CastDto> = emptyList(),
    val crew: List<CrewDto> = emptyList(),
)

@Serializable
data class CreatorDto(
    val id: Int,
    val name: String,
    @SerialName("profile_path") val profilePath: String? = null,
)

/** Movie and TV details share one DTO; each endpoint fills its own half. */
@Serializable
data class DetailsDto(
    val id: Int,
    // movie
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    // tv
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("created_by") val createdBy: List<CreatorDto> = emptyList(),
    // shared
    @SerialName("original_language") val originalLanguage: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    val credits: CreditsDto? = null,
)

/** "2014-11-05" → 2014; TMDB sends "" for unknown dates. */
internal fun String?.yearOrNull(): Int? = this?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
