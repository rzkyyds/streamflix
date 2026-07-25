package com.streamflixreborn.streamflix.providers

import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Calendar
import java.util.concurrent.TimeUnit

object IdlixProvider : Provider {

    private const val BASE_URL = "https://kisutidlix.zeabur.app/"
    override val baseUrl = BASE_URL
    override val name = "IDLIX"
    override val logo = "https://image.tmdb.org/t/p/w300"
    override val language = "en"

    private val service = IdlixService.build()

    // ─── API Response Models ─────────────────────────────────────────────

    data class ApiItem(
        val title: String?,
        val originalTitle: String?,
        val year: Int?,
        val type: String?,
        val quality: String?,
        val rating: Double?,
        val season: Int?,
        val poster: String?,
        val slug: String?,
        val link: ApiLink?
    )

    data class ApiLink(
        val endpoint: String?,
        val url: String?,
        val thumbnail: String?
    )

    data class ApiSearchResponse(val success: Boolean, val data: List<ApiItem>?)

    data class ApiMovieDetail(
        val title: String?,
        val year: Int?,
        val type: String?,
        val runtime: String?,
        val runtimeMinutes: Int?,
        val overview: String?,
        val poster: String?,
        val backdrop: String?,
        val genres: List<ApiGenre>?,
        val country: String?,
        val language: String?,
        val director: ApiDirector?,
        val cast: List<ApiCast>?,
        val trailer: String?,
        val rating: String?,
        val quality: String?,
        val watchUrl: String?,
        val seasons: List<ApiSeason>?
    )

    data class ApiDetailResponse(val success: Boolean, val data: ApiMovieDetail?)

    data class ApiGenre(val name: String?)

    data class ApiDirector(val name: String?)

    data class ApiCast(val name: String?, val character: String?, val image: String?)

    data class ApiSeason(
        val name: String?,
        val seasonNumber: Int?,
        val episodeCount: Int?,
        val episodes: List<ApiEpisode>?
    )

    data class ApiEpisode(
        val episodeNumber: Int?,
        val title: String?,
        val overview: String?
    )

    data class ApiStreamData(
        val slug: String?,
        val streamUrl: String?,
        val subtitles: List<ApiSubtitle>?,
        val videoId: String?,
        val title: String?,
        val maxHeight: Int?
    )

    data class ApiStreamResponse(val success: Boolean, val data: ApiStreamData?)

    data class ApiSubtitle(val lang: String?, val label: String?, val url: String?)

    data class ApiGenreListResponse(val success: Boolean, val data: List<ApiGenreItem>?)

    data class ApiGenreItem(val name: String?, val slug: String?)

    // ─── Helper Functions ────────────────────────────────────────────────

    private fun ApiItem.toMovie() = Movie(
        id = slug ?: "",
        title = title ?: "",
        quality = quality,
        rating = rating,
        poster = poster,
        released = year?.let { "$it-01-01" }
    )

    private fun ApiItem.toTvShow() = TvShow(
        id = slug ?: "",
        title = title ?: "",
        quality = quality,
        rating = rating,
        poster = poster
    )

    private fun ApiMovieDetail.toMovie(): Movie {
        val genreList = genres?.mapNotNull { it.name?.let { n -> Genre(id = n.lowercase(), name = n) } } ?: listOf()
        val directorList = director?.let { listOf(People(id = it.name ?: "", name = it.name ?: "")) } ?: listOf()
        val castList = cast?.mapNotNull { People(id = it.name ?: "", name = it.name ?: "") } ?: listOf()

        return Movie(
            id = "",
            title = title ?: "",
            overview = overview,
            released = year?.let { "$it-01-01" },
            runtime = runtimeMinutes,
            trailer = trailer,
            quality = quality,
            rating = rating?.toDoubleOrNull(),
            poster = poster,
            banner = backdrop,
            genres = genreList,
            directors = directorList,
            cast = castList
        )
    }

    // ─── Provider Implementation ─────────────────────────────────────────

    override suspend fun getHome(): List<Category> {
        val categories = mutableListOf<Category>()

        // Trending Movies
        try {
            val trending = service.getTrending()
            val movies = trending.data?.map { it.toMovie() }?.take(15) ?: listOf()
            if (movies.isNotEmpty()) {
                categories.add(Category(name = "Trending Movies (IDLIX)", list = movies))
            }
        } catch (_: Exception) {}

        // Cinemaxxi (Recent)
        try {
            val recent = service.getCinemaxxi()
            val items = recent.data?.map { item ->
                if (item.type == "series") item.toTvShow() else item.toMovie()
            }?.take(15) ?: listOf()
            if (items.isNotEmpty()) {
                categories.add(Category(name = "Recently Added", list = items))
            }
        } catch (_: Exception) {}

        // Featured
        try {
            val featured = service.getFeatured()
            val featuredMovies = featured.data
                ?.filter { it.slug != null && it.slug != "undefined" }
                ?.take(10)
                ?.map { it.toMovie() } ?: listOf()
            if (featuredMovies.isNotEmpty()) {
                categories.add(Category(name = Category.FEATURED, list = featuredMovies))
            }
        } catch (_: Exception) {}

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isEmpty()) return listOf()
        val response = service.search(query, page)
        return response.data?.map { item ->
            if (item.type == "series") item.toTvShow() else item.toMovie()
        } ?: listOf()
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        val response = service.getTrending()
        return response.data?.filter { it.type == "movie" }?.map { it.toMovie() } ?: listOf()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val response = service.getTrending()
        return response.data?.filter { it.type == "series" }?.map { it.toTvShow() } ?: listOf()
    }

    override suspend fun getMovie(id: String): Movie {
        val detail = service.getMovie(id)
        return detail.data?.toMovie() ?: throw Exception("Movie not found")
    }

    override suspend fun getTvShow(id: String): TvShow {
        val detail = service.getSeries(id)
        return detail.data?.let { d ->
            val seasons = d.seasons?.mapIndexed { idx, s ->
                Season(
                    id = "${id}_s${s.seasonNumber ?: idx}",
                    number = s.seasonNumber ?: (idx + 1),
                    title = s.name ?: "Season ${s.seasonNumber ?: idx + 1}",
                    episodes = s.episodes?.map { e ->
                        Episode(
                            id = "${id}_s${s.seasonNumber}_e${e.episodeNumber}",
                            number = e.episodeNumber ?: 1,
                            title = e.title
                        )
                    } ?: listOf()
                )
            } ?: listOf()

            TvShow(
                id = id,
                title = d.title ?: "",
                overview = d.overview,
                released = d.year?.let { "$it-01-01" },
                runtime = d.runtimeMinutes,
                trailer = d.trailer,
                quality = d.quality,
                rating = d.rating?.toDoubleOrNull(),
                poster = d.poster,
                banner = d.backdrop,
                seasons = seasons,
                genres = d.genres?.mapNotNull { it.name?.let { n -> Genre(id = n.lowercase(), name = n) } } ?: listOf(),
                cast = d.cast?.mapNotNull { People(id = it.name ?: "", name = it.name ?: "") } ?: listOf()
            )
        } ?: throw Exception("TV Show not found")
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        // IDLIX API doesn't support this directly; return empty
        return listOf()
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        // Return empty genre
        return Genre(id = id, name = id, shows = listOf())
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(id = id, name = id, filmography = listOf())
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return listOf(Video.Server(id = "idlix-direct", name = "IDLIX Stream"))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        // Parse path: "movie/slug" or "series/slug/season/s/episode/e"
        val parts = server.id.split("/")
        val streamResponse = when {
            parts.size >= 2 && parts[0] == "movie" -> service.getMovieStream(parts[1])
            parts.size >= 6 && parts[0] == "series" -> {
                val slug = parts[1]
                val season = parts[3].toIntOrNull() ?: 1
                val episode = parts[5].toIntOrNull() ?: 1
                service.getEpisodeStream(slug, season, episode)
            }
            else -> throw Exception("Invalid stream ID format")
        }

        val data = streamResponse.data
        if (data?.streamUrl == null) throw Exception("No stream URL available")

        return Video(
            source = data.streamUrl,
            subtitles = data.subtitles?.map {
                Video.Subtitle(label = it.label ?: it.lang ?: "Sub", file = it.url ?: "")
            } ?: listOf()
        )
    }

    // ─── Retrofit Service ────────────────────────────────────────────────

    private interface IdlixService {
        companion object {
            fun build(): IdlixService {
                val client = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(IdlixService::class.java)
            }
        }

        @GET("api/search")
        suspend fun search(@Query("q") query: String, @Query("page") page: Int): ApiSearchResponse

        @GET("api/movie/trending")
        suspend fun getTrending(): ApiSearchResponse

        @GET("api/featured")
        suspend fun getFeatured(): ApiSearchResponse

        @GET("api/cinemaxxi")
        suspend fun getCinemaxxi(): ApiSearchResponse

        @GET("api/movie/{slug}")
        suspend fun getMovie(@Path("slug") slug: String): ApiDetailResponse

        @GET("api/series/{slug}")
        suspend fun getSeries(@Path("slug") slug: String): ApiDetailResponse

        @GET("api/movie/{slug}/stream")
        suspend fun getMovieStream(@Path("slug") slug: String): ApiStreamResponse

        @GET("api/series/{slug}/season/{season}/episode/{episode}/stream")
        suspend fun getEpisodeStream(
            @Path("slug") slug: String,
            @Path("season") season: Int,
            @Path("episode") episode: Int
        ): ApiStreamResponse
    }
}
