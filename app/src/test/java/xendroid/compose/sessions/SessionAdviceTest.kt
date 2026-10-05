package xendroid.compose.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.sessions.SessionAdvice.Rule

/** Round 2: the end-of-session suggestions, rule by rule, and what keeps them quiet. */
class SessionAdviceTest {
    private fun run(state: RunState = RunState.ENDED, minutes: Long = 30, reason: String? = null, perf: RunPerformance? = null) =
        SessionRun(runId = "r1", state = state, launchSource = "library", gamePath = "/games/Halo 3.iso", buildVersion = "v1", pid = 1,
            startedAt = 0, titleId = "4D5307E6", runningAt = if (minutes > 0) 1_000 else null, lastSeenAt = 1_000 + minutes * 60_000,
            endedAt = 1_000 + minutes * 60_000, endReason = reason, performance = perf)

    /** [fps]: seconds at each FPS; [frameMs]: frames at each ms. */
    private fun perf(fps: Map<Int, Int>, frameMs: Map<Int, Long> = mapOf(16 to 1000L), pipelines: Long? = null,
                     audio: Pair<Long, Long>? = null, hottest: Float? = null, limit: Int = 60) = RunPerformance(
        fpsHistogram = List(fps.keys.max() + 1) { fps[it] ?: 0 },
        frameTimeHistogramMs = List(frameMs.keys.max() + 1) { frameMs[it] ?: 0L },
        pipelineCreations = pipelines, audioBlocks = audio?.first, audioConcealedBlocks = audio?.second,
        batteryMaxC = hottest, fpsLimits = listOf(limit), firstFrameSeconds = 7)

    private fun settings(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get
    private val smooth = perf(mapOf(60 to 1700, 58 to 100))

    @Test fun aNativeCrashOnACustomDriverSuggestsThePhonesOwn() {
        val crash = run(RunState.FAILED, reason = "native crash: SIGSEGV in libvulkan_freedreno.so", perf = smooth)
        val advice = SessionAdvice.of(crash, settings(SessionAdvice.DRIVER to "/data/drivers/turnip/libvulkan_freedreno.so"), emptySet())
        assertEquals(Rule.SYSTEM_DRIVER, advice.first().rule)
        assertEquals(mapOf(SessionAdvice.DRIVER to ""), advice.first().values)
        // On the phone's own driver there is nothing to switch to.
        assertTrue(SessionAdvice.of(crash, settings(), emptySet()).none { it.rule == Rule.SYSTEM_DRIVER })
    }

    @Test fun failingToOpenWithValuesOfItsOwnSuggestsTheGlobalOnes() {
        val failed = run(RunState.FAILED, minutes = 0, reason = "fatal error: GPU device lost")
        val own = setOf("GPU|framerate_limit", "GPU|draw_resolution_scale_x")
        val advice = SessionAdvice.of(failed, settings(), own)
        assertEquals(listOf(Rule.RESET_GAME), advice.map { it.rule })
        assertEquals(own.associateWith { null }, advice.single().values)
        assertEquals(listOf("2"), advice.single().numbers)
        assertTrue(SessionAdvice.of(failed, settings(), emptySet()).isEmpty())
        assertTrue("a failed session is always worth a look", SessionAdvice.worthALook(failed))
        assertTrue("a short closed one is not", !SessionAdvice.worthALook(run(minutes = 0)))
    }

    @Test fun slowAtAHighScaleSuggests1xRatherThanALowerLimit() {
        val slow = run(perf = perf(mapOf(38 to 900, 42 to 600, 30 to 300)))
        val advice = SessionAdvice.of(slow, settings(SessionAdvice.SCALE_X to "2", SessionAdvice.SCALE_Y to "2", SessionAdvice.FPS_LIMIT to "60"), emptySet())
        assertEquals(listOf(Rule.SCALE_DOWN), advice.map { it.rule })
        assertEquals(mapOf(SessionAdvice.SCALE_X to "1", SessionAdvice.SCALE_Y to "1"), advice.single().values)
    }

    @Test fun swingingUnderA60LimitSuggestsASteady30() {
        val swinging = run(perf = perf(mapOf(60 to 500, 45 to 600, 33 to 500, 30 to 200)))
        val advice = SessionAdvice.of(swinging, settings(SessionAdvice.FPS_LIMIT to "60"), emptySet())
        assertEquals(listOf(Rule.STEADY_30), advice.map { it.rule })
        assertEquals(mapOf(SessionAdvice.FPS_LIMIT to "30"), advice.single().values)
        assertTrue(SessionAdvice.of(run(perf = smooth), settings(SessionAdvice.FPS_LIMIT to "60"), emptySet()).isEmpty())
    }

    @Test fun stuttersWithManyPipelinesSuggestMoreThreads() {
        val stutter = run(perf = perf(mapOf(30 to 1800), frameMs = mapOf(33 to 9000L, 120 to 200L), pipelines = 1830, limit = 30))
        val advice = SessionAdvice.of(stutter, settings(SessionAdvice.FPS_LIMIT to "30", SessionAdvice.PIPELINE_THREADS to "4"), emptySet())
        assertEquals(Rule.PIPELINES, advice.single().rule)
        assertEquals(mapOf(SessionAdvice.PIPELINE_THREADS to "5"), advice.single().values)
        assertTrue(SessionAdvice.of(stutter, settings(SessionAdvice.PIPELINE_THREADS to "5"), emptySet()).none { it.rule == Rule.PIPELINES })
    }

    @Test fun lateAudioSuggestsADeeperBuffer() {
        val crackle = run(perf = perf(mapOf(60 to 1800), audio = 10_000L to 400L))
        val advice = SessionAdvice.of(crackle, settings(SessionAdvice.AUDIO_BURSTS to "4"), emptySet())
        assertEquals(mapOf(SessionAdvice.AUDIO_BURSTS to "6"), advice.single().values)
        assertEquals(listOf("4.0"), advice.single().numbers)
    }

    @Test fun heatSuggestsALowerLimitOnce() {
        val hot = run(perf = perf(mapOf(60 to 1800), hottest = 47.5f))
        assertEquals(listOf(Rule.HEAT), SessionAdvice.of(hot, settings(SessionAdvice.FPS_LIMIT to "60"), emptySet()).map { it.rule })
        assertTrue(SessionAdvice.of(hot, settings(SessionAdvice.FPS_LIMIT to "30"), emptySet()).isEmpty())
        // Already lowered by the swing rule: one suggestion writes the limit, not two.
        val hotSwinging = run(perf = perf(mapOf(60 to 500, 45 to 600, 33 to 500, 30 to 200), hottest = 47f))
        assertEquals(listOf(Rule.STEADY_30), SessionAdvice.of(hotSwinging, settings(SessionAdvice.FPS_LIMIT to "60"), emptySet()).map { it.rule })
    }

    @Test fun errorsComeFirstAndAtMostThree() {
        val bad = run(RunState.FAILED, reason = "native crash: SIGSEGV", perf = perf(mapOf(38 to 900, 42 to 600, 30 to 300),
            frameMs = mapOf(25 to 9000L, 120 to 200L), pipelines = 900, audio = 10_000L to 400L, hottest = 48f))
        val advice = SessionAdvice.of(bad, settings(SessionAdvice.DRIVER to "/d/libvulkan.so", SessionAdvice.SCALE_X to "2"), emptySet())
        assertEquals(3, advice.size)
        assertEquals(Rule.SYSTEM_DRIVER, advice.first().rule)
    }

    @Test fun modesMutesAndTheDailyLimit() {
        val error = SessionAdvice.Advice(Rule.SYSTEM_DRIVER, mapOf(SessionAdvice.DRIVER to ""))
        val perf = SessionAdvice.Advice(Rule.STEADY_30, mapOf(SessionAdvice.FPS_LIMIT to "30"))
        val both = listOf(error, perf)
        val now = 10 * SessionAdvice.DAY_MS
        fun shown(mode: SessionAdvice.Mode = SessionAdvice.Mode.ALWAYS, muted: Set<String> = emptySet(), last: Long = 0) =
            SessionAdvice.shown(both, "4d5307e6", mode, muted, last, now).map { it.rule }
        assertEquals(listOf(Rule.SYSTEM_DRIVER, Rule.STEADY_30), shown())
        assertEquals(listOf(Rule.SYSTEM_DRIVER), shown(SessionAdvice.Mode.ERRORS))
        assertEquals(emptyList<Rule>(), shown(SessionAdvice.Mode.NEVER))
        assertEquals(emptyList<Rule>(), shown(muted = setOf(SessionAdvice.gameKey("4D5307E6"))))
        assertEquals(listOf(Rule.STEADY_30), shown(muted = setOf("system-driver")))
        assertEquals(listOf(Rule.SYSTEM_DRIVER), shown(muted = setOf(SessionAdvice.ruleKey(Rule.STEADY_30, "4D5307E6"))))
        assertEquals("another game's mute does not count", 2, shown(muted = setOf(SessionAdvice.ruleKey(Rule.STEADY_30, "4D5309C9"))).size)
        // Performance at most once a day per game; errors always.
        assertEquals(listOf(Rule.SYSTEM_DRIVER), shown(last = now - 3_600_000))
        assertEquals(2, shown(last = now - 2 * SessionAdvice.DAY_MS).size)
    }
}
