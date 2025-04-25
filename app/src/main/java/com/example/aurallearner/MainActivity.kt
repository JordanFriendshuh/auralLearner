package com.example.aurallearner

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aurallearner.ui.theme.AuralLearnerTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var soundPool: SoundPool
    private lateinit var soundMap: Map<Int, Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttrs)
            .build()

        val map = mutableMapOf<Int, Int>()
        for (midi in 48..84) {
            try {
                assets.openFd("piano/piano_$midi.mp3").use { afd ->
                    map[midi] = soundPool.load(afd, 1)
                }
            } catch (_: Exception) {}
        }
        soundMap = map

        setContent {
            AuralLearnerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IntervalTrainerUI(tts, soundPool, soundMap)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.75f)
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        soundPool.release()
        super.onDestroy()
    }
}

enum class IntervalSetOption(val label: String) {
    Chromatic("Chromatic"),
    Consonant("Consonant"),
    MajorScale("Major Scale"),
    MinorScale("Minor Scale")
}

data class Interval(val name: String, val semitones: Int)

@Composable
fun IntervalTrainerUI(
    tts: TextToSpeech,
    soundPool: SoundPool,
    soundMap: Map<Int, Int>
) {
    var timesBefore by remember { mutableStateOf(3) }
    var timesAfter by remember { mutableStateOf(2) }
    var timesPerKey by remember { mutableStateOf(10f) }
    var timesTogether by remember { mutableStateOf(0f) }
    var intervalSet by remember { mutableStateOf(IntervalSetOption.Chromatic) }
    var speakAtEnd by remember { mutableStateOf(true) }
    var ascending by remember { mutableStateOf(true) }
    var descending by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val keepGoing = remember { mutableStateOf(AtomicBoolean(false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aural Interval Trainer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Sliders
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Before: $timesBefore")
            Slider(
                value = timesBefore.toFloat(),
                onValueChange = { timesBefore = it.toInt() },
                valueRange = 0f..5f,
                steps = 4
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("After: $timesAfter")
            Slider(
                value = timesAfter.toFloat(),
                onValueChange = { timesAfter = it.toInt() },
                valueRange = 0f..5f,
                steps = 4
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Together: ${timesTogether.toInt()}")
            Slider(
                value = timesTogether,
                onValueChange = { timesTogether = it },
                valueRange = 0f..5f,
                steps = 4
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Same key: ${timesPerKey.toInt()}")
            Slider(
                value = timesPerKey,
                onValueChange = { timesPerKey = it },
                valueRange = 1f..50f,
                steps = 49
            )
        }

        // Toggles on one line with concise labels
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = speakAtEnd, onCheckedChange = { speakAtEnd = it })
            Text("Speak at end", modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Checkbox(checked = ascending, onCheckedChange = { ascending = it })
            Text("Asc", modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Checkbox(checked = descending, onCheckedChange = { descending = it })
            Text("Dec")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interval set radio buttons
        Text("Interval Set")
        IntervalSetOption.values().forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = intervalSet == option,
                    onClick = { intervalSet = option }
                )
                Text(option.label)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    keepGoing.value.set(true)
                    isPlaying = true
                    playIntervalsLoop(
                        tts,
                        soundPool,
                        soundMap,
                        timesBefore,
                        timesAfter,
                        1000L,
                        speakAtEnd,
                        ascending,
                        descending,
                        intervalSet,
                        timesPerKey.toInt(),
                        timesTogether.toInt(),
                        keepGoing.value
                    ) { isPlaying = false }
                },
                enabled = !isPlaying
            ) {
                Text("Start")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { keepGoing.value.set(false) },
                enabled = isPlaying
            ) {
                Text("Stop")
            }
        }
    }
}
fun playIntervalsLoop(
    tts: TextToSpeech,
    soundPool: SoundPool,
    soundMap: Map<Int, Int>,
    timesBefore: Int,
    timesAfter: Int,
    postDelay: Long,
    speakAtEnd: Boolean,
    ascending: Boolean,
    descending: Boolean,
    intervalSet: IntervalSetOption,
    timesPerKey: Int,
    timesTogether: Int,
    keepGoing: AtomicBoolean,
    onComplete: () -> Unit
) {
    val noteNames = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    )
    val allIntervals = listOf(
        Interval("Minor Second", 1), Interval("Major Second", 2),
        Interval("Minor Third", 3), Interval("Major Third", 4),
        Interval("Perfect Fourth", 5), Interval("Tritone", 6),
        Interval("Perfect Fifth", 7), Interval("Minor Sixth", 8),
        Interval("Major Sixth", 9), Interval("Minor Seventh", 10),
        Interval("Major Seventh", 11), Interval("Octave", 12)
    )
    val consonantIntervals = listOf(
        Interval("Major Third", 4), Interval("Perfect Fourth", 5),
        Interval("Perfect Fifth", 7), Interval("Major Sixth", 9),
        Interval("Octave", 12)
    )
    val majorScaleIntervals = listOf(
        Interval("Major Second", 2), Interval("Major Third", 4),
        Interval("Perfect Fourth", 5), Interval("Perfect Fifth", 7),
        Interval("Major Sixth", 9), Interval("Major Seventh", 11),
        Interval("Octave", 12)
    )
    val minorScaleIntervals = listOf(
        Interval("Major Second", 2), Interval("Minor Third", 3),
        Interval("Perfect Fourth", 5), Interval("Perfect Fifth", 7),
        Interval("Minor Sixth", 8), Interval("Minor Seventh", 10),
        Interval("Octave", 12)
    )
    val intervals = when (intervalSet) {
        IntervalSetOption.Chromatic -> allIntervals
        IntervalSetOption.Consonant -> consonantIntervals
        IntervalSetOption.MajorScale -> majorScaleIntervals
        IntervalSetOption.MinorScale -> minorScaleIntervals
    }

    fun checkAndSleep(ms: Long) {
        var waited = 0L; val step = 50L
        while (waited < ms && keepGoing.get()) {
            Thread.sleep(step); waited += step
        }
        if (!keepGoing.get()) throw InterruptedException()
    }

    fun play(midi: Int) {
        soundMap[midi]?.let { id ->
            soundPool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    Thread {
        try {
            var inKeyCount = 0; var currentRoot = 60
            while (keepGoing.get()) {
                if (inKeyCount == 0) {
                    currentRoot = (48..72).random()
                    val keyName = noteNames[currentRoot % 12]
                    repeat(5) {
                        play(currentRoot); checkAndSleep(500)
                    }
                    tts.speak("Key of $keyName", TextToSpeech.QUEUE_FLUSH, null, null)
                    checkAndSleep(3000)
                }
                val interval = intervals.random()
                repeat(timesBefore) {
                    if (!keepGoing.get()) return@Thread
                    if (ascending) {
                        play(currentRoot); checkAndSleep(300)
                        play(currentRoot + interval.semitones); checkAndSleep(postDelay)
                    }
                    if (descending) {
                        play(currentRoot + interval.semitones); checkAndSleep(300)
                        play(currentRoot); checkAndSleep(postDelay)
                    }
                }
                if (!keepGoing.get()) return@Thread
                tts.speak(interval.name, TextToSpeech.QUEUE_FLUSH, null, null)
                checkAndSleep(3000)
                repeat(timesAfter) {
                    if (!keepGoing.get()) return@Thread
                    if (ascending) {
                        play(currentRoot); checkAndSleep(300)
                        play(currentRoot + interval.semitones); checkAndSleep(postDelay)
                    }
                    if (descending) {
                        play(currentRoot + interval.semitones); checkAndSleep(300)
                        play(currentRoot); checkAndSleep(postDelay)
                    }
                }
                repeat(timesTogether) {
                    if (!keepGoing.get()) return@Thread
                    play(currentRoot); play(currentRoot + interval.semitones)
                    checkAndSleep(postDelay)
                }
                if (speakAtEnd) tts.speak(interval.name, TextToSpeech.QUEUE_ADD, null, null)
                checkAndSleep(postDelay)
                inKeyCount = (inKeyCount + 1) % timesPerKey
            }
        } catch (_: InterruptedException) {}
        onComplete()
    }.start()
}
