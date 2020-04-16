package com.lucas.darkplayer2.adapters
import com.lucas.darkplayer2.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lucas.darkplayer2.MyDiffUtil
import com.lucas.darkplayer2.Song
import kotlinx.android.synthetic.main.recycler_item.view.*
import es.claucookie.miniequalizerlibrary.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class RecyclerAdapter(var items: List<Song>?, val context: Context):
    RecyclerView.Adapter<ViewHolder>() {
    private val mutableClickedIndex = MutableLiveData<Int>()
    val clickedIndex : LiveData<Int> get() = mutableClickedIndex
    var currentIndex : Int? = null
    var previousIndex : Int? = null
    var playing: Boolean = false
    //val uriList = ArrayList
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(currentIndex == position){
            //show eq view if current position is the song selected
            holder.AlbumArt.visibility = View.INVISIBLE
            holder.EqualizerView.isVisible = true
            holder.EqualizerView.stopBars()
        }else{
            GlobalScope.launch(Dispatchers.IO) {
                val uri = Uri.parse(items?.get(position)?.AlbumArt)
            }
            holder.EqualizerView.isVisible = false
            holder.AlbumArt.visibility = View.VISIBLE
        }
        if(playing){
            holder.EqualizerView.animateBars()
        }else{
            holder.EqualizerView.stopBars()
        }
        holder.ArtistNameText.text = (items?.get(position)?.Artist)
        holder.SongTitleText.text = (items?.get(position)?.Title)
        holder.Layout.setOnClickListener{
            mutableClickedIndex.postValue(position)
        }
    }

    override fun getItemCount(): Int {
        return items?.size ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(
                context
            ).inflate(R.layout.recycler_item, parent, false)
        )
    }

    fun indexIsPlaying(index: Int, playing: Boolean){
        this.playing = playing
        currentIndex = index
        //so that it does not get stuck in that state with bars playing
        previousIndex?.let { notifyItemChanged(it) }
        notifyItemChanged(index)
        previousIndex = index
    }

    fun updateData(items: List<Song>){
        if(this.items != null) {
            val diffResult = DiffUtil.calculateDiff(MyDiffUtil(this.items!!, items))
            diffResult.dispatchUpdatesTo(this)
        }else{
            //if the items are null might as well do it the normal way
            this.items = items
            notifyDataSetChanged()
        }
    }
}
class ViewHolder(view:View): RecyclerView.ViewHolder(view){
    val AlbumArt: ImageView = view.AlbumArt
    val SongTitleText: TextView = view.SongTitle
    val ArtistNameText: TextView = view.ArtistName
    val Layout : ConstraintLayout = view.item_background
    val EqualizerView: EqualizerView = view.equalizer
}