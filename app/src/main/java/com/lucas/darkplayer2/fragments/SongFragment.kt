package com.lucas.darkplayer2.fragments

import android.media.session.PlaybackState
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lucas.darkplayer2.R
import com.lucas.darkplayer2.ui.main.MainActivity
import com.lucas.darkplayer2.ui.main.MainViewModel
import com.lucas.darkplayer2.adapters.RecyclerAdapter
import kotlinx.android.synthetic.main.songs_layout.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.compat.SharedViewModelCompat.sharedViewModel

/**
 * A placeholder fragment containing a simple view.
 */
class SongFragment : Fragment() {

    val viewmodel: MainViewModel by sharedViewModel(this, MainViewModel::class.java)
    override fun onPause() {
        super.onPause()
        viewmodel.onActivityDestroyed()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //start the background thread
        viewmodel.refreshSongs()
        //set the rV parameters
        var ra = RecyclerAdapter(
            viewmodel.Songs.value,
            requireActivity().applicationContext
        )
        songs_recycler.adapter = ra
        songs_recycler.layoutManager = LinearLayoutManager(requireActivity().applicationContext)
        viewmodel.Songs.observe(viewLifecycleOwner, Observer {
            val Songs = viewmodel.Songs.value
            if (Songs != null) {
                ra.updateData(Songs)
            }
        })
        ra.clickedIndex.observe(viewLifecycleOwner, Observer {
            lifecycleScope.launch(Dispatchers.IO) {
                viewmodel.Songs.value?.let { it1 ->
                    (activity as MainActivity?)?.playServiceSong(
                        it1,
                        it
                    )
                }
            }
        })
        viewmodel.currentIndex.observe(viewLifecycleOwner, Observer {
            //if currentIndex changes, call the adapter method to update the ui
            if(viewmodel.pStatus.value == PlaybackState.STATE_PLAYING) {
                ra.indexIsPlaying(it, true)
            }else{
                ra.indexIsPlaying(it, false)
            }
        })
        viewmodel.pStatus.observe(viewLifecycleOwner, Observer {
            if(it == PlaybackState.STATE_PLAYING) {
                viewmodel.currentIndex.value?.let { it1 -> ra.indexIsPlaying(it1, true) }
            }else{
                viewmodel.currentIndex.value?.let { it1 -> ra.indexIsPlaying(it1, false) }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.songs_layout, container, false)


        return root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(): SongFragment {
            return SongFragment().apply {
            }
        }
    }
}