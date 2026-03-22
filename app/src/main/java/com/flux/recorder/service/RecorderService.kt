package com.flux.recorder.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.flux.recorder.R
import com.flux.recorder.core.audio.AudioRecorder
import com.flux.recorder.core.codec.AudioEncoder
import com.flux.recorder.core.codec.MediaMuxerWrapper
import com.flux.recorder.core.codec.VideoEncoder
import com.flux.recorder.core.projection.ScreenCaptureManager
import com.flux.recorder.data.AudioSource
import com.flux.recorder.data.RecordingSettings
import com.flux.recorder.data.RecordingState
import com.flux.recorder.data.ScreenOrientation
import com.flux.recorder.utils.FileManager
import com.flux.recorder.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import java.io.File

/**
 * Foreground service that manages the screen recording process
 */
class RecorderService : Service() {
    
    private val binder = RecorderBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private lateinit var fileManager: FileManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var audioRecorder: AudioRecorder? = null
    
    private var muxer: MediaMuxerWrapper? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    private var audioJob: Job? = null
    
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0

    private var lastHdrState = false
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) {
                checkHdrState()
            }
        }
    }

    companion object {
        private const val TAG = "RecorderService"
        const val ACTION_START_RECORDING = "com.flux.recorder.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.flux.recorder.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.flux.recorder.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.flux.recorder.RESUME_RECORDING"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SETTINGS = "settings"

        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    }
    
    override fun onCreate() {
        super.onCreate()
        fileManager = FileManager(this)
        notificationHelper = NotificationHelper(this)
        screenCaptureManager = ScreenCaptureManager(this)
        
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        
        Log.d(TAG, "RecorderService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val settings = intent.getParcelableExtra<RecordingSettings>(EXTRA_SETTINGS)
                
                if (resultData != null && settings != null) {
                    startRecording(resultCode, resultData, settings)
                }
            }
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun startRecording(resultCode: Int, data: Intent, settings: RecordingSettings) {
        if (_recordingState.value !is RecordingState.Idle) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        // Show processing state immediately for responsive UI
        _recordingState.value = RecordingState.Processing(0)

        try {
            // Start foreground service (must happen on main thread)
            val notification = notificationHelper.createRecordingNotification(0L)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                var foregroundServiceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     foregroundServiceType = foregroundServiceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NotificationHelper.NOTIFICATION_ID, notification, foregroundServiceType)
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }

            // Initialize MediaProjection (must happen on main thread before API calls)
            if (!screenCaptureManager.initializeProjection(resultCode, data)) {
                _recordingState.value = RecordingState.Error(getString(R.string.error_screen_capture))
                stopSelf()
                return
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground/projection", e)
            _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            stopSelf()
            return
        }

        // Heavy initialization in background
        serviceScope.launch(Dispatchers.Default) {
            try {
                // Create output file
                outputFile = fileManager.createRecordingFile()

                // Calculate dimensions based on device screen and quality tier
                val (screenWidth, screenHeight) = screenCaptureManager.getScreenDimensions()
                val (rawW, rawH) = settings.videoQuality.computeDimensions(screenWidth, screenHeight)

                // Apply orientation override
                val (width, height) = when (settings.screenOrientation) {
                    ScreenOrientation.AUTO -> Pair(rawW, rawH)
                    ScreenOrientation.PORTRAIT -> if (rawW < rawH) Pair(rawW, rawH) else Pair(rawH, rawW)
                    ScreenOrientation.LANDSCAPE -> if (rawW > rawH) Pair(rawW, rawH) else Pair(rawH, rawW)
                }

                // Initialize encoder
                val bitrate = settings.calculateBitrate(width, height)
                videoEncoder = VideoEncoder(
                    width,
                    height,
                    bitrate,
                    settings.frameRate.fps,
                    mimeType = settings.videoCodec.mimeType
                )

                val surface = videoEncoder?.prepare()
                if (surface == null) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value = RecordingState.Error(getString(R.string.error_encoder))
                        stopSelf()
                    }
                    return@launch
                }

                // Create virtual display
                val virtualDisplay = screenCaptureManager.createVirtualDisplay(
                    surface,
                    width,
                    height,
                    screenCaptureManager.getScreenDensity()
                )

                if (virtualDisplay == null) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value = RecordingState.Error(getString(R.string.error_virtual_display))
                        stopSelf()
                    }
                    return@launch
                }

                // Setup Audio Encoder & Recorder
                var audioEnabled = false
                Log.d(TAG, "Audio Source Setting: ${settings.audioSource}")

                if (settings.audioSource != AudioSource.NONE) {
                    Log.d(TAG, "Initializing audio encoder and recorder...")
                    audioEncoder = AudioEncoder() // Default settings
                    audioEncoder?.prepare()

                    audioRecorder = AudioRecorder()

                    // Start audio recording with the specified source
                    val success = audioRecorder?.start(
                        screenCaptureManager.getMediaProjection(),
                        settings.audioSource
                    ) ?: false

                    if (success) {
                        audioEnabled = true
                        Log.d(TAG, "Audio recording enabled: ${settings.audioSource}")
                    } else {
                        Log.e(TAG, "Failed to start audio recorder for source: ${settings.audioSource}")
                        audioEncoder?.release()
                        audioEncoder = null
                    }
                } else {
                    Log.w(TAG, "Audio source is NONE - no audio will be recorded")
                }

                // Initialize muxer
                muxer = MediaMuxerWrapper(outputFile!!).apply {
                    prepare()
                    setAudioExpected(audioEnabled)
                }

                // Start recording loop
                startTime = System.currentTimeMillis()
                _recordingState.value = RecordingState.Recording(0)

                recordingJob = serviceScope.launch {
                    recordingLoop()
                }

                if (audioEnabled) {
                    audioJob = serviceScope.launch(Dispatchers.IO) {
                        audioLoop()
                    }
                }

                Log.d(TAG, "Recording started")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
    }
    
    private suspend fun recordingLoop() {
        var videoTrackAdded = false
        var lastNotificationUpdate = 0L
        var lastPauseNotificationUpdate = 0L

        while (coroutineContext.isActive) {
            val state = _recordingState.value
            if (state !is RecordingState.Recording && state !is RecordingState.Paused) break

            if (state is RecordingState.Paused) {
                // Drain encoder output and discard (don't write to muxer)
                val drainOutput = videoEncoder?.getEncodedData()
                if (drainOutput is VideoEncoder.EncoderOutput.Data) {
                    videoEncoder?.releaseOutputBuffer(drainOutput.index)
                }

                // Update notification periodically to keep Focus Island in sync
                val now = System.currentTimeMillis()
                if (now - lastPauseNotificationUpdate >= 1000) {
                    lastPauseNotificationUpdate = now
                    val notification = notificationHelper.createRecordingNotification(
                        state.durationMs, isPaused = true
                    )
                    notificationHelper.updateNotification(notification)
                }
                delay(10)
                continue
            }

            try {
                // Get encoded video data
                val output = videoEncoder?.getEncodedData() ?: VideoEncoder.EncoderOutput.TryAgain

                when (output) {
                    is VideoEncoder.EncoderOutput.FormatChanged -> {
                        Log.d(TAG, "Encoder format changed")
                        val format = videoEncoder?.getOutputFormat()
                        if (format != null && !videoTrackAdded) {
                            muxer?.addVideoTrack(format)
                            videoTrackAdded = true
                            Log.d(TAG, "Video track added to muxer, HDR=${videoEncoder?.isHdrActive}")
                        } else if (format != null && videoTrackAdded) {
                            // Format changed during recording - HDR state may have changed
                            Log.d(TAG, "Format changed during recording, HDR=${videoEncoder?.isHdrActive}")
                        }
                    }
                    is VideoEncoder.EncoderOutput.Data -> {
                        val (buffer, bufferInfo, bufferIndex) = output

                        if (videoTrackAdded && (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer?.writeVideoSample(buffer, bufferInfo)
                        }

                        videoEncoder?.releaseOutputBuffer(bufferIndex)
                    }
                    is VideoEncoder.EncoderOutput.TryAgain -> {
                        // No data available yet, just continue
                    }
                }

                // Update duration
                val currentDuration = System.currentTimeMillis() - startTime - pausedDuration
                _recordingState.value = RecordingState.Recording(currentDuration)

                // Update notification every second
                val currentSecond = currentDuration / 1000
                if (currentSecond != lastNotificationUpdate) {
                    lastNotificationUpdate = currentSecond
                    val notification = notificationHelper.createRecordingNotification(currentDuration)
                    notificationHelper.updateNotification(notification)
                }

                delay(10) // Small delay to prevent busy waiting

            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                break
            }
        }
    }
    
    private suspend fun audioLoop() {
        var audioTrackAdded = false
        val bufferSize = audioRecorder?.getBufferSize() ?: 4096
        val audioBuffer = ByteArray(bufferSize)
        var readCount = 0
        
        Log.d(TAG, "🎤 Starting audio loop with buffer size: $bufferSize")
        
        while (coroutineContext.isActive) {
            val state = _recordingState.value
            if (state !is RecordingState.Recording && state !is RecordingState.Paused) break

            if (state is RecordingState.Paused) {
                // Drain audio buffer to prevent stale data on resume
                audioRecorder?.read(audioBuffer, bufferSize)
                delay(10)
                continue
            }
            try {
                // 1. Read Audio
                val readResult = audioRecorder?.read(audioBuffer, bufferSize) ?: -1
                
                if (readResult > 0) {
                    readCount++
                    if (readCount % 100 == 0) {
                        Log.d(TAG, "🎤 Audio read count: $readCount, bytes: $readResult")
                    }
                    
                    val timestampUs = System.nanoTime() / 1000
                    
                    // 2. Encode Audio
                    audioEncoder?.encode(audioBuffer, readResult, timestampUs)
                    
                    // 3. Retrieve Encoded Data
                    var outputAvailable = true
                    while (outputAvailable) {
                        val output = audioEncoder?.getEncodedData() ?: AudioEncoder.Output.TryAgain
                        
                        when (output) {
                            is AudioEncoder.Output.Data -> {
                                if (output.buffer != null && output.info.size > 0) {
                                    if (audioTrackAdded) {
                                        muxer?.writeAudioSample(output.buffer, output.info)
                                    } else {
                                        Log.w(TAG, "⚠️ Audio data available but track not added yet")
                                    }
                                }
                                audioEncoder?.releaseOutputBuffer(output.index)
                            }
                            is AudioEncoder.Output.FormatChanged -> {
                                val format = audioEncoder?.getOutputFormat()
                                if (format != null && !audioTrackAdded) {
                                    muxer?.addAudioTrack(format)
                                    audioTrackAdded = true
                                    Log.d(TAG, "✅ Audio track added to muxer")
                                }
                            }
                            is AudioEncoder.Output.TryAgain -> {
                                outputAvailable = false
                            }
                        }
                    }
                } else {
                    if (readCount == 0 && readResult == -1) {
                        Log.e(TAG, "❌ Audio recorder returning -1 (no data)")
                    }
                    delay(5)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio loop", e)
                break
            }
        }
        
        Log.d(TAG, "🎤 Audio loop ended. Total reads: $readCount, Track added: $audioTrackAdded")
    }
    
    private fun pauseRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording) {
            pauseStartTime = System.currentTimeMillis()
            screenCaptureManager.pause()
            muxer?.onPause()
            _recordingState.value = RecordingState.Paused(currentState.durationMs)

            val notification = notificationHelper.createRecordingNotification(
                currentState.durationMs, isPaused = true
            )
            notificationHelper.updateNotification(notification)
        }
    }

    private fun resumeRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Paused) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            muxer?.onResume()
            val surface = videoEncoder?.inputSurface
            if (surface != null) {
                screenCaptureManager.resume(surface)
            }
            _recordingState.value = RecordingState.Recording(currentState.durationMs)

            val notification = notificationHelper.createRecordingNotification(currentState.durationMs)
            notificationHelper.updateNotification(notification)
        }
    }

    private fun checkHdrState() {
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return

        // Check if the current display mode or content is HDR
        // On modern Android, the system toggles HDR mode when HDR content is visible
        val isHdr = display.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true && 
                     Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

        // Note: In a real scenario, we might want to check more specific flags 
        // to see if HDR is actually ACTIVE right now.
        if (isHdr != lastHdrState) {
            lastHdrState = isHdr
            if (isHdr) {
                Log.d(TAG, "Dynamic HDR detected - switching encoder to HLG")
                videoEncoder?.switchToHdr(isPq = false) // Default to HLG for better compatibility
            } else {
                Log.d(TAG, "SDR content detected - switching encoder to SDR")
                videoEncoder?.switchToSdr()
            }
        }
    }

    private fun stopRecording() {
        if (_recordingState.value is RecordingState.Idle) return
        Log.d(TAG, "Stopping recording")

        // Update state immediately for responsive UI
        _recordingState.value = RecordingState.Idle

        // Cancel recording jobs
        recordingJob?.cancel()
        recordingJob = null

        audioJob?.cancel()
        audioJob = null

        // Heavy cleanup in background
        val currentVideoEncoder = videoEncoder
        val currentAudioEncoder = audioEncoder
        val currentAudioRecorder = audioRecorder
        val currentMuxer = muxer
        val currentOutputFile = outputFile

        videoEncoder = null
        audioEncoder = null
        audioRecorder = null
        muxer = null

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Signal end of stream
                currentVideoEncoder?.signalEndOfStream()
                delay(100) // Ensure last frames are written

                // Release resources
                currentMuxer?.release()
                currentVideoEncoder?.release()
                currentAudioRecorder?.stop()
                currentAudioEncoder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing resources", e)
            }

            screenCaptureManager.stop()

            // Make sure file is visible in gallery
            currentOutputFile?.let { file ->
                // Android 10+ uses MediaStore, no need to scan private file
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    android.media.MediaScannerConnection.scanFile(
                        this@RecorderService,
                        arrayOf(file.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                }

                val publicFile = fileManager.copyToPublicGallery(file)

                if (publicFile != null) {
                    // Android 9 and below - copy to public directory
                    android.media.MediaScannerConnection.scanFile(
                        this@RecorderService,
                        arrayOf(publicFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    Log.d(TAG, "Copied and scanned public file: ${publicFile.absolutePath}")
                } else {
                    // Android 10+ - already saved via MediaStore
                    Log.d(TAG, "Saved to MediaStore (DCIM folder)")
                }

                try {
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(TAG, "Deleted private original file: $deleted")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete private file", e)
                }
            }

            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            Log.d(TAG, "Recording stopped, file saved: ${currentOutputFile?.absolutePath}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        displayManager.unregisterDisplayListener(displayListener)
        
        if (_recordingState.value !is RecordingState.Idle) {
            stopRecording()
        }
        serviceScope.cancel()
        Log.d(TAG, "RecorderService destroyed")
    }
    
    inner class RecorderBinder : Binder() {
        fun getService(): RecorderService = this@RecorderService
    }
}
