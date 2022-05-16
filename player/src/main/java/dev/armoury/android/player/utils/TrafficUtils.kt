package dev.armoury.android.player.utils

import android.net.TrafficStats
import java.util.*

class TrafficUtils {

    companion object {
        val GB: Long = 1000000000
        val MB: Long = 1000000
        val KB: Long = 1000

        fun getNetworkSpeed(): String {

            var downloadSpeedOutput = ""
            var units = ""
            val mBytesPrevious = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            val mBytesCurrent = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

            val mNetworkSpeed = mBytesCurrent - mBytesPrevious

            val mDownloadSpeedWithDecimals: Float

            if (mNetworkSpeed >= GB) {
                mDownloadSpeedWithDecimals = mNetworkSpeed.toFloat() / GB.toFloat()
                units = " GB"
            } else if (mNetworkSpeed >= MB) {
                mDownloadSpeedWithDecimals = mNetworkSpeed.toFloat() / MB.toFloat()
                units = " MB"

            } else {
                mDownloadSpeedWithDecimals = mNetworkSpeed.toFloat() / KB.toFloat()
                units = " KB"
            }


            downloadSpeedOutput = if (units != " KB" && mDownloadSpeedWithDecimals < 100) {
                String.format(Locale.US, "%.1f", mDownloadSpeedWithDecimals)
            } else {
                Integer.toString(mDownloadSpeedWithDecimals.toInt())
            }

            return (downloadSpeedOutput + units)

        }

        fun getConnectionQualityBasedOnNetworkSpeed(): ConnectionQuality {
            val mBytesPrevious = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            val mBytesCurrent = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()

            val mNetworkSpeed = mBytesCurrent - mBytesPrevious

            val speed = mNetworkSpeed.toFloat() / KB.toFloat()
            return when {
                speed < 150 -> {
                    ConnectionQuality.POOR
                }
                speed < 400 -> {
                    ConnectionQuality.MODERATE
                }
                speed < 2000 -> {
                    ConnectionQuality.GOOD
                }
                else -> {
                    ConnectionQuality.EXCELLENT
                }
            }

        }

        enum class ConnectionQuality {
            /**
             * DownStream under 150 kbps.
             */
            POOR,

            /**
             * DownStream between 150 and 400 kbps.
             */
            MODERATE,

            /**
             * DownStream between 400 and 2000 kbps.
             */
            GOOD,

            /**
             * DownStream over 2000 kbps.
             */
            EXCELLENT,

            /**
             * Placeholder for unknown DownStream. This is the initial value and will stay at this value
             * if a DownStream cannot be accurately found.
             */
            UNKNOWN
        }

    }
}