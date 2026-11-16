package ai.roadie.app

import ai.roadie.app.platform.AudioFocus
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioFocusInstrumentedTest {
    @Test fun competingMediaRequestNotifiesLossAndReturn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(AudioManager::class.java)
        val roadie = AudioFocus(context)
        val lost = CountDownLatch(1)
        val returned = CountDownLatch(1)
        roadie.onLoss = { transient -> if (transient) lost.countDown() }
        roadie.onGain = { returned.countDown() }
        val competing = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            assertTrue("Roadie should acquire focus", roadie.acquire())
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(competing))
            assertTrue("Roadie should receive transient loss", lost.await(10, TimeUnit.SECONDS))
            manager.abandonAudioFocusRequest(competing)
            assertTrue("Roadie should receive focus return", returned.await(10, TimeUnit.SECONDS))
        } finally {
            manager.abandonAudioFocusRequest(competing)
            roadie.release()
        }
    }
}
