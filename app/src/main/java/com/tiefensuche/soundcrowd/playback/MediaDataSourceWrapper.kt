package com.tiefensuche.soundcrowd.playback

import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

class MediaDataSourceWrapper(private val mediaDataSource: MediaDataSource) : DataSource {
    private var dataSpec: DataSpec? = null
    private var position: Long = 0

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        position = dataSpec.position
        return mediaDataSource.size
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val read = mediaDataSource.readAt(position, buffer, offset, readLength)
        position += read.toLong()
        return read
    }

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun getUri(): Uri? {
        return dataSpec?.uri
    }

    @Throws(IOException::class)
    override fun close() {
    }
}