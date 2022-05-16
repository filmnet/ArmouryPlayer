package dev.armoury.android.player.viewmodel

import android.app.Application
import android.os.Handler
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import dev.armoury.android.data.ArmouryUiAction
import dev.armoury.android.lifecycle.SingleLiveEvent
import dev.armoury.android.widget.MessageView
import dev.armoury.android.widget.data.MessageModel
import dev.armoury.android.player.R
import dev.armoury.android.data.ErrorModel
import dev.armoury.android.player.data.*
import dev.armoury.android.player.utils.ArmouryMediaUtils
import dev.armoury.android.player.utils.SmoothTrackSelectionFactory
import dev.armoury.android.viewmodel.ArmouryViewModel

//  TODO : We should moved the player out of this library later.
abstract class ArmouryPlayerViewModel<UI : ArmouryUiAction>(applicationContext: Application) :
    ArmouryViewModel<UI>(applicationContext) {

    protected var videoUrl: String? = null
    protected var vastUrl: String? = null

    protected val _state = MutableLiveData<PlayerState>(PlayerState.Idle)
    val state: LiveData<PlayerState>
        get() = _state

    protected val _enablePlayerController = MutableLiveData<Boolean>(true)
    val enablePlayerController: LiveData<Boolean>
        get() = _enablePlayerController

    private val _controllerVisibility: SingleLiveEvent<Int> = SingleLiveEvent(View.INVISIBLE)
    val controllerVisibility: LiveData<Int>
        get() = _controllerVisibility

    protected val _playerUiActions = SingleLiveEvent<PlayerUiActions?>(value = null)
    val playerUiActions: LiveData<PlayerUiActions?>
        get() = _playerUiActions

    protected val selectedSpeed = SingleLiveEvent(ArmouryMediaUtils.defaultSpeedModel)

    protected val selectedQuality = SingleLiveEvent(ArmouryMediaUtils.autoQualityTrack)

    protected val selectedSubtitle = SingleLiveEvent(ArmouryMediaUtils.noSubtitleTrack)

    protected val selectedAudio = SingleLiveEvent<VideoTrackModel.Audio?>(null)

    val showComingSoon: LiveData<Boolean> = Transformations.map(_state) {
        _state.value is PlayerState.Error.ComingSoon
    }

    /**
     * We need to save the last position of the player
     * if we are playing a video file and resuming
     */
    private var playerLastPosition: Long? = null

    //  TODO Can we merge this variable to the PlayerStates or not
    val stopPlayer: LiveData<Boolean> = Transformations.map(state) {
        state.value is PlayerState.NeedToPrepare || state.value is PlayerState.Error
    }

    val controllerVisibilityListener by lazy {
        PlayerControlView.VisibilityListener { visibility ->
            _controllerVisibility.value = visibility
        }
    }

    protected val hasTimeShift = MutableLiveData<Boolean>()

    /**
     * Playback Report Related
     */
    protected abstract fun needReportPlayback(): Boolean

    private var isReporting = false
    private val playbackReportHandler = Handler()
    private val playbackReportRunnable: Runnable by lazy {
        Runnable {
            sendReportPlaybackIsRequested()
            playbackReportHandler.postDelayed(
                playbackReportRunnable,
                getPlaybackReportInterval()
            )
        }
    }

    protected open fun getPlaybackReportInterval(): Long {
        TODO(reason = "You should override this function if you are going to have the report playback feature")
    }

    protected open fun sendReportPlaybackIsRequested() {
        TODO(reason = "You should override this function if you are going to have the report playback feature")
    }

    protected open fun sendPlaybackErrorLog(error: PlaybackException?) {
        TODO(reason = "You should override this function if you are going to send log of playback error")
    }

    protected open fun sendPlaybackErrorLog(error: ErrorModel?) {
        TODO(reason = "You should override this function if you are going to send log of playback error")
    }

    private fun stopReporting() {
        isReporting = false
        playbackReportHandler.removeCallbacks(playbackReportRunnable)
    }

    protected abstract fun getReportPlaybackRequestCode(): Int?

    protected open fun isSeriousReportPlaybackError(errorModel: ErrorModel): Boolean {
        TODO(reason = "You should override this function if you are going to have the report playback feature")
    }

    private fun startReporting() {
        if (isReporting) return
        isReporting = true
        playbackReportHandler.postDelayed(
            playbackReportRunnable,
            getPlaybackReportInterval()
        )
    }

    /**
     * End of the playback report related
     */

    private fun stopPlaybackCurrentTimeHandler() {
        _playerUiActions.value = PlayerUiActions.PreparingPlaybackCurrentTimeHandler(
            start = false
        )
    }

    private fun startPlaybackCurrentTimeHandler() {
        _playerUiActions.value = PlayerUiActions.PreparingPlaybackCurrentTimeHandler(
            start = true
        )
    }

    val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(applicationContext).build()
    }

    val adaptiveTrackSelectionFactory by lazy {
        DefaultTrackSelector(
            applicationContext,
            SmoothTrackSelectionFactory(defaultBandwidthMeter = bandwidthMeter)
        )
    }

    private val qualityRendererIndex: Int by lazy {
        adaptiveTrackSelectionFactory.currentMappedTrackInfo?.let {
            ArmouryMediaUtils.getRendererIndex(
                it,
                C.TRACK_TYPE_VIDEO
            )
        } ?: -1
    }

    private val subtitleRendererIndex: Int by lazy {
        adaptiveTrackSelectionFactory.currentMappedTrackInfo?.let {
            ArmouryMediaUtils.getRendererIndex(
                it,
                C.TRACK_TYPE_TEXT
            )
        } ?: -1
    }

    private val audioRendererIndex: Int by lazy {
        adaptiveTrackSelectionFactory.currentMappedTrackInfo?.let {
            ArmouryMediaUtils.getRendererIndex(
                it,
                C.TRACK_TYPE_AUDIO
            )
        } ?: -1
    }

    val timeRelatedViewsVisibility: LiveData<Int> = Transformations.map(hasTimeShift) {
        when (it) {
            false -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    val replayButtonVisibility: LiveData<Int> = Transformations.map(state) {
        when (it) {
            PlayerState.Done -> View.VISIBLE
            else -> View.GONE
        }
    }

    private val playbackState = MutableLiveData<Int>(Player.STATE_IDLE)

    //  TODO : The state of the playbackstate should be considered as well
    val showLoadingIndicator: LiveData<Boolean> = Transformations.map(state) {
        when (it) {
            is PlayerState.Buffering,
            is PlayerState.Preparing,
            is PlayerState.Fetching -> true
            else -> false
        }
    }

    //  TODO : Should be checked later
    val showBanner: LiveData<Boolean> = Transformations.map(playbackState) {
        when (it) {
            Player.STATE_BUFFERING,
            Player.STATE_READY -> false
            else -> true
        }
    }

    private fun isBehindLiveWindow(e: PlaybackException): Boolean {
        if (e.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            return false
        }
        var cause: Throwable? = e.cause
        while (cause != null) {
            if (cause is BehindLiveWindowException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun onPlaybackStateChanged(playWhenReady: Boolean, playbackState: Int) {
        this.playbackState.value = playbackState
        when (playbackState) {
            Player.STATE_IDLE -> {
                stopReporting()
                stopPlaybackCurrentTimeHandler()
            }
            Player.STATE_READY -> {
                if (_state.value !is PlayerState.Error) {
                    _state.value =
                        if (playWhenReady) PlayerState.Playing.VideoFile else PlayerState.Pause
                }
                //  TODO Should be checked
                if (needReportPlayback()) {
                    when (playWhenReady) {
                        true -> startReporting()
                        false -> stopReporting()
                    }
                }
                when (playWhenReady) {
                    true -> startPlaybackCurrentTimeHandler()
                    false -> stopPlaybackCurrentTimeHandler()
                }
            }
            Player.STATE_ENDED -> {
                _state.value = PlayerState.Done
                stopReporting()
                stopPlaybackCurrentTimeHandler()
            }
            Player.STATE_BUFFERING -> {
                _state.value = PlayerState.Buffering
            }
        }
    }

    fun onPlayerError(error: PlaybackException?) {
        sendPlaybackErrorLog(error)
        error?.let {
            when {
                isBehindLiveWindow(it) -> {
                    videoUrl?.let { url ->
                        vastUrl?.let { adUrl ->
                            _state.value = PlayerState.Preparing.Vast(url = url, vastUrl = adUrl)
                        } ?: kotlin.run { _state.value = PlayerState.Preparing.Video(url = url) }
                    }
                }
                else -> {
                    _messageModel.value = MessageModel(
                        state = MessageView.States.ERROR,
                        descriptionTextRes = R.string.message_error_playing_video,
                        buttonTextRes = R.string.button_retry
                    )
                    _state.value = PlayerState.Error.Playing(
                        MessageModel(
                            state = MessageView.States.ERROR,
                            descriptionTextRes = R.string.message_error_playing_video,
                            buttonTextRes = R.string.button_retry
                        )
                    )
                }
            }
        }
    }

    fun onAdEvent(adEvent: AdEvent) {
        onAdEventListener(adEvent = adEvent)
    }

    protected open fun onAdEventListener(adEvent: AdEvent) {
        TODO(reason = "You should override this function if you are going to handle ad events")
    }

    protected fun onSpeedSelected(selectedSpeed: VideoSpeedModel) {
        if (this.selectedSpeed.value == selectedSpeed) return
        this.selectedSpeed.value = selectedSpeed
        _playerUiActions.value = PlayerUiActions.UpdatePlayerParam(
            playerParam = PlaybackParameters(selectedSpeed.value, selectedSpeed.value)
        )
    }

    protected fun onQualitySelected(selectedTrack: VideoTrackModel.Quality) {
        if (this.selectedQuality.value == selectedTrack) return
        this.selectedQuality.value = selectedTrack
        val parametersBuilder = adaptiveTrackSelectionFactory.parameters.buildUpon()
        parametersBuilder.setRendererDisabled(qualityRendererIndex, false)
        if (selectedTrack.isAutoQuality()) {
            parametersBuilder.clearSelectionOverrides(qualityRendererIndex)
        } else {
            val override = DefaultTrackSelector.SelectionOverride(
                selectedTrack.groupIndex,
                selectedTrack.trackIndex
            )
            adaptiveTrackSelectionFactory.currentMappedTrackInfo?.getTrackGroups(
                qualityRendererIndex
            )?.let { array ->
                parametersBuilder.setSelectionOverride(
                    qualityRendererIndex,
                    array,
                    override
                )
            }
        }
        adaptiveTrackSelectionFactory.parameters = parametersBuilder.build()
    }

    protected fun onAudioSelected(selectedTrack: VideoTrackModel.Audio) {
        if (this.selectedAudio.value == selectedTrack) return
        this.selectedAudio.value = selectedTrack
        val parametersBuilder = adaptiveTrackSelectionFactory.parameters.buildUpon()
        parametersBuilder.setRendererDisabled(audioRendererIndex, false)

        val override = DefaultTrackSelector.SelectionOverride(
            selectedTrack.groupIndex,
            selectedTrack.trackIndex
        )

        adaptiveTrackSelectionFactory.currentMappedTrackInfo?.getTrackGroups(audioRendererIndex)
            ?.let { array ->
                parametersBuilder.setSelectionOverride(
                    audioRendererIndex,
                    array,
                    override
                )
            }

        adaptiveTrackSelectionFactory.parameters = parametersBuilder.build()
    }

    protected fun onSubtitleSelected(selectedTrack: VideoTrackModel.Subtitle) {
        if (this.selectedSubtitle.value == selectedTrack) return
        this.selectedSubtitle.value = selectedTrack
        val parametersBuilder = adaptiveTrackSelectionFactory.parameters.buildUpon()
        if (selectedTrack.isNoSubTitle()) {
            parametersBuilder.clearSelectionOverrides(subtitleRendererIndex)
            parametersBuilder.setRendererDisabled(subtitleRendererIndex, true)
        } else {
            parametersBuilder.setRendererDisabled(subtitleRendererIndex, false)
            val override = DefaultTrackSelector.SelectionOverride(
                selectedTrack.groupIndex,
                selectedTrack.trackIndex
            )
            adaptiveTrackSelectionFactory.currentMappedTrackInfo?.getTrackGroups(
                subtitleRendererIndex
            )?.let { array ->
                parametersBuilder.setSelectionOverride(
                    subtitleRendererIndex,
                    array,
                    override
                )
            }
        }
        adaptiveTrackSelectionFactory.parameters = parametersBuilder.build()
    }

    fun onViewClicked(id: Int) {
        when (id) {
            R.id.exo_settings -> {
                handleClickSettingButton()
            }
            R.id.exo_toggle_full_screen -> {
                _playerUiActions.value = PlayerUiActions.ToggleFullScreen
            }
            R.id.exo_replay -> {
                videoUrl?.let {
                    _playerUiActions.value =
                        PlayerUiActions.PreparePlayer(videoFileUrl = it, vastFileUrl = vastUrl)
                }
            }
        }
    }

    protected open fun handleClickSettingButton() {
        TODO("Should be implemented")
    }

    protected fun onSpeedOptionSelected() {
        _playerUiActions.value = PlayerUiActions.ShowSpeedPicker(
            currentSpeedModel = selectedSpeed.value!!
        )
    }

    //  TODO Performance Improvement needed
    protected fun onQualityOptionSelected() {
        _playerUiActions.value = PlayerUiActions.ShowQualityPicker(
            currentQuality = selectedQuality.value!!,
            availableQualityTracks = ArmouryMediaUtils.getVideoTrackList(
                adaptiveTrackSelectionFactory.currentMappedTrackInfo
            ) ?: ArrayList()
        )
    }

    fun onFragmentStopped(playerPosition: Long?) {
        _state.value = PlayerState.Pause
        if (hasTimeShift.value == true) {
            playerLastPosition = playerPosition
        }
    }

    fun onFragmentStarted() {
        if (_state.value == PlayerState.Pause) {
            videoUrl?.let { url ->
                vastUrl?.let { vastUrl ->
                    _state.value = PlayerState.Preparing.Vast(
                        url = url,
                        requestedPosition = playerLastPosition,
                        vastUrl = vastUrl
                    )
                } ?: kotlin.run {
                    _state.value = PlayerState.Preparing.Video(
                        url = url,
                        requestedPosition = playerLastPosition
                    )
                }
            }
        }
    }

    override fun onCleared() {
        if (needReportPlayback()) stopReporting()
        stopPlaybackCurrentTimeHandler()
        super.onCleared()
    }

    override fun handleErrorInChild(errorModel: ErrorModel) {
        when (errorModel.requestCode) {
            getReportPlaybackRequestCode() -> if (isSeriousReportPlaybackError(errorModel = errorModel)) onSeriousErrorOccurred(
                errorModel
            )
            else -> onSeriousErrorOccurred(errorModel)
        }
    }

    protected fun onSeriousErrorOccurred(errorModel: ErrorModel) {
        sendPlaybackErrorLog(errorModel)
        stopReporting()
        stopPlaybackCurrentTimeHandler()
        _state.value = PlayerState.Error.Playing(messageModel = errorModel.messageModel)
    }
}
