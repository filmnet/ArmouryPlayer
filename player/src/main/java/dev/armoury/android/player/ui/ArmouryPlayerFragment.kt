package dev.armoury.android.player.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatImageView
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.*
import dev.armoury.android.player.R
import dev.armoury.android.data.ArmouryUiAction
import dev.armoury.android.player.data.PlayerState
import dev.armoury.android.player.data.PlayerUiActions
import dev.armoury.android.player.data.VideoSpeedModel
import dev.armoury.android.player.data.VideoTrackModel
import dev.armoury.android.player.utils.ArmouryMediaUtils
import dev.armoury.android.player.viewmodel.ArmouryPlayerViewModel
import dev.armoury.android.player.widgets.AppTimeBar
import dev.armoury.android.player.widgets.PlayerTimeTextView
import dev.armoury.android.ui.ArmouryFragment
import dev.armoury.android.utils.isPortrait

abstract class ArmouryPlayerFragment<UA : ArmouryUiAction, T : ViewDataBinding, V : ArmouryPlayerViewModel<UA>> :
    ArmouryFragment<UA, T, V>() {

    private var exoPlayer: ExoPlayer? = null
    private var toggleFullScreenButton: AppCompatImageView? = null
    private var isCurrentTimeHandlerRunning = false
    private val playbackCurrentTimeHandler = Handler(Looper.getMainLooper())
    private val playbackCurrentTimeRunnable: Runnable by lazy {
        Runnable {
            onPlaybackCurrentPositionChanged(exoPlayer?.currentPosition)
            playbackCurrentTimeHandler.postDelayed(
                playbackCurrentTimeRunnable,
                1000
            )
        }
    }
    private val adsLoader: ImaAdsLoader by lazy {
        val settings = ImaSdkFactory.getInstance().createImaSdkSettings()
        settings.language = "fa"
        ImaAdsLoader.Builder(requireContext()).setImaSdkSettings(settings).setAdEventListener(adsLoaderEventListener).build()
    }

    private val playerUiActionObserver: Observer<PlayerUiActions?> by lazy {
        Observer<PlayerUiActions?> { action ->
            when (action) {
                is PlayerUiActions.PreparePlayer -> {
                    preparePlayer(
                        url = action.videoFileUrl,
                        requestedPosition = action.requestedPosition,
                        vastUrl = action.vastFileUrl
                    )
                }
                is PlayerUiActions.ShowQualityPicker -> {
                    showQualityPicker(
                        availableQualityTracks = action.availableQualityTracks,
                        selectedQuality = action.currentQuality
                    )
                }
                is PlayerUiActions.ShowSpeedPicker -> {
                    showSpeedPicker(selectedSpeedModel = action.currentSpeedModel)
                }
                is PlayerUiActions.UpdatePlayerParam -> {
                    exoPlayer?.setPlaybackParameters(action.playerParam)
                }
                //  TODO Should be checked later
                is PlayerUiActions.ToggleFullScreen -> {
                    activity.apply {
                        requestedOrientation =
                            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        onScreenRotated(requestedOrientation)
                    }
                }
                is PlayerUiActions.PreparingPlaybackCurrentTimeHandler -> {
                    if (action.start) {
                        if (isCurrentTimeHandlerRunning) return@Observer
                        isCurrentTimeHandlerRunning = true
                        playbackCurrentTimeHandler.postDelayed(
                            playbackCurrentTimeRunnable,
                            1000
                        )
                    } else {
                        isCurrentTimeHandlerRunning = false
                        playbackCurrentTimeHandler.removeCallbacks(playbackCurrentTimeRunnable)
                    }
                }
            }
        }
    }

    private val playerStateObserver: Observer<PlayerState?> by lazy {
        Observer<PlayerState?> { state ->
            when (state) {
                is PlayerState.Preparing.Video -> preparePlayer(
                    url = state.url,
                    requestedPosition = state.requestedPosition
                )
                is PlayerState.Preparing.Vast -> preparePlayer(
                    url = state.url,
                    requestedPosition = state.requestedPosition,
                    vastUrl = state.vastUrl
                )
                is PlayerState.Stop -> stopPlayer()
                is PlayerState.Error -> stopPlayer()
            }
        }
    }

    //  TODO : Find a better way!?
    //  TODO : Maybe customize exo player
    private val timeRelatedViewsVisibilityObserver: Observer<Int?> by lazy {
        Observer<Int?> {
            viewDataBinding.root.findViewById<AppTimeBar>(R.id.exo_progress)?.setForceVisibility(it)
            viewDataBinding.root.findViewById<PlayerTimeTextView>(R.id.exo_position)
                ?.setForceVisibility(it)
            viewDataBinding.root.findViewById<PlayerTimeTextView>(R.id.exo_time_slash)
                ?.setForceVisibility(it)
            viewDataBinding.root.findViewById<PlayerTimeTextView>(R.id.exo_duration)
                ?.setForceVisibility(it)
        }
    }

    private val replayButtonVisibilityObserver: Observer<Int?> by lazy {
        Observer<Int?> {
            viewDataBinding.root.findViewById<View>(R.id.exo_replay)?.visibility = it ?: View.GONE
        }
    }

    private val playerEventListener = object : Player.Listener {

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            viewModel.onPlaybackStateChanged(
                playWhenReady = playWhenReady,
                playbackState = playbackState
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            viewModel.onPlayerError(error)
        }

    }

    private val adsLoaderEventListener = AdEvent.AdEventListener {
        viewModel.onAdEvent(it)
    }

    private val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(activity).build()
    }

    override fun startObserving() {
        super.startObserving()
        viewModel.playerUiActions.observe(this, playerUiActionObserver)
        viewModel.state.observe(this, playerStateObserver)
        viewModel.timeRelatedViewsVisibility.observe(this, timeRelatedViewsVisibilityObserver)
        viewModel.replayButtonVisibility.observe(this, replayButtonVisibilityObserver)
        //  TODO
        viewModel.stopPlayer.observe(this, Observer {
            if (it == true) stopPlayer()
        })
    }

    protected abstract fun showQualityPicker(
        availableQualityTracks: List<VideoTrackModel>,
        selectedQuality: VideoTrackModel
    )

    abstract fun showSpeedPicker(selectedSpeedModel: VideoSpeedModel)

    override fun doOtherTasks() {
        viewDataBinding.root.findViewById<AppCompatImageView>(R.id.exo_toggle_full_screen)?.apply {
            setOnClickListener {
                viewModel.onViewClicked(this.id)
            }
            toggleFullScreenButton = this
        }
        viewDataBinding.root.findViewById<View>(R.id.exo_settings)?.apply {
            setOnClickListener {
                viewModel.onViewClicked(this.id)
            }
        }
        viewDataBinding.root.findViewById<View>(R.id.exo_replay)?.apply {
            setOnClickListener {
                viewModel.onViewClicked(this.id)
            }
        }

        // Make the screen on when using watching a video
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onFragmentStopped(exoPlayer?.currentPosition)
        stopPlayer()
    }

    override fun onStart() {
        super.onStart()
        /**
         * We can use onRestart here, but as we are going to change this page to
         * a fragment, we are calling this function in onStart
         */
        viewModel.onFragmentStarted()
    }

    private fun preparePlayer(
        url: String,
        requestedPosition: Long? = null,
        vastUrl: String? = null
    ) {
        // Set up the factory for media sources, passing the ads loader and ad view providers.
        vastUrl?.let {
            preparePlayerVast(url = url, requestedPosition = requestedPosition, vastUrl = vastUrl)
        } ?: kotlin.run {
            preparePlayerVideo(url = url, requestedPosition = requestedPosition)
        }
    }

    private fun preparePlayerVideo(
        url: String,
        requestedPosition: Long? = null
    ) {
        exoPlayer = ExoPlayer
            .Builder(activity, DefaultRenderersFactory(activity))
            .setTrackSelector(viewModel.adaptiveTrackSelectionFactory)
            .setLoadControl(DefaultLoadControl())
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(getPlayerSeekIncrementMs())
            .setSeekForwardIncrementMs(getPlayerSeekIncrementMs())
            .build() // TODO

        exoPlayer?.apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            playWhenReady = true
            addListener(playerEventListener)
            setPlayer(this)
            setMediaSource(
                ArmouryMediaUtils.buildMediaSource(
                    url = url
                )
            )
            prepare()
            requestedPosition?.let { seekTo(it) }
        }
    }

    private fun preparePlayerVast(
        url: String, requestedPosition: Long? = null,
        vastUrl: String
    ) {
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSource.Factory(requireContext())

        val mediaSourceFactory: MediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setAdsLoaderProvider { adsLoader }
            .setAdViewProvider(getPlayerView())

        exoPlayer = ExoPlayer
            .Builder(activity, DefaultRenderersFactory(activity))
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(viewModel.adaptiveTrackSelectionFactory)
            .setLoadControl(DefaultLoadControl())
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(getPlayerSeekIncrementMs())
            .setSeekForwardIncrementMs(getPlayerSeekIncrementMs())
            .build() // TODO
        adsLoader.setPlayer(exoPlayer)

        exoPlayer?.apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            playWhenReady = true
            addListener(playerEventListener)
            setPlayer(this)
            // Create the MediaItem to play, specifying the content URI and ad tag URI.
            val contentUri = Uri.parse(url)
            val adTagUri = Uri.parse(vastUrl)
            val mediaItem: MediaItem =
                MediaItem.Builder().setUri(contentUri).setAdsConfiguration(MediaItem.AdsConfiguration.Builder(adTagUri).build()).build()
            setMediaItem(mediaItem)
            prepare()
            requestedPosition?.let { seekTo(it) }
        }
    }

    abstract fun setPlayer(simpleExoPlayer: ExoPlayer)

    private fun stopPlayer() {
        adsLoader.setPlayer(null)
        getPlayerView().player = null
        exoPlayer?.apply {
            playWhenReady = false
            stop()
            release()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToggleFullScreenButton()
    }

    protected open fun onScreenRotated(rotation: Int) {

    }

    protected fun pausePlayer() {
        exoPlayer?.playWhenReady = false
    }

    protected fun resumePlayer() {
        exoPlayer?.playWhenReady = true
    }

    private fun updateToggleFullScreenButton() {
        toggleFullScreenButton?.setImageResource(
            if (activity.isPortrait()) {
                R.drawable.ic_fullscreen
            } else {
                R.drawable.ic_fullscreen_exit
            }
        )
    }

    protected open fun onPlaybackCurrentPositionChanged(currentTime: Long?) {

    }

    //  It's going to return the playerView used in layout
    protected abstract fun getPlayerView(): PlayerView

    //  It's going to return the playerView used in layout
    protected abstract fun getPlayerSeekIncrementMs(): Long
}