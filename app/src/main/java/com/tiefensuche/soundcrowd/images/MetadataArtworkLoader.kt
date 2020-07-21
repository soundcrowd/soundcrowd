package com.tiefensuche.soundcrowd.images

import android.graphics.Bitmap
import android.support.v4.media.MediaDescriptionCompat
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.tiefensuche.soundcrowd.waveform.StringKey

/**
 * Returns the artwork bitmap from [MediaDescriptionCompat] if existent.
 */
internal class MetadataArtworkLoader private constructor() : ModelLoader<MediaDescriptionCompat, Bitmap> {


    override fun buildLoadData(model: MediaDescriptionCompat, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap>? {
        return model.mediaId?.let { ModelLoader.LoadData(StringKey(it), object: DataFetcher<Bitmap> {

            override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
                callback.onDataReady(model.iconBitmap)
            }

            override fun getDataClass(): Class<Bitmap> {
                return Bitmap::class.java
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

        })}
    }

    override fun handles(metadata: MediaDescriptionCompat): Boolean {
        return metadata.iconBitmap != null
    }

    internal class Factory : ModelLoaderFactory<MediaDescriptionCompat, Bitmap> {


        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaDescriptionCompat, Bitmap> {
            return MetadataArtworkLoader()
        }

        override fun teardown() {
            // nothing
        }
    }
}