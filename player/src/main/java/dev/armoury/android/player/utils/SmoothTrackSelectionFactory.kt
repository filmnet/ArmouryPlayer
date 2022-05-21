package dev.armoury.android.player.utils

import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.common.collect.ImmutableList

class SmoothTrackSelectionFactory(
    private val defaultBandwidthMeter: BandwidthMeter
) :
    AdaptiveTrackSelection.Factory() {

    private val BLACKLIST_DURATION = 1000L

    override fun createAdaptiveTrackSelection(
        group: TrackGroup,
        tracks: IntArray,
        type: Int,
        bandwidthMeter: BandwidthMeter,
        adaptationCheckpoints: ImmutableList<AdaptiveTrackSelection.AdaptationCheckpoint>
    ): AdaptiveTrackSelection {
        val adaptiveTrackSelection = AdaptiveTrackSelection(
            group,
            tracks,
            defaultBandwidthMeter
        )

        when (TrafficUtils.getConnectionQualityBasedOnNetworkSpeed()) {
            TrafficUtils.Companion.ConnectionQuality.POOR -> {
                for (i in tracks.indices) {
                    if (i >= 4) {
                        adaptiveTrackSelection.blacklist(
                            i,
                            0
                        )
                    } else {
                        adaptiveTrackSelection.blacklist(i, BLACKLIST_DURATION)
                    }
                }
            }
            TrafficUtils.Companion.ConnectionQuality.MODERATE -> {
                for (i in tracks.indices) {
                    if (i >= 2) {
                        adaptiveTrackSelection.blacklist(
                            i,
                            0
                        )
                    } else {
                        adaptiveTrackSelection.blacklist(i, BLACKLIST_DURATION)
                    }
                }
            }
            else -> {
                for (i in tracks.indices) {
                    if (i >= 1) {
                        adaptiveTrackSelection.blacklist(
                            i,
                            0
                        )
                    } else {
                        adaptiveTrackSelection.blacklist(i, BLACKLIST_DURATION)
                    }
                }
            }
        }
        return adaptiveTrackSelection
    }
}