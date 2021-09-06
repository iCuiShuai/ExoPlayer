package com.mxplay.interactivemedia.internal.core

import com.google.android.exoplayer2.util.MimeTypes
import com.mxplay.interactivemedia.api.player.AdMediaInfo
import com.mxplay.interactivemedia.internal.data.model.IMediaFilesProvider
import com.mxplay.interactivemedia.internal.data.model.MediaFile
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*

@RunWith(JUnit4::class)
class MediaFileSelectorTest : TestCase(){

    private val mediaFilesDash = listOf(
            MediaFile().apply {
                width = 854;height = 480; type = "application/dash+xml"; bitrate = 45; delivery = "streaming"
            }
    )

    private val mediaFilesHls = listOf(
            MediaFile().apply {
                width = 854;height = 480; type = "application/x-mpegURL"; bitrate = 45; delivery = "streaming"
            }
    )

    private val mediaFilesOthers = listOf(
            MediaFile().apply {
                width = 176;height = 144; type = "video/3gpp"; bitrate = 79; delivery = "progressive"
            },
            MediaFile().apply {
                width = 640;height = 360; type = "video/mp4"; bitrate = 675; delivery = "progressive"
            },
            MediaFile().apply {
                width = 1920;height = 1080; type = "video/webm"; bitrate = 5033; delivery = "progressive"
            },
            MediaFile().apply {
                width = 1280;height = 720; type = "video/webm"; bitrate = 2456; delivery = "progressive"
            }
    )

    private val mediaFiles = listOf(
            mediaFilesDash,
            mediaFilesHls,
            mediaFilesOthers
    ).flatten()

    class MediaFilesProvider(val mediaFiles : List<MediaFile>) : IMediaFilesProvider{
        var file:AdMediaInfo? = null
        override fun setAdMediaInfo(adMediaInfo: AdMediaInfo) {
            file = adMediaInfo
        }

        override fun getAdMediaInfo(): AdMediaInfo? {
            return file
        }

        override fun getAllMedia(): List<MediaFile> {
            return mediaFiles
        }
    }

    fun getMimeTypes(): MutableList<String> {
       return Collections.unmodifiableList( listOf(
               MimeTypes.VIDEO_MP4,
               MimeTypes.VIDEO_WEBM,
               MimeTypes.VIDEO_H263,
               MimeTypes.AUDIO_MP4,
               MimeTypes.APPLICATION_MPD,
               MimeTypes.APPLICATION_M3U8,
               MimeTypes.AUDIO_MPEG))
    }


    @Test
    fun testChooseBestMediaFile(){
        val mediaFileSelector = MediaFileSelector(getMimeTypes(), 0)
        val selectMediaFile = mediaFileSelector.selectMediaFile(MediaFilesProvider(mediaFiles))
        assertEquals(mediaFilesHls[0], selectMediaFile)
    }

    @Test
    fun testSelectMediaFileDefaultBitrate700(){
        val mediaFileSelector = MediaFileSelector(getMimeTypes(), 700)
        val selectMediaFile = mediaFileSelector.selectMediaFile(MediaFilesProvider(mediaFilesOthers))
        assertEquals(mediaFilesOthers[1], selectMediaFile)
    }

    @Test
    fun testSelectMediaFileDefaultBitrate5500(){
        val mediaFileSelector = MediaFileSelector(getMimeTypes(), 5500)
        val selectMediaFile = mediaFileSelector.selectMediaFile(MediaFilesProvider(mediaFilesOthers))
        assertEquals(mediaFilesOthers[2], selectMediaFile)
    }

    @Test
    fun testChooseAvailableMediaFile(){
        val mediaFileSelector = MediaFileSelector(getMimeTypes(), 0)
        val selectMediaFile = mediaFileSelector.selectMediaFile(MediaFilesProvider(listOf(
                MediaFile().apply {
                    width = 176;height = 144; type = "video/3gpp"; bitrate = 79; delivery = "progressive"
                }
        )))
        assertNotNull(selectMediaFile)
    }


    @Test(expected = IllegalStateException::class)
    fun testMimeTypeNotFound(){
        val mediaFileSelector = MediaFileSelector(listOf(MimeTypes.AUDIO_MPEG), 0)
        val selectMediaFile = mediaFileSelector.selectMediaFile(MediaFilesProvider(mediaFilesOthers))
        assertNull(selectMediaFile)
    }



}