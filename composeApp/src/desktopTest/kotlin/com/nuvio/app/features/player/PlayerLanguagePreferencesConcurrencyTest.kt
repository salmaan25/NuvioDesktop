package com.nuvio.app.features.player

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * normalizeLanguageCode() is called from the UI thread and from background coroutines at the same
 * time, and its memo cache is periodically cleared. Every result must still match the original
 * implementation under concurrent use.
 */
class PlayerLanguagePreferencesConcurrencyTest {

    @Test
    fun concurrentCallsAlwaysReturnTheOriginalResult() {
        val inputs: List<String?> = LanguageTestSamples +
            (0 until 1_500).map { i -> if (i % 3 == 0) "zz-$i" else "Subtitle $i Spanish (Latin America)" }
        val expectedNormalized = inputs.associateWith { OriginalLanguagePreferences.normalizeLanguageCode(it) }
        val expectedLabels = LanguageTestSamples.associateWith { OriginalLanguagePreferences.languageLabelResForCode(it) }

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<String>()
        repeat(threads) { worker ->
            pool.execute {
                start.await()
                repeat(20) { round ->
                    val ordered = if ((worker + round) % 2 == 0) inputs else inputs.asReversed()
                    for (input in ordered) {
                        val actual = normalizeLanguageCode(input)
                        if (actual != expectedNormalized.getValue(input)) {
                            failures += "normalize <$input>: expected=${expectedNormalized.getValue(input)} actual=$actual"
                        }
                    }
                    for (input in LanguageTestSamples) {
                        val actual = languageLabelResForCode(input)
                        if (actual != expectedLabels.getValue(input)) failures += "label <$input>"
                    }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(2, TimeUnit.MINUTES), "workers did not finish (possible deadlock)")
        assertTrue(failures.isEmpty(), "mismatches: ${failures.take(5)} (total ${failures.size})")
    }
}
