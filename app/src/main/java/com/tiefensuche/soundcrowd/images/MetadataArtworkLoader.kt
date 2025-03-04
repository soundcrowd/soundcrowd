package com.tiefensuche.soundcrowd.images

import androidx.media3.common.MediaItem
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.tiefensuche.soundcrowd.waveform.StringKey

/**
 * Returns the artwork ByteArray from [MediaItem] if existent.
 */
internal class MetadataArtworkLoader private constructor() : ModelLoader<MediaItem, ByteArray> {


    override fun buildLoadData(model: MediaItem, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteArray>? {
        return ModelLoader.LoadData(StringKey(model.mediaId), object: DataFetcher<ByteArray> {

            override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteArray>) {
                callback.onDataReady(model.mediaMetadata.artworkData)
            }

            override fun getDataClass(): Class<ByteArray> {
                return ByteArray::class.java
            }

            override fun cleanup() {
                // nothing
            }

            override fun getDataSource(): DataSource {
                return DataSource.LOCAL
            }

            override fun cancel() {
                // nothing
            }

        })
    }

    override fun handles(metadata: MediaItem): Boolean {
        return metadata.mediaMetadata.artworkData != null
    }

    internal class Factory : ModelLoaderFactory<MediaItem, ByteArray> {


        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaItem, ByteArray> {
            return MetadataArtworkLoader()
        }

        override fun teardown() {
            // nothing
        }
    }
}