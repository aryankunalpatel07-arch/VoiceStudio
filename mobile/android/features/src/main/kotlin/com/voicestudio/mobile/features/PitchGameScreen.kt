package com.voicestudio.mobile.features

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.voicestudio.mobile.designsystem.*
import com.voicestudio.mobile.local.PitchGame
import com.voicestudio.mobile.network.ClientFailure
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Ear-training game: works with no models, host or network (SPEC → Pitch game). */
@Composable fun PitchGameScreen(vm: StudioViewModel) {
    val context=LocalContext.current
    val store=remember {context.getSharedPreferences("pitch-game",Context.MODE_PRIVATE)}
    var best by remember {mutableIntStateOf(store.getInt("best",0))}
    var game by remember {mutableStateOf<PitchGame?>(null)}
    // PitchGame mutates in place; `turn` tells Compose an answer changed it.
    var turn by remember {mutableIntStateOf(0)}
    var replay by remember {mutableIntStateOf(0)}
    var feedback by remember {mutableStateOf<String?>(null)}
    var newBest by remember {mutableStateOf(false)}
    fun start() {game=PitchGame();turn=0;feedback=null;newBest=false}
    fun answer(higher:Boolean) {
        val current=game ?: return
        feedback=if(current.answer(higher)) "mobile.game.correct" else "mobile.game.wrong"
        if(current.score>best) {best=current.score;newBest=true;store.edit().putInt("best",best).apply()}
        turn++
    }
    LaunchedEffect(game,turn,replay) {
        val current=game?.takeIf {!it.over} ?: return@LaunchedEffect
        try {
            val samples=PitchGame.samples(current.round)
            val player=withContext(Dispatchers.Default) {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(PitchGame.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size*2).build()
            }
            // Leaving the screen or answering cancels this effect, which silences the tones.
            try {player.write(samples,0,samples.size);player.play();delay(samples.size*1000L/PitchGame.SAMPLE_RATE+100)} finally {player.release()}
        } catch(e:CancellationException) {throw e} catch(e:Exception) {vm.fail(ClientFailure("mobile.errors.audio"))}
    }
    Screen {
        ScreenTitle("mobile.game.title")
        val current=game
        if(current==null) {
            Text(tr("mobile.game.intro"))
            if(best>0) Text(tr("mobile.game.best",mapOf("score" to best)),color=role("textMuted"))
            Action("mobile.game.start") {start()}
        } else {
            val score=remember(current,turn) {current.score}
            val lives=remember(current,turn) {current.lives}
            Group {Column(Modifier.padding(VSTheme.inset),verticalArrangement=Arrangement.spacedBy(VSTheme.gap)) {
                Text(tr("mobile.game.score",mapOf("score" to score)),Modifier.testTag("game.score"),style=MaterialTheme.typography.titleMedium)
                Text(tr("mobile.game.best",mapOf("score" to best)),color=role("textMuted"))
                Text(tr("mobile.game.lives",mapOf("count" to lives)),Modifier.testTag("game.lives"))
                Text(tr("mobile.game.gap",mapOf("cents" to PitchGame.cents(score).roundToInt())),color=role("textMuted"))
            }}
            feedback?.let {Text(tr(it),Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
            if(lives==0) {
                Heading("mobile.game.over")
                if(newBest) Text(tr("mobile.game.new_best"))
                Action("mobile.game.play_again") {start()}
            } else {
                Action("mobile.game.higher",icon=Icons.Default.KeyboardArrowUp) {answer(true)}
                Action("mobile.game.lower",icon=Icons.Default.KeyboardArrowDown) {answer(false)}
                Secondary("mobile.game.replay") {replay++}
            }
        }
    }
}
