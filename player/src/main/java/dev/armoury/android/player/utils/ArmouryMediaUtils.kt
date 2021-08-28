package dev.armoury.android.player.utils

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.MappingTrackSelector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util.toLowerInvariant
import dev.armoury.android.player.R
import dev.armoury.android.player.data.VideoInfoModel
import dev.armoury.android.player.data.VideoSpeedModel
import dev.armoury.android.player.data.VideoTrackModel

object ArmouryMediaUtils {

    val autoQualityTrack: VideoTrackModel.Quality by lazy {
        VideoTrackModel.Quality(
            titleRes = R.string.quality_auto,
            width = Int.MAX_VALUE,
            height = Int.MAX_VALUE,
            groupIndex = -1,
            trackIndex = -1,
            default = true
        )
    }

    val noSubtitleTrack: VideoTrackModel.Subtitle by lazy {
        VideoTrackModel.Subtitle(
            titleRes = R.string.no_subtitle,
            groupIndex = -1,
            trackIndex = -1
        )
    }

    val defaultSpeedModel: VideoSpeedModel by lazy {
        VideoSpeedModel(
            title = R.string.speed_normal,
            value = 1.0f
        )
    }

    val speedOptions: List<VideoSpeedModel> by lazy {
        listOf(
            VideoSpeedModel(
                title = R.string.speed_0_5,
                value = 0.5f
            ),
            VideoSpeedModel(
                title = R.string.speed_0_75,
                value = 0.75f
            ),
            VideoSpeedModel(
                title = R.string.speed_normal,
                value = 1f
            ),
            VideoSpeedModel(
                title = R.string.speed_1_25,
                value = 1.25f
            ),
            VideoSpeedModel(
                title = R.string.speed_1_5,
                value = 1.5f
            ),
            VideoSpeedModel(
                title = R.string.speed_1_75,
                value = 1.75f
            ),
            VideoSpeedModel(
                title = R.string.speed_2,
                value = 2f
            )
        )
    }

    fun getRendererIndex(
        trackedGroup: MappingTrackSelector.MappedTrackInfo,
        trackType: Int
    ): Int? {
        for (i in 0 until trackedGroup.rendererCount) {
            val trackGroups = trackedGroup.getTrackGroups(i)
            if (
                trackGroups.length != 0 &&
                trackedGroup.getRendererType(i) == trackType
            ) {
                return i
            }
        }
        return null
    }

    fun buildMediaSource(
        url: String,
        userAgent: String
    ): MediaSource {
        val uri = Uri.parse(url)
        when (getMediaType(uri)) {
            C.TYPE_HLS -> return HlsMediaSource
                .Factory(DefaultHttpDataSourceFactory(userAgent))
                .createMediaSource(uri)
            C.TYPE_OTHER -> return ProgressiveMediaSource
                .Factory(DefaultHttpDataSourceFactory(userAgent))
                .createMediaSource(uri)
            else -> throw Throwable("We are not going to handle this type of video!")
        }
    }

    fun getMediaType(url: String) = getMediaType(Uri.parse(url))

    private fun getMediaType(uri: Uri): Int {
        val fileName = toLowerInvariant(uri.path ?: "") // TODO
        return when {
            fileName.endsWith(".mpd") or fileName.endsWith("mpd") -> C.TYPE_DASH
            fileName.endsWith(".m3u8") or fileName.endsWith("m3u8") -> C.TYPE_HLS
            fileName.matches(Regex.fromLiteral(".*\\.ism(l)?(/manifest(\\(.+\\))?)?")) -> C.TYPE_SS
            else -> C.TYPE_OTHER
        }
    }

    fun getVideoInfo(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?): VideoInfoModel {
        val videoQualities = getVideoTrackList(mappedTrackInfo = mappedTrackInfo)
        val (width, height) = videoQualities?.firstOrNull { it != autoQualityTrack }?.let { track ->
            Pair(track.width, track.height)
        } ?: Pair(null, null)
        return VideoInfoModel(
            width = width,
            height = height,
            qualityTracks = videoQualities,
            audioTracks = getVideoLanguagesList(mappedTrackInfo = mappedTrackInfo),
            subtitleTracks = getVideoSubtitleList(mappedTrackInfo = mappedTrackInfo)
        )
    }

    fun getVideoTrackList(
        mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?
    ): List<VideoTrackModel.Quality>? {
        return mappedTrackInfo?.let {
            val rendererIndex = getRendererIndex(mappedTrackInfo, C.TRACK_TYPE_VIDEO)
            return rendererIndex?.let {
                val videoTracks = ArrayList<VideoTrackModel.Quality>()
                val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                val trackGroupCount = trackGroups.length
                for (i in 0 until trackGroupCount) {
                    val currentGroup = trackGroups[i]
                    val itemsCount = currentGroup.length
                    for (j in 0 until itemsCount) {
                        if (mappedTrackInfo.getTrackSupport(rendererIndex, i, j) ==
                            C.FORMAT_HANDLED
                        ) {
                            val format = currentGroup.getFormat(j)
                            videoTracks.add(
                                getVideoQualityTrack(
                                    format = format,
                                    groupIndex = i,
                                    trackIndex = j
                                )
                            )
                        }
                    }
                }
                videoTracks.add(0, autoQualityTrack)
                videoTracks.sortWith(Comparator { trackInfoModel, t1 -> t1.width - trackInfoModel.width })
                videoTracks
            }
        }
    }

    fun getVideoLanguagesList(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?) =
        mappedTrackInfo?.let {
            getRendererIndex(mappedTrackInfo, C.TRACK_TYPE_AUDIO)?.let { rendererIndex ->
                val videoTracks = ArrayList<VideoTrackModel.Audio>()
                val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                val trackGroupCount = trackGroups.length
                for (i in 0 until trackGroupCount) {
                    val currentGroup = trackGroups[i]
                    val itemsCount = currentGroup.length
                    for (j in 0 until itemsCount) {
                        if (mappedTrackInfo.getTrackSupport(rendererIndex, i, j) ==
                            C.FORMAT_HANDLED
                        ) {
                            val format = currentGroup.getFormat(j)
                            getVideoLanguageTrack(
                                format = format,
                                groupIndex = i,
                                trackIndex = j
                            )?.let { videoTracks.add(it) }
                        }
                    }
                }
                videoTracks
            }
        }

    fun getVideoSubtitleList(mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?) =
        mappedTrackInfo?.let {
            getRendererIndex(mappedTrackInfo, C.TRACK_TYPE_TEXT)?.let { rendererIndex ->
                val videoTracks = ArrayList<VideoTrackModel.Subtitle>()
                val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
                val trackGroupCount = trackGroups.length
                for (i in 0 until trackGroupCount) {
                    val currentGroup = trackGroups[i]
                    val itemsCount = currentGroup.length
                    for (j in 0 until itemsCount) {
                        if (mappedTrackInfo.getTrackSupport(rendererIndex, i, j) ==
                            C.FORMAT_HANDLED
                        ) {
                            val format = currentGroup.getFormat(j)
                            getVideoSubtitleTrack(
                                format = format,
                                groupIndex = i,
                                trackIndex = j
                            )?.let { videoTracks.add(it) }
                        }
                    }
                }
                if (videoTracks.isNotEmpty()) {
                    val booleanDefaultSubtitle = videoTracks.firstOrNull { it.default } != null
                    videoTracks.add(noSubtitleTrack.copy(default = !booleanDefaultSubtitle))
                }
                videoTracks
            }
        }

    private fun getVideoQualityTrack(format: Format, groupIndex: Int, trackIndex: Int) =
        VideoTrackModel.Quality(
            title = "${format.height}p",
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            width = format.width,
            height = format.height
        )

    private fun getVideoLanguageTrack(format: Format, groupIndex: Int, trackIndex: Int) =
        format.label?.let { label ->
            VideoTrackModel.Audio(
                title = label,
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                default = isDefault(format = format)
            )
        }

    private fun getVideoSubtitleTrack(format: Format, groupIndex: Int, trackIndex: Int) =
        format.label?.let { label ->
            VideoTrackModel.Subtitle(
                title = label,
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                default = isDefault(format = format)
            )
        }

    private fun isDefault(format: Format) = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
}