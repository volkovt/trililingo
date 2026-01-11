package com.trililingo.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.trililingo.R

/**
 * Sound FX ultra rápido (baixa latência) para app gameficado.
 * - SoundPool é melhor que MediaPlayer para sfx curtinhos (tap/correct/wrong).
 */
class SoundFx(context: Context) {

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(attrs)
        .build()

    private val correctId = pool.load(context, R.raw.sfx_correct, 1)
    private val wrongId = pool.load(context, R.raw.sfx_wrong, 1)
    private val completeId = pool.load(context, R.raw.sfx_complete, 1)

    private fun play(soundId: Int, volume: Float = 1f, rate: Float = 1f) {
        if (soundId == 0) return
        pool.play(soundId, volume, volume, 1, 0, rate)
    }

    fun correct() = play(correctId, volume = 0.95f, rate = 1.02f)
    fun wrong() = play(wrongId, volume = 0.90f, rate = 0.98f)
    fun complete() = play(completeId, volume = 1.0f, rate = 1.0f)

    fun release() {
        pool.release()
    }
}
