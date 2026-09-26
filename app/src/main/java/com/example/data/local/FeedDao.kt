package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FeedCommentEntity
import com.example.data.model.FeedPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_posts WHERE scope = :scope ORDER BY createdAt DESC")
    fun getPostsByScope(scope: String): Flow<List<FeedPostEntity>>

    @Query("SELECT * FROM feed_posts WHERE (scope = 'GLOBAL') OR (scope = 'CREW' AND crewId = :crewId) ORDER BY createdAt DESC")
    fun getPostsForCrew(crewId: String): Flow<List<FeedPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: FeedPostEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<FeedPostEntity>)

    @Update
    suspend fun updatePost(post: FeedPostEntity)

    @Query("UPDATE feed_posts SET upvotes = upvotes + 1 WHERE id = :postId")
    suspend fun incrementUpvote(postId: Long)

    @Query("UPDATE feed_posts SET commentsCount = commentsCount + 1 WHERE id = :postId")
    suspend fun incrementCommentsCount(postId: Long)

    @Query("SELECT * FROM feed_comments ORDER BY createdAt ASC")
    fun getAllComments(): Flow<List<FeedCommentEntity>>

    @Query("SELECT * FROM feed_comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPost(postId: Long): Flow<List<FeedCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: FeedCommentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<FeedCommentEntity>)

    @Query("DELETE FROM feed_posts WHERE id = :postId")
    suspend fun deletePost(postId: Long)
}
