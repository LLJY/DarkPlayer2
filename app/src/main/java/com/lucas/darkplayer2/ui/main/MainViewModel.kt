package com.lucas.darkplayer2.ui.main

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.lucas.darkplayer2.R
import com.lucas.darkplayer2.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


class MainViewModel(application: Application) : AndroidViewModel(application) {
    //these are the mutable livedata that will be observed or updated for observers
    private var mutableSongs = MutableLiveData<List<Song>>()
    private var mutableCurrentSong = MutableLiveData<Song>()
    private var mutablepStatus = MutableLiveData<Int>()
    private var mutableMsecs = MutableLiveData<Long>()
    private var mutableIndex = MutableLiveData<Int>()
    //these are the exposed Livedata
    val Songs: LiveData<List<Song>> get() = mutableSongs
    val currentSong: LiveData<Song> get() = mutableCurrentSong
    val pStatus: LiveData<Int> get() = mutablepStatus
    val msecs: LiveData<Long> get() = mutableMsecs
    val currentIndex: LiveData<Int> get()= mutableIndex
    //these are other private variables
    private val handler = Handler()
    private val contentResolver = getApplication<Application>().contentResolver
    /**
     * Observes if content of music has changed, if changed, refresh the music list
     */
    private val contentObserver: ContentObserver = object : ContentObserver(handler) {
        override fun onChange(self: Boolean) {
            Log.d("lalala", "changed!!")
            //this operation is very expensive and should always be done in another thread
            GlobalScope.launch(Dispatchers.IO) {
                getAllSongs()
            }
        }
    }

    /**
     * Get all songs from storage, only for Android Q as there is no support for Duration Otherwise.
     */
    private suspend inline fun getAllSongs() {
        var returnlist = ArrayList<Song>()
        var urilist = ArrayList<String>()
        val returnSongs = GlobalScope.async(Dispatchers.IO) {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
            val list = ArrayList<Song>()
            while (cursor?.moveToNext()!!) {
                /**
                 * Set art as the default album art path of the media and create a drawable from it.
                 * If the drawable is null, the album art does not exist and a placeholder will be set in its place.
                 */
                /**
                 * If SDK version is Android 10 and above, get songs with duration, else, set duration as null
                 */
                if (Build.VERSION.SDK_INT >= 29) {
                    list.add(
                        Song(
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                            "0",
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                        )
                    )
                } else {
                    list.add(
                        Song(
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)),
                            "0",
                            cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                            null
                        )
                    )
                }
            }
            cursor.close()
            list
        }
        //process both in parallel as they both take a significant amount of time to complete.
        val returnUri = GlobalScope.async(Dispatchers.IO) {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
            val list = ArrayList<String>()
            while (cursor?.moveToNext()!!) {
                var art = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    cursor.getLong((cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)))
                ).toString()
                val dummyimage = ImageView(getApplication<Application>().applicationContext)
                dummyimage.setImageURI(Uri.parse(art))
                if (dummyimage.drawable == null) {
                    art =
                        "android.resource://com.lucas.darkplayer2/${R.drawable.ic_action_name}"
                }
                list.add(art)
            }
            cursor.close()
            list
        }
        returnlist = returnSongs.await()
        urilist = returnUri.await()
        for (i in 0 until returnlist.size-1) {
            returnlist[i].AlbumArt = urilist[i]
        }

        if (returnlist != mutableSongs.value) {
            mutableSongs.postValue(returnlist)
        }
    }

    /**
     * Starts the ContentObserver to update livedata if changes are observed.
     */
    fun refreshSongs() {
        /*
        getallsongs whenever refresh is called to manually refresh as contentobserver doesn't always work
         */
        GlobalScope.launch(Dispatchers.IO) {
            getAllSongs()
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
        }
    }

    fun updateCurrentSong(song: Song) {
        if (song != mutableCurrentSong.value) {
            mutableCurrentSong.postValue(song)
        }
    }
    fun updateSongList(songs: List<Song>){
        if(songs != mutableSongs.value) {
            mutableSongs.postValue(songs)
        }
    }

    fun updatePStatus(int: Int?) {
        mutablepStatus.postValue(int)
    }
    fun updateMsecs(long: Long?){
        mutableMsecs.postValue(long)
    }
    //for recyclerview only
    fun updateIndex(index: Int){
        mutableIndex.postValue(index)
    }
    /**
     * this function should be called to unregister any observers when the activity is destroyed
     * this is to avoid registering recievers over and over and over when the screen rotates (memory leak)
     */
    fun onActivityDestroyed() {
        contentResolver.unregisterContentObserver(contentObserver)
    }
}