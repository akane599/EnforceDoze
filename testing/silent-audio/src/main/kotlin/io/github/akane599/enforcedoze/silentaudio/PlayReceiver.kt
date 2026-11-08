// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.silentaudio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.concurrent.thread

/**
 * Plays silence as media for `--ei seconds N` (default 20, at most 50) and then stops:
 *
 *     adb shell am broadcast -n io.github.akane599.enforcedoze.silentaudio/.PlayReceiver --ei seconds 20
 *
 * The broadcast stays open while it plays, which keeps the process alive without a service; a background
 * broadcast may stay open for up to a minute.
 */
class PlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra(SECONDS, DEFAULT_SECONDS).coerceIn(1, MAX_SECONDS)
        val pending = goAsync()
        thread(name = "silence") {
            try {
                play(seconds * MILLIS_PER_SECOND)
            } finally {
                pending.finish()
            }
        }
    }

    private fun play(millis: Long) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val size = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val silence = ShortArray(size / 2)
        try {
            track.play()
            val end = SystemClock.elapsedRealtime() + millis
            while (SystemClock.elapsedRealtime() < end) track.write(silence, 0, silence.size)
            track.stop()
        } finally {
            track.release()
        }
    }

    private companion object {
        const val SECONDS = "seconds"
        const val DEFAULT_SECONDS = 20
        const val MAX_SECONDS = 50
        const val MILLIS_PER_SECOND = 1_000L
        const val SAMPLE_RATE = 48_000
    }
}
