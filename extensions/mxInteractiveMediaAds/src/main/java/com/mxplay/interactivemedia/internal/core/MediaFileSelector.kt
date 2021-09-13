package com.mxplay.interactivemedia.internal.core

import com.mxplay.interactivemedia.internal.api.IMediaSelector
import com.mxplay.interactivemedia.internal.data.model.IMediaFilesProvider
import com.mxplay.interactivemedia.internal.data.model.MediaFile
import com.mxplay.interactivemedia.internal.util.binarySearchFloor
import kotlin.collections.HashSet

val mimeTypePrioritySequence = mapOf<String, Int>(
        Pair("application/x-mpegURL", 0),
        Pair("application/dash+xml", 1),
        Pair("video/webm", 2),
        Pair("video/mp4", 3)
)

class MediaFileSelector(private val mimeTypes: List<String>, private val bitrateKbps: Int? = 0) : IMediaSelector {


    @Throws(IllegalStateException::class)
    override fun selectMediaFile(mediaFilesProvider: IMediaFilesProvider): MediaFile {
        val allMimeTypes = HashSet(mimeTypes)
        val desiredBitrate = if (bitrateKbps == null || bitrateKbps == 0) 7000 else bitrateKbps
        val filtered = mediaFilesProvider.getAllMedia().filter { allMimeTypes.contains(it.type) }
        var searchedMediaFile: MediaFile? = null
        val groupByDelivery = filtered.groupBy { it.delivery } // Streaming type are better so groupBy
        groupByDelivery.forEach { entry ->
            val groupByType = entry.value.sortedWith { o1, o2 ->
                ((mimeTypePrioritySequence[o1.type]
                        ?: Int.MAX_VALUE).compareTo(mimeTypePrioritySequence[o2.type]
                        ?: Int.MAX_VALUE))
            }.groupBy { it.type }

            groupByType.values.forEach { list ->
                list.sortedWith { o1, o2 ->
                    (o1.bitrate ?: 0).compareTo(o2.bitrate ?: 0)
                }.forEach { mediaFile ->
                    if (mediaFile.bitrate ?: 0 <= desiredBitrate) {
                        searchedMediaFile = mediaFile
                    }
                }
                if (searchedMediaFile != null) return searchedMediaFile!!
            }
        }


        if (searchedMediaFile == null) {
            val sortedList = filtered.sortedWith { o1, o2 ->
                (o1.bitrate ?: 0).compareTo(o2.bitrate ?: 0)
            }
            val index = sortedList.binarySearchFloor { e ->
                (e.bitrate ?: 0).compareTo(desiredBitrate)
            }
            sortedList.elementAtOrNull(index)?.let { selectedMedia ->
                return selectedMedia;
            }
        } else return searchedMediaFile!!

        throw IllegalStateException("Media file selection fail")
    }
}