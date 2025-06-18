package com.kino.puber.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponseList<T>(
    val items: List<T>? = null,
    val error: String? = null,
    val status: String? = null
)

@Serializable
data class ApiResponse<T>(
    val item: T? = null,
    val error: String? = null,
    val status: String? = null
)


@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val pagination: Pagination
)

@Serializable
data class Pagination(
    val current: Int,
    val perpage: Int,
    val total: Int,
    @SerialName("total_items") val totalItems: Int
)

@Serializable
data class Item(
    val id: Int,
    val title: String,
    val type: String,
    val year: Int? = null,
    val rating: String? = null,
    val genres: List<Genre>? = null,
    val countries: List<Country>? = null,
    val director: String? = null,
    val cast: String? = null,
    val plot: String? = null,
    val duration: Duration? = null,
    val posters: Posters? = null,
    val trailer: Trailer? = null,
    val quality: Int? = null,
    val ac3: Int? = null,
    val advert: Boolean? = null,
    val subscribed: Boolean? = null,
    @SerialName("in_watchlist") val inWatchlist: Boolean? = null,
    val imdb: String? = null,
    @SerialName("imdb_rating") val imdbRating: String? = null,
    @SerialName("imdb_votes") val imdbVotes: Int? = null,
    val kinopoisk: String? = null,
    @SerialName("kinopoisk_rating") val kinopoiskRating: String? = null,
    @SerialName("kinopoisk_votes") val kinopoiskVotes: Int? = null,
    val langs: String? = null,
    @SerialName("poor_quality") val poorQuality: Boolean? = null,
    @SerialName("rating_percentage") val ratingPercentage: String? = null,
    @SerialName("rating_votes") val ratingVotes: Int? = null,
    val subtype: String? = null,
    val tracklist: List<Tracklist>? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val views: Int? = null,
    val voice: String? = null,
    val finished: Boolean? = null,
    val comments: Int? = null
)

@Serializable
data class Genre(
    val id: Int,
    val title: String
)

@Serializable
data class Country(
    val id: Int,
    val title: String
)

@Serializable
data class Duration(
    val average: String? = null,
    val total: String? = null
)

@Serializable
data class Posters(
    val small: String? = null,
    val medium: String? = null,
    val big: String? = null,
    val wide: String? = null
)

@Serializable
data class Trailer(
    val url: String? = null,
    val quality: String? = null
)

@Serializable
data class Tracklist(
    val artists: String? = null,
    val title: String? = null,
    val url: String? = null
)

@Serializable
data class History(
    val id: Int,
    val item: Item,
    val video: Video? = null,
    val season: Int? = null,
    val time: Int? = null,
    val updated: String? = null
)

@Serializable
data class Video(
    val id: Int,
    val number: Int? = null,
    val title: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class Media(
    val id: Int,
    val title: String? = null,
    val files: List<MediaFile>? = null
)

@Serializable
data class MediaFile(
    val url: String,
    val quality: String? = null,
    val type: String? = null
)

@Serializable
data class Audio(
    val id: Int,
    val lang: String? = null,
    val type: String? = null,
    val author: String? = null
)

@Serializable
data class Subtitle(
    val id: Int,
    val lang: String,
    val url: String,
    val shift: Int? = null
)

@Serializable
data class Author(
    val id: Int,
    val name: String,
    val role: String? = null
)

@Serializable
data class Comment(
    val id: Int,
    val text: String,
    val author: String? = null,
    val date: String? = null,
    val rating: Int? = null
)

@Serializable
data class Bookmark(
    val id: Int,
    val title: String,
    val count: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class KCollection(
    val id: Int,
    val title: String,
    val description: String? = null,
    val count: Int? = null,
    val posters: Posters? = null
)

@Serializable
data class UserInfo(
    val id: Int,
    val username: String,
    val email: String? = null,
    val subscription: Subscription? = null
)

@Serializable
data class Subscription(
    val active: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    val days: Int? = null
)

@Serializable
data class DeviceSettings(
    @SerialName("id") val id: Int?,
    @SerialName("support_ssl") val supportSsl: Boolean? = null,
    @SerialName("support_hevc") val supportHevc: Boolean? = null,
    @SerialName("support_hdr") val supportHdr: Boolean? = null,
    @SerialName("support_4k") val support4k: Boolean? = null,
    @SerialName("mixed_playlist") val mixedPlaylist: Boolean? = null,
    @SerialName("streaming_type") val streamingType: Int? = null,
    @SerialName("server_location") val serverLocation: Int? = null
)

@Serializable
data class WatchingStatus(
    val id: Int,
    val status: Int,
    val time: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

// Additional models based on official API documentation

@Serializable
data class ServerLocation(
    val id: Int,
    val title: String,
    val location: String
)

@Serializable
data class StreamingType(
    val id: Int,
    val title: String
)

@Serializable
data class TranslationType(
    val id: Int,
    val title: String
)

@Serializable
data class QualityType(
    val id: Int,
    val title: String
)

@Serializable
data class VoiceAuthor(
    val id: Int,
    val name: String,
    val ru_name: String? = null
)

@Serializable
data class MediaLinks(
    val playlist: String? = null,
    val subtitles: List<SubtitleLink>? = null
)

@Serializable
data class SubtitleLink(
    val id: Int,
    val lang: String,
    val url: String,
    val shift: Int? = null
)

@Serializable
data class TVChannel(
    val id: Int,
    val title: String,
    val stream: String? = null,
    val epg: List<EPGProgram>? = null
)

@Serializable
data class EPGProgram(
    val id: Int,
    val title: String,
    val start: String,
    val end: String,
    val description: String? = null
)

@Serializable
data class BookmarkFolder(
    val id: Int,
    val title: String,
    val count: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class BookmarkToggleResult(
    val status: String,
    val action: String // "added" or "removed"
)

@Serializable
data class DeviceResponse(
    val status: Int,
    val device: DeviceResponseModel
)

@Serializable
data class DeviceResponseModel(
    val id: Long,
    val title: String,
    val hardware: String,
    val software: String,
    val created: Long,
    val updated: Long,
    val last_seen: Long,
    val is_browser: Boolean,
    val settings: SettingsResponse
)

@Serializable
data class SettingsResponse(
    val supportSsl: SettingValue,
    val supportHevc: SettingValue,
    val supportHdr: SettingValue,
    val support4k: SettingValue,
    val mixedPlaylist: SettingValue,
    val serverLocation: SettingList,
    val streamingType: SettingList
)

@Serializable
data class SettingValue(
    val value: Int,
    val label: String
)

@Serializable
data class SettingList(
    val type: String,
    val value: List<SettingOption>,
    val label: String
)

@Serializable
data class SettingOption(
    val id: Int,
    val label: String,
    val description: String = "",
    val selected: Int
)

@Serializable
data class DeviceInfo(
    val id: String,
    val title: String,
    val hardware: String,
    val software: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_seen") val lastSeen: String? = null
)

@Serializable
data class VoteResult(
    val status: String,
    val rating: Float? = null,
    @SerialName("user_vote") val userVote: Int? = null
)

@Serializable
data class Season(
    val id: Int,
    val number: Int,
    val title: String? = null,
    val episodes: List<Episode>? = null
)

@Serializable
data class Episode(
    val id: Int,
    val number: Int,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null,
    @SerialName("ac3") val hasAC3: Boolean? = null,
    val subtitles: List<SubtitleLink>? = null
)

@Serializable
data class WatchingInfo(
    val time: Int = 0,
    val duration: Int = 0,
    val status: Int = 0, // 0 - not watched, 1 - watched, 2 - will watch
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ItemFiles(
    val id: Int,
    val files: List<VideoFile>
)

@Serializable
data class VideoFile(
    val url: String,
    val quality: String,
    val translation: Translation? = null
)

@Serializable
data class Translation(
    val id: Int,
    val title: String,
    val type: String, // "voice", "sub"
    val lang: String? = null
) 