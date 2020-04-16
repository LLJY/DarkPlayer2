package com.lucas.darkplayer2.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lucas.darkplayer2.Playlist
import com.lucas.darkplayer2.PlaylistEntity
import com.lucas.darkplayer2.R
import kotlinx.android.synthetic.main.playlist_item.view.*
class PlaylistsRecyclerAdapter(var items: List<Playlist>?, val context: Context):
    RecyclerView.Adapter<PlaylistsViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistsViewHolder {
        return PlaylistsViewHolder(LayoutInflater.from(context).inflate(R.layout.playlist_item,parent, false))
    }

    override fun getItemCount(): Int {
        return items?.size ?: 0
    }

    override fun onBindViewHolder(holder: PlaylistsViewHolder, position: Int) {
        holder.Image1.setImageURI( items?.get(position)?.Image1Uri)
        holder.Image2.setImageURI(items?.get(position)?.Image2Uri)
        holder.Image3.setImageURI(items?.get(position)?.Image3Uri)
        holder.Image4.setImageURI(items?.get(position)?.Image4Uri)
        //just some grammar tings
        if(items?.get(position)?.numberOfSongs == 1){
            holder.numberOfSongs.text="${items?.get(position)?.numberOfSongs} Song in Playlist"
        }else{
            holder.numberOfSongs.text = "${items?.get(position)?.numberOfSongs} Songs in Playlist"
        }
        holder.playlistName.text = items?.get(position)?.playlistName

    }
}
class PlaylistsViewHolder(view: View): RecyclerView.ViewHolder(view) {
    val Image1 = view.Image1
    val Image2 = view.Image2
    val Image3 = view.Image3
    val Image4 = view.Image4
    val playlistName = view.playlist_name
    val numberOfSongs = view.playlist_number_of_songs
}