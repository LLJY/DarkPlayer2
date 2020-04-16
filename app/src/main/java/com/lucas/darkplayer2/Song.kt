package com.lucas.darkplayer2

import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

//duration is an Android 10 feature, only use it on Android 10, hence make it nullable.
@Parcelize
data class Song (var Title: String, var Artist: String, var SongURL: String, var AlbumArt: String, var Album: String, var Duration: String?) : Parcelable
@Serializable
//the reason why I am defining a separate class is to avoid Uri.Parse in recyclerview, which significantly impacts rV performance.
data class SerializeSong(var Title: String, var Artist: String, var SongURL: String, var AlbumArt: String, var Album: String, var Duration: String?)