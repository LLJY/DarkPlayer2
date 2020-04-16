package com.lucas.darkplayer2

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.lucas.darkplayer2.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.*


class PlayerService : LifecycleService(), MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener {
    private val binder = LocalBinder()
    //lazy is more suitable for this
    var player: MediaPlayer? = null
    val mSession by lazy { MediaSession(this, Context.MEDIA_SESSION_SERVICE) }
    private var bitmap: Bitmap? = null
    lateinit var Songs: List<Song>
    private lateinit var shufflelist: IntArray
    //lateinit var bitmap: Bitmap
    private var mutableCurrentSong = MutableLiveData<Song>()
    private var mutablePlaybackState = MutableLiveData<Int>()
    private var msecs = MutableLiveData<Long>()
    private var mutableRepeat = MutableLiveData<Boolean>()
    private var mutableShuffle = MutableLiveData<Boolean>()
    private var resumePosition = 0
    private var mSessionPlaybackState = PlaybackState.Builder().setActions(
        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    )
    val playbackState: LiveData<Int> get() = mutablePlaybackState
    val currentSong: LiveData<Song> get() = mutableCurrentSong
    val currentPosition: LiveData<Long> get() = msecs
    private var index = 0
    private var mutableCurrentIndex = MutableLiveData<Int>()
    val currentIndex: LiveData<Int> get() = mutableCurrentIndex
    private var concurrentThreadRunning = false
    private var observersRunning = false
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        if(player != null && player?.isPlaying!!){
            //stop playing when service is destroyed
            player?.stop()
            player?.release()
            mSession.release()
        }
        //inform the ui that songs are no longer playing
        mutablePlaybackState.postValue(PlaybackState.STATE_STOPPED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.getParcelableArrayListExtra<Parcelable>("songlist") != null && !intent.hasExtra(
                    "PlayPauseIntent"
                ) && !intent.hasExtra("PrevIntent") && !intent.hasExtra("NextIntent") && !intent.hasExtra("StopIntent")
            ) {
                Songs = intent.getParcelableArrayListExtra("songlist")!!
                //if the value exists, use it, else the index should start at 0
                index = intent.getIntExtra("SongToPlay", 0)
                shufflelist = IntArray(Songs.size)
                for (i in 0..Songs.size - 1) {
                    shufflelist[i] = i
                }
                //do not spawn extra observers if they are already running
                if (!observersRunning) {
                    observersRunning = true
                    mutableCurrentSong.observe(this, Observer {
                        initPlayer()
                        mutableCurrentIndex.postValue(shufflelist[index])
                    })
                    playbackState.observe(this, Observer {
                        updatePlaybackState(it, false)
                    })
                    mutableCurrentSong.postValue(Songs[shufflelist[index]])
                }
            }
            //this section handles the intents sent by notifications
            //just check if the intent exist (if it does, it means the notification action is activated, no need to check the value.
            if (intent.hasExtra("PlayPauseIntent")) {
                if (playbackState.value == PlaybackState.STATE_PLAYING) {
                    pausePlayer()
                } else {
                    resumePlayer()
                }
            }
            if (intent.hasExtra("PrevIntent")) {
                prevSong()
            }
            if (intent.hasExtra("NextIntent")) {
                nextSong()
            }
            if(intent.hasExtra("StopIntent")){
                stopForeground(true)
                stopSelf()
            }
        }
        if(!mSession.isActive){
            //only init if mediasession is not currently active
            initMediaSession()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    private fun initMediaSession(){
        mSession.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                Log.d("sdf", mediaButtonIntent.action.toString())
                return super.onMediaButtonEvent(mediaButtonIntent)
            }

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                seekTo(pos.toInt())
            }

            override fun onPause() {
                super.onPause()
                pausePlayer()
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                prevSong()
            }

            override fun onPlay() {
                super.onPlay()
                if (mutablePlaybackState.value == PlaybackState.STATE_PAUSED) {
                    resumePlayer()
                } else {
                    startPlaying()
                }
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                nextSong()
            }
        })
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        if (!mSession.isActive) {
            mSession.isActive = true
        }
    }

    private fun initPlayer() {
        bitmap =
            Bitmap.createScaledBitmap(
                MediaStore.Images.Media.getBitmap(
                    this.contentResolver,
                    Uri.parse(currentSong.value!!.AlbumArt)
                ), 512, 512, false
            )
        player = MediaPlayer()
        player!!.setOnCompletionListener(this)
        player!!.setOnPreparedListener(this)
        player!!.setDataSource(currentSong.value?.SongURL)
        val metadata = currentSong.value?.Duration?.toLong()?.let {
            MediaMetadata.Builder().putLong(
                MediaMetadata.METADATA_KEY_DURATION,
                it
            ).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap).build()
        }
        mSession.setMetadata(metadata)
        player!!.prepareAsync()
        //Thread that updates necessary data every second.
         if(!concurrentThreadRunning) {
             concurrentThreadRunning = true
             lifecycleScope.launch(Dispatchers.IO) {
                 while (mSession.isActive && player != null && playbackState.value != null) {
                     updatePlaybackState(playbackState.value!!, true)

                     delay(1000)
                 }
             }
             lifecycleScope.launch(Dispatchers.IO) {
                 while (player != null) {
                     //we are doing this in a seperate thread from the main Maintenance thread because this can stall its important duties when player is null.
                     var position = 0
                     var notSuccess = true
                     /* keep looping until we get position, as we are doing this many times, there is a high possibility to throw an exception if we
            *  try multiple times as we did for pausePlayer()
            */
                     while (notSuccess) {
                         try {
                             position = player?.currentPosition!!
                             notSuccess = false
                             //successfully gotten the position!!
                         } catch (ex: Exception) {
                             //do nothing
                         }
                     }
                     msecs.postValue(position.toLong())
                     delay(1000)
                 }
                 if (player == null) {
                     msecs.postValue(null)
                 }
             }
         }
    }

    /**
     * Updates the state of the notification
     */
    fun updatePlaybackState(state: Int, fromCoroutine: Boolean) {
        try {
           player?.currentPosition?.toLong()?.let {
               mSessionPlaybackState
                    .setState(state, it, 1F, SystemClock.elapsedRealtime())
            }
            mSession.setPlaybackState(mSessionPlaybackState.build())
            //updating from the coroutine will cause chaos.
            if (!fromCoroutine) {
                buildNotification()
            }
        } catch (ex: IllegalStateException) {
        }
    }
    fun seekTo(pos: Int){
        if(player !=null) {
            resumePosition = pos
            player?.seekTo(pos)
            updatePlaybackState(playbackState.value!!, false)
        }else{
            //if trying to modify while player not started, reset value to 0
            msecs.postValue(0)
        }
    }

    fun startPlaying() {
        if (player != null) {
            mutablePlaybackState.postValue(PlaybackState.STATE_PLAYING)
            player!!.start()
        } else {
            initPlayer()
        }
    }

    fun playSong(index: Int, songList: List<Song>){
        lifecycleScope.launch(Dispatchers.IO) {
            if (player != null) {
                try {
                    if (player?.isPlaying!!) {
                        player?.stop()
                        player?.release()
                    }
                    shufflelist = IntArray(Songs.size)
                    for (i in Songs.indices) {
                        shufflelist[i] = i
                    }
                    this@PlayerService.index = index
                    Songs = songList
                    mutableCurrentSong.postValue(Songs[shufflelist[index]])
                } catch (ex: java.lang.IllegalStateException) {
                }
            }
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        //sometimes oncomplete is called when the song isn't actually completed, check if it's completed by checking if current position divided by duration > 0 ish
        if((player?.currentPosition)!! / (player?.duration)!! > 0.1) {
            nextSong()
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        startPlaying()
    }

    //this section onwards are public methods of the service for playback controls
    fun shuffle(){
        //keep swapping the values instead of randomly generating as it avoids duplicates.
        val random = Random()
        for (i in shufflelist.indices){
            val randInt =  random.nextInt(shufflelist.size-1)
            val currentVal = shufflelist[i]
            val swapVal = shufflelist[randInt]
            //swap the two
            shufflelist[i] = swapVal
            shufflelist[randInt] = currentVal
        }
    }


    fun nextSong() {
        if(player != null) {
            //do not increment if index is going to approach the max size.
            if(index < Songs.size-2) {
                player?.stop()
                player?.release()
                index++
                mutableCurrentSong.postValue(Songs[shufflelist[index]])
            }else{
                player?.stop()
                player?.release()
                // repeat the same song for now, we will check for repeat in the future
                mutableCurrentSong.postValue(Songs[shufflelist[index]])
            }
        }
    }

    fun prevSong() {
        if(player != null) {
            var position = 0
            //try 4 times to get currentposition(sometimes it errors)
            for (i in 0..3) {
                try {
                    position = player?.currentPosition!!
                    break
                } catch (ex: java.lang.IllegalStateException) {
                    if (i == 2) {
                        throw ex
                    }
                }
            }
            player?.stop()
            player?.release()
            if (index != 0) {
                //3000 is 3s, which is the amount of time I personally like my music to replay instead of going back, give me enough buffer.
                if (position <= 30000) {
                    //decrement the index and postvalue to trigger playing the previous song
                    index--
                    mutableCurrentSong.postValue(Songs[shufflelist[index]])
                } else {
                    //postsong without changing value to trigger a replay
                    mutableCurrentSong.postValue(Songs[shufflelist[index]])
                }
            }
        }else{
            //postsong without changing value to trigger a replay
            mutableCurrentSong.postValue(Songs[shufflelist[index]])
        }
    }

    fun resumePlayer() {
        if (player != null) {
            mutablePlaybackState.postValue(PlaybackState.STATE_PLAYING)
            //player.setVolume(1.0f, 1.0f)
            player!!.seekTo(resumePosition)
            player!!.start()
        } else {
            initPlayer()
        }
    }

    fun pausePlayer() {
        if (player != null) {
            mutablePlaybackState.postValue(PlaybackState.STATE_PAUSED)
            resumePosition = player!!.currentPosition
            player!!.pause()
        } else {
            stopSelf()
        }
    }

    @SuppressLint("WrongConstant")
    private fun buildNotification() {
        //intent to launch app when notification is clicked
        val clickIntent = Intent(this, MainActivity::class.java)
        val clickPIntent = PendingIntent.getActivity(
            this, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        var notification: Notification
        val playPauseIntent = PendingIntent.getService(
            applicationContext,
            0,
            Intent(this, PlayerService::class.java).putExtra("PlayPauseIntent", true),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextIntent = PendingIntent.getService(
            applicationContext,
            2,
            Intent(this, PlayerService::class.java).putExtra("NextIntent", true),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevIntent = PendingIntent.getService(
            applicationContext,
            1,
            Intent(this, PlayerService::class.java).putExtra("PrevIntent", true),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            applicationContext,
            3,
            Intent(this, PlayerService::class.java).putExtra("StopIntent", true),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (playbackState.value == PlaybackState.STATE_PLAYING) {
            notification = Notification.Builder(this, "MYFUCKINGNOTIFICATION")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentSong.value?.Title).setContentText(currentSong.value?.Artist)
                .setLargeIcon(bitmap).setOngoing(true)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(android.R.drawable.ic_media_pause, "Pause", playPauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)
                .setContentIntent(clickPIntent).setShowWhen(false)
                .setSubText(currentSong.value?.Album).setStyle(
                    Notification.MediaStyle().setMediaSession(mSession.sessionToken).setShowActionsInCompactView(
                        0,
                        1,
                        2
                    )
                ).build()
        } else {
            notification = Notification.Builder(this, "MYFUCKINGNOTIFICATION")
                .setSmallIcon(android.R.drawable.ic_media_pause)
                .setContentTitle(currentSong.value?.Title).setContentText(currentSong.value?.Artist)
                .setLargeIcon(bitmap).setOngoing(true)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(android.R.drawable.ic_media_play, "Pause", playPauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)
                .setContentIntent(clickPIntent).setShowWhen(false)
                .setSubText(currentSong.value?.Album).setStyle(
                    Notification.MediaStyle().setMediaSession(mSession.sessionToken).setShowActionsInCompactView(
                        0,
                        1,
                        2
                    )
                ).build()
        }
        stopForeground(false)
        startForeground(42069, notification)
    }

}