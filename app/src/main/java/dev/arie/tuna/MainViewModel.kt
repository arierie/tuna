package dev.arie.tuna

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.android.AudioDispatcherFactory.fromDefaultMicrophone
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm.FFT_YIN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainViewModel: ViewModel() {

    private val _tunerState = MutableStateFlow<TunerState>(TunerState.NoSound)

    val tunerState: StateFlow<TunerState>
        get() = _tunerState.asStateFlow()

    private var dispatcher: AudioDispatcher? = null

    private var lastPitchUpdate = 0L

    fun startAudioListener(context: Context) = viewModelScope.launch(Dispatchers.Default) {
        if (!hasMicrophonePermission(context)) return@launch

        dispatcher = fromDefaultMicrophone(SAMPLE_RATE.toInt(), BUFFER_SIZE, BUFFER_OVERLAP)

        val processor = PitchProcessor(FFT_YIN, SAMPLE_RATE, BUFFER_SIZE, pitchDetectionHandler())

        dispatcher?.addAudioProcessor(processor)
        dispatcher?.run()
    }

    fun stopAudioListener() = dispatcher?.stop()

    private fun pitchDetectionHandler() = PitchDetectionHandler { result, audioEvent ->
        viewModelScope.launch(Dispatchers.Default) {
            val pitchInHz = result.pitch.toDouble()
            if (shouldUpdateTunerState(pitchInHz, audioEvent)) {
                val capturedNoteState = getCurrentPitchState(pitchInHz)
                _tunerState.emit(capturedNoteState)

                saveLastUpdatedTime()
            }
        }
    }

    private fun getCurrentPitchState(pitchHz: Double): TunerState {
        val closestFrequency = NotesEnum.getClosestFrequencyInAllNotes(pitchHz)
        val note = NotesEnum.getNoteByFrequency(closestFrequency)

        val diff = if (closestFrequency > pitchHz) {
            abs(closestFrequency - pitchHz).unaryMinus()
        } else {
            abs(pitchHz - closestFrequency)
        }

        return when {
            diff.isInPermittedTolerance() -> TunerState.Tuned(note)
            diff < -0.5 -> TunerState.Down(note)
            else -> TunerState.Up(note)
        }
    }

    private fun shouldUpdateTunerState(pitchInHz: Double, audioEvent: AudioEvent): Boolean {
        return audioEvent.getdBSPL() > MIC_THRESHOLD_IN_DB && pitchInHz != INVALID_PITCH &&
            System.currentTimeMillis() - lastPitchUpdate > MIN_UPDATE_TIME_IN_MS
    }

    private fun saveLastUpdatedTime() {
        lastPitchUpdate = System.currentTimeMillis()
    }

    companion object {
        const val MIC_THRESHOLD_IN_DB = -60 
        const val MIN_UPDATE_TIME_IN_MS = 250
        const val INVALID_PITCH = -1.0
        const val SAMPLE_RATE = 22050F
        const val BUFFER_SIZE = 1024
        const val BUFFER_OVERLAP = 0
    }
}
