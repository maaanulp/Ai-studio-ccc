package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FeedScope {
    CREW,
    GLOBAL
}

enum class FeedTag(val label: String) {
    INTEL("[INTEL]"),
    PLAN("[PLAN]"),
    DISCUSION("[DISCUSIÓN]"),
    ANUNCIO("[ANUNCIO]");

    companion object {
        fun fromString(str: String): FeedTag {
            return when {
                str.contains("PLAN", true) -> PLAN
                str.contains("DISCUS", true) || str.contains("DISCUSS", true) -> DISCUSION
                str.contains("ANUNCIO", true) || str.contains("ANNOUNCE", true) -> ANUNCIO
                else -> INTEL
            }
        }
    }
}

@Entity(tableName = "feed_posts")
data class FeedPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val scope: String = "CREW", // "CREW" or "GLOBAL"
    val crewId: String = "CCC",
    val author: String = "m0lt0rn",
    val title: String = "",
    val tag: String = "INTEL", // "INTEL", "PLAN", "DISCUSIÓN", "ANUNCIO"
    val content: String = "",
    val upvotes: Int = 0,
    val commentsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "feed_comments")
data class FeedCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val remotePostId: String? = null,
    val author: String = "m0lt0rn",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

sealed class HybridFeedItem(open val time: Long) {
    data class UserPostItem(
        val post: FeedPostEntity,
        val comments: List<FeedCommentEntity> = emptyList(),
        val isExpanded: Boolean = false,
        val isUpvoted: Boolean = false
    ) : HybridFeedItem(post.createdAt)

    data class SystemEventItem(
        val id: String,
        val eventType: String, // "TARGET_INGESTED", "WALLET_MATCH", "RANK_MILESTONE", "APPS_UPDATE"
        val contributor: String,
        val crewId: String,
        val targetIp: String,
        val wallet: String = "",
        val stolenCrypto: Long = 0L,
        val details: String = "",
        val eventTime: Long = System.currentTimeMillis()
    ) : HybridFeedItem(eventTime)
}

