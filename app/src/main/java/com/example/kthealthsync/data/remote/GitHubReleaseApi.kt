package com.example.kthealthsync.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>?
) {
    val apkDownloadUrl: String
        get() = assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browserDownloadUrl ?: htmlUrl
}

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("size") val size: Long?
)

interface GitHubReleaseService {
    @GET("repos/oline-tramp/NutriusPrime/releases/latest")
    suspend fun getLatestRelease(
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "NutriusPrime-Android"
    ): Response<GitHubRelease>

    companion object {
        private const val BASE_URL = "https://api.github.com/"

        fun create(): GitHubReleaseService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GitHubReleaseService::class.java)
        }
    }
}
