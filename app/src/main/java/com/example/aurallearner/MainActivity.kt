package com.example.aurallearner

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .setMaxStreams(8)
            .setAudioAttributes(audioAttrs)
            .build()

        val map = mutableMapOf<Int, Int>()
        for (midi in 48..84) {
            try {
                assets.openFd("piano/piano_$midi.ogg").use { afd ->
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
                    AuralLearnerApp(tts, soundPool, soundMap)
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

// ─── App Shell ───────────────────────────────────────────────────────────────

@Composable
fun AuralLearnerApp(tts: TextToSpeech, soundPool: SoundPool, soundMap: Map<Int, Int>) {
    var selectedTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Intervals") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Chords") })
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> IntervalTrainerUI(tts, soundPool, soundMap)
                1 -> ChordTrainerUI(tts, soundPool, soundMap)
            }
        }
    }
}

// ─── Interval Mode ───────────────────────────────────────────────────────────

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
    var ascending by remember { mutableStateOf(true) }
    var descending by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val keepGoing = remember { mutableStateOf(AtomicBoolean(false)) }

    DisposableEffect(Unit) {
        onDispose { keepGoing.value.set(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Interval Trainer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Before: $timesBefore")
            Slider(value = timesBefore.toFloat(), onValueChange = { timesBefore = it.toInt() }, valueRange = 0f..5f, steps = 4)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("After: $timesAfter")
            Slider(value = timesAfter.toFloat(), onValueChange = { timesAfter = it.toInt() }, valueRange = 0f..5f, steps = 4)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Together: ${timesTogether.toInt()}")
            Slider(value = timesTogether, onValueChange = { timesTogether = it }, valueRange = 0f..5f, steps = 4)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Same key: ${timesPerKey.toInt()}")
            Slider(value = timesPerKey, onValueChange = { timesPerKey = it }, valueRange = 1f..50f, steps = 49)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ascending, onCheckedChange = { ascending = it })
            Text("Asc", modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Checkbox(checked = descending, onCheckedChange = { descending = it })
            Text("Dec")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Interval Set")
        IntervalSetOption.values().forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = intervalSet == option, onClick = { intervalSet = option })
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
                        tts, soundPool, soundMap,
                        timesBefore, timesAfter, 1000L,
                        ascending, descending,
                        intervalSet, timesPerKey.toInt(),
                        timesTogether.toInt(), keepGoing.value
                    ) { isPlaying = false }
                },
                enabled = !isPlaying
            ) { Text("Start") }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { keepGoing.value.set(false) }, enabled = isPlaying) { Text("Stop") }
        }
    }
}

// ─── Chord Mode ──────────────────────────────────────────────────────────────

enum class ChordProgressionSetOption(val label: String, val isMinor: Boolean = false) {
    IIVVOnly("I  IV  V"),
    IIVVvi("I  IV  V  vi"),
    MajorFull("Major (Full)"),
    Minor("Minor", isMinor = true)
}

data class ChordDef(val spokenName: String, val rootOffset: Int, val intervals: List<Int>)
data class ChordProgression(val chords: List<ChordDef>)

// Major key chords (all diatonic)
private val I_CHORD   = ChordDef("One",         0, listOf(0, 4, 7))
private val ii_CHORD  = ChordDef("Minor Two",   2, listOf(0, 3, 7))
private val iii_CHORD = ChordDef("Minor Three", 4, listOf(0, 3, 7))
private val IV_CHORD  = ChordDef("Four",        5, listOf(0, 4, 7))
private val V_CHORD   = ChordDef("Five",        7, listOf(0, 4, 7))
private val vi_CHORD  = ChordDef("Minor Six",   9, listOf(0, 3, 7))

// Minor key chords
private val i_MIN   = ChordDef("Minor One",   0, listOf(0, 3, 7))
private val iv_MIN  = ChordDef("Minor Four",  5, listOf(0, 3, 7))
private val v_MIN   = ChordDef("Minor Five",  7, listOf(0, 3, 7))
private val VI_MAJ  = ChordDef("Six",         8, listOf(0, 4, 7))
private val VII_MAJ = ChordDef("Seven",       10, listOf(0, 4, 7))
// V_CHORD (major Five) is shared — used as the harmonic minor dominant

@Composable
fun ChordTrainerUI(
    tts: TextToSpeech,
    soundPool: SoundPool,
    soundMap: Map<Int, Int>
) {
    var timesBefore by remember { mutableStateOf(2) }
    var timesAfter by remember { mutableStateOf(2) }
    var timesPerKey by remember { mutableStateOf(10f) }
    var progressionSet by remember { mutableStateOf(ChordProgressionSetOption.IIVVOnly) }
    var isPlaying by remember { mutableStateOf(false) }
    val keepGoing = remember { mutableStateOf(AtomicBoolean(false)) }

    DisposableEffect(Unit) {
        onDispose { keepGoing.value.set(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Chord Trainer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Before: $timesBefore")
            Slider(value = timesBefore.toFloat(), onValueChange = { timesBefore = it.toInt() }, valueRange = 0f..5f, steps = 4)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("After: $timesAfter")
            Slider(value = timesAfter.toFloat(), onValueChange = { timesAfter = it.toInt() }, valueRange = 0f..5f, steps = 4)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Same key: ${timesPerKey.toInt()}")
            Slider(value = timesPerKey, onValueChange = { timesPerKey = it }, valueRange = 1f..50f, steps = 49)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Progression Set")
        ChordProgressionSetOption.values().forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = progressionSet == option, onClick = { progressionSet = option })
                Text(option.label)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    keepGoing.value.set(true)
                    isPlaying = true
                    playChordLoop(
                        tts, soundPool, soundMap,
                        timesBefore, timesAfter,
                        progressionSet,
                        timesPerKey.toInt(), keepGoing.value
                    ) { isPlaying = false }
                },
                enabled = !isPlaying
            ) { Text("Start") }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { keepGoing.value.set(false) }, enabled = isPlaying) { Text("Stop") }
        }
    }
}

// ─── Interval Playback Loop ───────────────────────────────────────────────────

fun playIntervalsLoop(
    tts: TextToSpeech,
    soundPool: SoundPool,
    soundMap: Map<Int, Int>,
    timesBefore: Int,
    timesAfter: Int,
    postDelay: Long,
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
        soundMap[midi]?.let { id -> soundPool.play(id, 1f, 1f, 1, 0, 1f) }
    }

    Thread {
        try {
            var inKeyCount = 0; var currentRoot = 60
            while (keepGoing.get()) {
                if (inKeyCount == 0) {
                    currentRoot = (48..72).random()
                    val keyName = noteNames[currentRoot % 12]
                    repeat(5) { play(currentRoot); checkAndSleep(500) }
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
                checkAndSleep(postDelay)
                inKeyCount = (inKeyCount + 1) % timesPerKey
            }
        } catch (_: InterruptedException) {}
        onComplete()
    }.start()
}

// ─── Chord Playback Loop ──────────────────────────────────────────────────────

fun playChordLoop(
    tts: TextToSpeech,
    soundPool: SoundPool,
    soundMap: Map<Int, Int>,
    timesBefore: Int,
    timesAfter: Int,
    progressionSet: ChordProgressionSetOption,
    timesPerKey: Int,
    keepGoing: AtomicBoolean,
    onComplete: () -> Unit
) {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    val iivvProgressions = listOf(
        ChordProgression(listOf(I_CHORD, IV_CHORD, V_CHORD)),
        ChordProgression(listOf(I_CHORD, V_CHORD, IV_CHORD)),
        ChordProgression(listOf(I_CHORD, IV_CHORD, I_CHORD, V_CHORD)),
        ChordProgression(listOf(IV_CHORD, V_CHORD, I_CHORD)),
        ChordProgression(listOf(I_CHORD, I_CHORD, IV_CHORD, V_CHORD)),
    )
    val iivvviProgressions = listOf(
        ChordProgression(listOf(I_CHORD, V_CHORD, vi_CHORD, IV_CHORD)),   // axis
        ChordProgression(listOf(I_CHORD, IV_CHORD, vi_CHORD, V_CHORD)),
        ChordProgression(listOf(vi_CHORD, IV_CHORD, I_CHORD, V_CHORD)),
        ChordProgression(listOf(I_CHORD, vi_CHORD, IV_CHORD, V_CHORD)),   // 50s
        ChordProgression(listOf(I_CHORD, IV_CHORD, V_CHORD, vi_CHORD)),
    )
    val majorProgressions = listOf(
        // I IV V core
        ChordProgression(listOf(I_CHORD, IV_CHORD, V_CHORD)),                        // I IV V
        ChordProgression(listOf(I_CHORD, V_CHORD, IV_CHORD)),                        // I V IV
        ChordProgression(listOf(I_CHORD, IV_CHORD, I_CHORD, V_CHORD)),               // I IV I V
        ChordProgression(listOf(IV_CHORD, V_CHORD, I_CHORD)),                        // IV V I
        ChordProgression(listOf(I_CHORD, I_CHORD, IV_CHORD, V_CHORD)),               // I I IV V
        // With vi
        ChordProgression(listOf(I_CHORD, V_CHORD, vi_CHORD, IV_CHORD)),              // I V vi IV  (axis)
        ChordProgression(listOf(I_CHORD, vi_CHORD, IV_CHORD, V_CHORD)),              // I vi IV V  (50s)
        ChordProgression(listOf(I_CHORD, IV_CHORD, vi_CHORD, V_CHORD)),              // I IV vi V
        ChordProgression(listOf(I_CHORD, IV_CHORD, V_CHORD, vi_CHORD)),              // I IV V vi
        ChordProgression(listOf(vi_CHORD, IV_CHORD, I_CHORD, V_CHORD)),              // vi IV I V
        ChordProgression(listOf(I_CHORD, vi_CHORD, ii_CHORD, V_CHORD)),              // I vi ii V
        // With ii
        ChordProgression(listOf(I_CHORD, ii_CHORD, IV_CHORD, V_CHORD)),              // I ii IV V
        ChordProgression(listOf(I_CHORD, IV_CHORD, ii_CHORD, V_CHORD)),              // I IV ii V
        ChordProgression(listOf(ii_CHORD, V_CHORD, I_CHORD)),                        // ii V I
        ChordProgression(listOf(I_CHORD, ii_CHORD, V_CHORD, I_CHORD)),               // I ii V I
        // With iii
        ChordProgression(listOf(I_CHORD, iii_CHORD, IV_CHORD, V_CHORD)),             // I iii IV V
        ChordProgression(listOf(I_CHORD, iii_CHORD, vi_CHORD, IV_CHORD)),            // I iii vi IV
        ChordProgression(listOf(I_CHORD, vi_CHORD, iii_CHORD, IV_CHORD)),            // I vi iii IV
        // Longer / mixed
        ChordProgression(listOf(I_CHORD, V_CHORD, vi_CHORD, iii_CHORD, IV_CHORD)),  // I V vi iii IV
        ChordProgression(listOf(I_CHORD, ii_CHORD, iii_CHORD, IV_CHORD)),            // I ii iii IV  (ascending)
    )
    val minorProgressions = listOf(
        ChordProgression(listOf(i_MIN, iv_MIN, V_CHORD)),                    // i iv V  (harmonic minor)
        ChordProgression(listOf(i_MIN, VII_MAJ, VI_MAJ, V_CHORD)),           // i VII VI V  (Andalusian cadence)
        ChordProgression(listOf(i_MIN, iv_MIN, i_MIN, V_CHORD)),             // i iv i V
        ChordProgression(listOf(i_MIN, VII_MAJ, VI_MAJ, VII_MAJ)),           // i VII VI VII
        ChordProgression(listOf(i_MIN, VI_MAJ, VII_MAJ, i_MIN)),             // i VI VII i
        ChordProgression(listOf(i_MIN, iv_MIN, VII_MAJ, VI_MAJ)),            // i iv VII VI
        ChordProgression(listOf(i_MIN, v_MIN, iv_MIN, i_MIN)),               // i v iv i  (natural minor)
        ChordProgression(listOf(i_MIN, VII_MAJ, VI_MAJ, iv_MIN)),            // i VII VI iv
        ChordProgression(listOf(i_MIN, iv_MIN, VI_MAJ, VII_MAJ)),            // i iv VI VII
        ChordProgression(listOf(i_MIN, v_MIN, VI_MAJ, VII_MAJ)),             // i v VI VII
        ChordProgression(listOf(i_MIN, iv_MIN, v_MIN, i_MIN)),               // i iv v i  (pure natural minor)
        ChordProgression(listOf(i_MIN, VII_MAJ, iv_MIN, V_CHORD)),           // i VII iv V
    )
    val progressions = when (progressionSet) {
        ChordProgressionSetOption.IIVVOnly -> iivvProgressions
        ChordProgressionSetOption.IIVVvi -> iivvviProgressions
        ChordProgressionSetOption.MajorFull -> majorProgressions
        ChordProgressionSetOption.Minor -> minorProgressions
    }

    fun checkAndSleep(ms: Long) {
        var waited = 0L; val step = 50L
        while (waited < ms && keepGoing.get()) {
            Thread.sleep(step); waited += step
        }
        if (!keepGoing.get()) throw InterruptedException()
    }

    fun playChord(keyRoot: Int, chord: ChordDef) {
        chord.intervals.forEach { offset ->
            soundMap[keyRoot + chord.rootOffset + offset]?.let { id ->
                soundPool.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    fun playProgression(keyRoot: Int, progression: ChordProgression) {
        progression.chords.forEach { chord ->
            playChord(keyRoot, chord)
            checkAndSleep(1200L)
        }
    }

    fun speakProgression(progression: ChordProgression, initialQueueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        progression.chords.forEachIndexed { i, chord ->
            val mode = if (i == 0) initialQueueMode else TextToSpeech.QUEUE_ADD
            tts.speak(chord.spokenName, mode, null, null)
        }
    }

    Thread {
        try {
            var inKeyCount = 0
            var currentRoot = 60
            while (keepGoing.get()) {
                if (inKeyCount == 0) {
                    currentRoot = (48..60).random()
                    val keyName = noteNames[currentRoot % 12]
                    val introChord = if (progressionSet.isMinor) i_MIN else I_CHORD
                    repeat(3) {
                        playChord(currentRoot, introChord)
                        checkAndSleep(900L)
                    }
                    tts.speak("Key of $keyName", TextToSpeech.QUEUE_FLUSH, null, null)
                    checkAndSleep(3000L)
                }
                val progression = progressions.random()
                repeat(timesBefore) {
                    playProgression(currentRoot, progression)
                    checkAndSleep(500L)
                }
                speakProgression(progression)
                checkAndSleep((progression.chords.size * 900L).coerceAtLeast(3000L))
                repeat(timesAfter) {
                    playProgression(currentRoot, progression)
                    checkAndSleep(500L)
                }
                checkAndSleep(1000L)
                inKeyCount = (inKeyCount + 1) % timesPerKey
            }
        } catch (_: InterruptedException) {}
        onComplete()
    }.start()
}
