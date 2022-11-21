package dev.arie.tuna

import androidx.compose.ui.graphics.Color

sealed class TunerState(open val note: NotesEnum?, val bgColor: Color) {
    class Down(override val note: NotesEnum) : TunerState(note, OutOfTuneColor)
    class Tuned(override val note: NotesEnum) : TunerState(note, TunedColor)
    class Up(override val note: NotesEnum) : TunerState(note, OutOfTuneColor)
    object NoSound : TunerState(null, Black)
}
