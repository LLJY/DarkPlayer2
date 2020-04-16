package com.lucas.darkplayer2

import android.net.Uri
import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.room.*

@Entity(tableName = "Playlists")
data class PlaylistEntity(
    @NonNull
    @PrimaryKey(autoGenerate = true) var id: Int,
    @NonNull
    @ColumnInfo(name = "Title") var Title: String,
    @NonNull
    @ColumnInfo(name = "Songs") var Songs: String,
    @NonNull
    @ColumnInfo(name = "AlbumArt1") var AlbumArt1: Uri,
    @NonNull
    @ColumnInfo(name = "AlbumArt2") var AlbumArt2: Uri,
    @NonNull
    @ColumnInfo(name = "AlbumArt3") var AlbumArt3: Uri,
    @NonNull
    @ColumnInfo(name = "AlbumArt4") var AlbumArt4: Uri
)
@Dao
interface PlaylistDao{
    //get all playlist, used for playlist page.
    @Query("SELECT * FROM Playlists ORDER BY Title ASC")
    suspend fun getPlaylists() : LiveData<List<PlaylistEntity>>

    //get playlist by id, used for songs screen
    @Query("SELECT * FROM Playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Int): PlaylistEntity

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM Playlists")
    suspend fun nukePlaylists()
}

@Database(entities = arrayOf(PlaylistEntity::class), version = 1, exportSchema = false)
public abstract class PlaylistRoomDatabase : RoomDatabase(){
    abstract fun playlistDao(): PlaylistDao
    companion object{
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: PlaylistRoomDatabase? = null


    }
}