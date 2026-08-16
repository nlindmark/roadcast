package se.roadcast.core.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState

class AskInterruptionTest {
    private val segment = PodcastSegment(
        id = "seg-1",
        placeId = "place",
        title = "Place",
        dialogue = listOf(
            DialogueLine("1", HostId.HOST_A, "Intro", true),
            DialogueLine("2", HostId.HOST_B, "Detail", true),
            DialogueLine("3", HostId.HOST_A, "Close", true),
        ),
        estimatedDurationSeconds = 90,
        generatedAtEpochMillis = 0L,
    )

    @Test
    fun `ask pauses and resumes from the next dialogue line`() = runBlocking {
        val player = FakePodcastPreviewPlayer()
        player.play(segment)
        player.advanceToLine(0)

        val resumeIndex = player.pauseForAsk()
        assertEquals(1, resumeIndex)
        assertTrue(player.state.value is PreviewPlaybackState.Paused)

        player.playAnswerThenResume(
            question = "When did it open?",
            answerText = "It opened in 1900.",
            speaker = HostId.HOST_B,
            resumeFromLineIndex = resumeIndex,
        )

        val playing = player.state.value as PreviewPlaybackState.Playing
        assertEquals(1, playing.lineIndex)
        assertEquals("Detail", playing.line.text)
        assertEquals("It opened in 1900.", player.lastAnswerText)
        assertEquals(1, player.lastResumeIndex)
    }

    @Test
    fun `answer after final line completes the segment`() = runBlocking {
        val player = FakePodcastPreviewPlayer()
        player.play(segment)
        player.advanceToLine(2)

        val resumeIndex = player.pauseForAsk()
        assertEquals(3, resumeIndex)

        player.playAnswerThenResume(
            question = "Anything else?",
            answerText = "That is all we can verify.",
            speaker = HostId.HOST_B,
            resumeFromLineIndex = resumeIndex,
        )

        assertEquals(PreviewPlaybackState.Completed, player.state.value)
    }
}
