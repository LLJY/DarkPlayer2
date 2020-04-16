package com.lucas.darkplayer2.ui.main

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.lucas.darkplayer2.PlayerService
import com.lucas.darkplayer2.R
import com.lucas.darkplayer2.Song
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private lateinit var mService: PlayerService
    private var mBound = false
    private var observerSet = false
    var doSeekBarUpdate = true
    val model: MainViewModel by viewModel()
    private val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.LocalBinder
            mService = binder.getService()
            mBound = true
            if(!observerSet) {
                observerSet = true
/*           observe when the service is bound as service may already be running and activity might have been killed
            * which causes the ui to not be up to date if we did this in startService like we did previously.
            */
                mService.currentSong.observe(this@MainActivity, Observer {
                    model.updateCurrentSong(it)
                })
                mService.playbackState.observe(this@MainActivity, Observer {
                    model.updatePStatus(it)
                })
                //observe for timing changes (most active)
                mService.currentPosition.observe(this@MainActivity, Observer {
                    model.updateMsecs(it)
                })
                mService.currentIndex.observe(this@MainActivity, Observer {
                    model.updateIndex(it)
                })
            }
            if(mService.player !=null){
                //if the service is playing, set all the data in the ui
                mService.currentSong.value?.let { model.updateCurrentSong(it) }
                model.updatePStatus(mService.playbackState.value)
                model.updateMsecs(mService.currentPosition.value)
                mService.currentIndex.value?.let { model.updateIndex(it) }
            }
        }

    }
    fun playServiceSong(songList: List<Song>, index: Int){
        lifecycleScope.launch(Dispatchers.IO) {
            model.updateSongList(songList)
            if (mService.currentSong.value == null) {
                startService(index)
            } else {
                mService.playSong(index, songList)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //create notification
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        val name: CharSequence = "Playback Notification"
        val description = "Notification for playback control"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel =
            NotificationChannel("MYFUCKINGNOTIFICATION", name, importance)
        channel.description = description
        //so it does not play sound or vibrate
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.setBypassDnd(true)
        channel.enableLights(false)
        //var s = ContentLoadingProgressBar(null)
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager?.createNotificationChannel(channel)
        //all the standard stuff
        setContentView(R.layout.activity_main)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
        //keep view updated with latest info
        setPlayPause(model.pStatus.value)
        setSongUI(model.currentSong.value)
        setCurrentMsecs(model.msecs.value)
        //expand the bottomsheet onclick
        design_bottom_sheet.setOnClickListener {
            BottomSheetBehavior.from(design_bottom_sheet).state =
                BottomSheetBehavior.STATE_EXPANDED
        }
        //observe if currentsong has changed
        model.currentSong.observe(this, Observer {
            setSongUI(it)
        })
        //observe if playback status has changed
        model.pStatus.observe(this, Observer {
            setPlayPause(it)
        })
        //observe for timing changes (most active)
        model.msecs.observe(this, Observer {
            setCurrentMsecs(it)
        })
        if(model.pStatus.value != PlaybackState.STATE_PLAYING || model.pStatus.value != PlaybackState.STATE_PAUSED){
            BottomSheetBehavior.from(design_bottom_sheet).isHideable = true
            BottomSheetBehavior.from(design_bottom_sheet).state =
                BottomSheetBehavior.STATE_HIDDEN
        }
        val playPauseListener = View.OnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                when (model.pStatus.value) {
                    PlaybackState.STATE_PLAYING -> mService.pausePlayer()
                    PlaybackState.STATE_PAUSED -> mService.resumePlayer()
                    else -> startService()
                }
            }
        }
        //set the onclick listener to the same for both play pauses
        playPauseTop.setOnClickListener(playPauseListener)
        playPauseBottom.setOnClickListener(playPauseListener)
        NextButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                mService.nextSong()
            }
        }
        PrevButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                mService.prevSong()
            }
        }
        Seeker.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //avoid seekbar jumping when tracking touch, which is very weird.
                doSeekBarUpdate=false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //only update when tracking touch
                if(mBound){
                    if (seekBar != null) {
                        mService.seekTo(seekBar.progress)
                        doSeekBarUpdate = true
                    }
                }
            }

        })

    }

    override fun onStart() {
        super.onStart()
        Intent(this, PlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)

        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    fun startService(indexToPlay: Int? = null) {
        if (model.Songs.value != null) {
            val intent = Intent(this, PlayerService::class.java)
            intent.putParcelableArrayListExtra("songlist", ArrayList(model.Songs.value!!))
            if(indexToPlay != null){
                intent.putExtra("SongToPlay", indexToPlay)
            }
            startService(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    inline fun setSongUI(song: Song?) {
        if (song != null) {
            SongName.text = "${song.Title} - ${song.Artist}"
            MainAlbumArt.setImageURI(Uri.parse(song.AlbumArt))
            EndTime.text = String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(song.Duration!!.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(song.Duration!!.toLong()) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(song.Duration!!.toLong())))
            Seeker.max = song.Duration!!.toInt()
            SeekerTop.max = song.Duration!!.toInt()
        }else{
            SongName.text = "No Song Playing"
            MainAlbumArt.setImageResource(R.drawable.music_icon)
            EndTime.text = "--:--"
        }
    }

    inline fun setPlayPause(int: Int?) {
        when (int) {
            PlaybackState.STATE_PLAYING -> {
                playPauseTop.setImageResource(R.drawable.pause)
                playPauseBottom.setImageResource(R.drawable.pause)
                //show the bottomsheet when playing, hide when not playing
                BottomSheetBehavior.from(design_bottom_sheet).isHideable = false
                BottomSheetBehavior.from(design_bottom_sheet).state =
                    BottomSheetBehavior.STATE_COLLAPSED
            }
            PlaybackState.STATE_PAUSED -> {
                playPauseTop.setImageResource(R.drawable.play)
                playPauseBottom.setImageResource(R.drawable.play)
                BottomSheetBehavior.from(design_bottom_sheet).isHideable = false
                BottomSheetBehavior.from(design_bottom_sheet).state =
                    BottomSheetBehavior.STATE_COLLAPSED
            }
            PlaybackState.STATE_STOPPED -> {
                //hide the sheet when not playing.
                BottomSheetBehavior.from(design_bottom_sheet).isHideable = true
                BottomSheetBehavior.from(design_bottom_sheet).state =
                    BottomSheetBehavior.STATE_HIDDEN
            }
            null -> {
                BottomSheetBehavior.from(design_bottom_sheet).isHideable = true
                BottomSheetBehavior.from(design_bottom_sheet).state =
                    BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }
    inline fun setCurrentMsecs(long: Long?){
        if(long !=null){
            //prevent updating seekbar in weird situations
            if(doSeekBarUpdate) {
                //set the text
                CurrentTime.text = String.format(
                    "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(long),
                    TimeUnit.MILLISECONDS.toSeconds(long) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(long))
                )
                //now set the seekbar
                val progress = long.toInt()
                Seeker.progress = progress
                SeekerTop.progress = progress
            }
        }else{
            //ONLY set the current time, End Time will be set with currentSong(which makes more sense)
            CurrentTime.text = "--:--"
            Seeker.progress = 0
            SeekerTop.progress=0
        }
    }
}