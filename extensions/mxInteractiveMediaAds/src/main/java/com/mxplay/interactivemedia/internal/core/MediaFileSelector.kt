package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.internal.api.IMediaSelector
import com.mxplay.interactivemedia.internal.data.model.IMediaFilesProvider
import com.mxplay.interactivemedia.internal.data.model.MediaFile
import com.mxplay.interactivemedia.internal.util.binarySearchFloor
import java.util.*

class MediaFileSelector(private val mimeTypes: List<String>, private val  bitrateKbps: Int? = 0) : IMediaSelector {

    @Throws(IllegalStateException::class)
    override fun selectMediaFile(mediaFilesProvider: IMediaFilesProvider): MediaFile {
        val allMimeTypes = LinkedList(mimeTypes)
        allMimeTypes.add("")
        val groupBy = mediaFilesProvider.getAllMedia().groupBy { it.type ?: "" }
        val desiredBitrate = if (bitrateKbps == null || bitrateKbps == 0) 7000 else bitrateKbps

        allMimeTypes.forEach{ type ->
            groupBy[type]?.let {
                val sortedList = it.sortedWith { o1, o2 ->
                    (o1.bitrate ?: 0).compareTo(o2.bitrate ?: 0)
                }
                val index = sortedList.binarySearchFloor{ e ->
                    (e.bitrate ?: 0).compareTo(desiredBitrate)
                }
                sortedList.elementAtOrNull(index)?.let {
                    e ->  return e
                }
            }
        }
        throw IllegalStateException("Media file selection fail")
    }
}