package jp.s64.android.exowrap.hls.audio

import android.os.Handler
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException

class HlsPlayerTest {

    private lateinit var mockExoPlayer: ExoPlayer
    private lateinit var mockHlsMediaSource: HlsMediaSource
    private lateinit var mockListener: HlsPlayer.Listener
    private lateinit var mockHandler: Handler
    private lateinit var mockDeps: HlsPlayer.IDependencies


    @Before
    fun setUp() {
        mockExoPlayer = mockk(relaxed = true)
        mockHlsMediaSource = mockk(relaxed = true)
        mockListener = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)
        mockDeps = mockk {
            // returns new instance every time
            every { createSeekNotifier() }.answers { mockk(relaxed = true) }
        }
    }

    /**
     * 正しい順序で実装されている限り、初期化と破棄が対になっていること
     */
    @Test
    fun testInitAndDestroy() {
        // region HlsPlayer#init
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        verify(exactly = 1) {
            mockHlsMediaSource.addEventListener(mockHandler, player.sourceListener)
        }

        verify(exactly = 1) {
            mockExoPlayer.addListener(player.playerListener)
        }

        verify(exactly = 0) {
            mockListener.onStateChanged(any())
            mockListener.onSeekPositionChanged(any())
        }
        //endregion

        // region HlsPlayer#destroy
        player.destroy()

        verify(exactly = 1) {
            mockHlsMediaSource.removeEventListener(player.sourceListener)
        }

        verify(exactly = 1) {
            mockExoPlayer.removeListener(player.playerListener)
        }

        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.DESTROYED)
        }
        // endregion
    }

    /**
     * HlsPlayer#initを不適切なタイミングでcallした場合、
     * 例外が発生すること
     */
    @Test(expected = IllegalStateException::class)
    fun testInit_invalid() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.PLAYING

        player.init()
    }

    /**
     * InitializedからBufferingがはじまった時、
     * BUFFERING_IN_PAUSEに変化すること
     */
    @Test
    fun testStartBuffering_initialized() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        verify(exactly = 1) { mockExoPlayer.playWhenReady = false }
        assertThat(player.state, `is`(HlsPlayer.State.INITIALIZED))

        player.playerListener
                .onPlayerStateChanged(false, Player.STATE_BUFFERING)

        verify(exactly = 2) { mockExoPlayer.playWhenReady = false }
        assertThat(player.state, `is`(HlsPlayer.State.BUFFERING_IN_PAUSE))
        assertThat(player.seekNotifier, `is`(nullValue()))

        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.BUFFERING_IN_PAUSE)
        }
    }

    /**
     * Initializedから直接READYになった時、Pausedに変化すること
     */
    @Test
    fun testDirectReady() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        verify(exactly = 1) { mockExoPlayer.playWhenReady = false }
        assertThat(player.state, `is`(HlsPlayer.State.INITIALIZED))

        player.playerListener
                .onPlayerStateChanged(false, Player.STATE_READY)

        verify(exactly = 2) { mockExoPlayer.playWhenReady = false }

        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.PAUSED)
        }
    }

    /**
     * BUFFERING_IN_PAUSEからREADYになった時、Pausedに変化すること
     */
    @Test
    fun testPausedBufferingToReady() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.BUFFERING_IN_PAUSE

        player.playerListener
                .onPlayerStateChanged(false, Player.STATE_READY)

        verify(exactly = 1) { mockExoPlayer.playWhenReady = false }
        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.PAUSED)
        }
    }

    /**
     * BUFFERING_IN_PLAYからREADYになった時、PLAYINGに変化すること
     */
    @Test
    fun testPlayedBufferingToReady() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.BUFFERING_IN_PLAY

        player.playerListener
                .onPlayerStateChanged(true, Player.STATE_READY)

        verify(exactly = 1) { mockExoPlayer.playWhenReady = true }
        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.PLAYING)
        }

        assertThat(player.seekNotifier, `is`(not(nullValue())))

        verify(exactly = 1) {
            player.seekNotifier!!.start(player)
        }
    }

    /**
     * PLAYINGからBufferingになった時、BUFFERING_IN_PLAYに変化すること
     */
    @Test
    fun testPlayingToBuffering() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.PLAYING
        val dummyNotifier = mockk<SeekNotifier>().apply {
            player.seekNotifier = this
        }

        player.playerListener
                .onPlayerStateChanged(true, Player.STATE_BUFFERING)

        assertThat(player.seekNotifier, `is`(dummyNotifier))

        verify(exactly = 0) {
            player.seekNotifier!!.start(any())
            player.seekNotifier!!.stop()
        }

        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.BUFFERING_IN_PLAY)
        }
    }

    /**
     * PAUSEDからBufferingになった時、BUFFERING_IN_PAUSEに変化すること
     */
    @Test
    fun testPausedToBuffering() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.PAUSED

        player.playerListener
                .onPlayerStateChanged(false, Player.STATE_BUFFERING)

        verify(exactly = 1) {
            mockListener.onStateChanged(HlsPlayer.State.BUFFERING_IN_PAUSE)
        }
    }

    /**
     * PLAYINGからpause要求があった場合、期待通り変化すること
     */
    @Test
    fun testPlayingToPause() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.PLAYING
        val dummyNotifier = mockk<SeekNotifier>(relaxed = true).apply {
            player.seekNotifier = this
        }

        player.pause()

        verify(exactly = 1) {
            mockExoPlayer.playWhenReady = false
            dummyNotifier.stop()
            mockListener.onStateChanged(HlsPlayer.State.PAUSED)
        }

        assertThat(player.seekNotifier, `is`(nullValue()))
    }

    /**
     * Pausedからplay要求があった場合、期待通り変化すること
     */
    @Test
    fun testPausedToPlay() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.PAUSED
        assertThat(player.seekNotifier, `is`(nullValue()))

        player.play()

        assertThat(player.seekNotifier, `is`(not(nullValue())))

        verify(exactly = 1) {
            mockExoPlayer.playWhenReady = true
            player.seekNotifier!!.start(player)
            mockListener.onStateChanged(HlsPlayer.State.PLAYING)
        }
    }

    /**
     * BUFFERING_IN_PAUSEの時にpauseしようとすると、例外が発生すること
     */
    @Test(expected = IllegalStateException::class)
    fun testPauseWhenBufferingPaused() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.BUFFERING_IN_PAUSE

        player.pause()

        fail()
    }

    /**
     * BUFFERING_IN_PAUSEの時にplay要求をすると、一旦はPLAYINGに変化すること
     */
    @Test
    fun testPlayWhenBufferingPaused() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.BUFFERING_IN_PAUSE

        player.play()

        assertThat(player.state, `is`(HlsPlayer.State.PLAYING))
        assertThat(player.seekNotifier, `is`(not(nullValue())))

        verify(exactly = 1) {
            player.seekNotifier!!.start(player)
            mockListener.onStateChanged(HlsPlayer.State.PLAYING)
        }
    }

    /**
     * BUFFERING_IN_PLAYの時にpause要求をすると、BUFFERING_IN_PAUSEに変化すること
     */
    @Test
    fun testPauseWhenBufferingPlay() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.state = HlsPlayer.State.BUFFERING_IN_PLAY
        val dummyNotifier = mockk<SeekNotifier>(relaxed = true).apply {
            player.seekNotifier = this
        }

        player.pause()

        assertThat(player.state, `is`(HlsPlayer.State.BUFFERING_IN_PAUSE))
        assertThat(player.seekNotifier, `is`(nullValue()))

        verify(exactly = 1) {
            dummyNotifier.stop()
            mockListener.onStateChanged(HlsPlayer.State.BUFFERING_IN_PAUSE)
            mockExoPlayer.playWhenReady = false
        }
    }

    /**
     * BUFFERING_IN_PLAYの時にplay要求をすると、例外が発生すること
     */
    @Test(expected = IllegalStateException::class)
    fun testPlayWhenBufferingPlay() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.BUFFERING_IN_PLAY

        player.play()

        fail()
    }

    /**
     * PLAYING中にplay要求をすると、例外が発生すること
     */
    @Test(expected = IllegalStateException::class)
    fun testPlayWhenPlaying() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.PLAYING

        player.play()

        fail()
    }

    /**
     * PAUSED中にpause要求をすると、例外が発生すること
     */
    @Test(expected = IllegalStateException::class)
    fun testPauseWhenPaused() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.PAUSED

        player.pause()

        fail()
    }

    /**
     * Destroyされていなければ、期待通りsetSeekPositionが利用できること
     */
    @Test
    fun testSetSeekPosition_notDestroyed() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        assertThat(player.state, `is`(not(HlsPlayer.State.DESTROYED)))

        val seekPosition = 1000L;

        player.setSeekPosition(seekPosition)

        verify(exactly = 1) {
            mockExoPlayer.seekTo(0, seekPosition)
        }
    }

    /**
     * 解放済みインスタンスでは、setSeekPositionが利用できないこと
     */
    @Test(expected = IllegalStateException::class)
    fun testSetSeekPosition_destroyed() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.DESTROYED

        player.setSeekPosition(999L)
    }

    /**
     * Destroyされていなければ、期待通りsetSpeedが利用できること
     */
    @Test
    fun testSetSpeed_notDestroyed() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        every { mockDeps.createPlaybackParameters(any(), any(), any()) }
                .returns(mockk(relaxed = true))

        clearMocks(mockExoPlayer)

        assertThat(player.state, `is`(not(HlsPlayer.State.DESTROYED)))

        val speed = 2.0F;

        player.speed = speed

        verify(exactly = 1) {
            mockDeps.createPlaybackParameters(eq(speed), any(), any())
            mockExoPlayer.setPlaybackParameters(any())
        }
    }

    /**
     * 解放済みインスタンスでは、setSpeedが利用できないこと
     */
    @Test(expected = IllegalStateException::class)
    fun testSetSpeed_destroyed() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )

        player.state = HlsPlayer.State.DESTROYED

        player.speed = 2.0F
    }

    /**
     * ストリームが正常に終端まで達した時、1回のみイベントが発火されること
     * また、再生位置が先頭に戻ること
     */
    @Test
    fun testOnEnded() {
        val player = HlsPlayer(
                mockExoPlayer,
                mockHlsMediaSource,
                mockListener,
                mockHandler,
                mockDeps
        )
        clearMocks(mockExoPlayer)

        player.playerListener.onPlayerStateChanged(false, Player.STATE_ENDED)

        verifySequence {
            mockListener.onEnded()
            mockListener.onStateChanged(HlsPlayer.State.PAUSED)
            mockListener.onSeekPositionChanged(0L)
        }

        verify(exactly = 0) {
            mockListener.onStateChanged(HlsPlayer.State.DESTROYED)
        }

        // region 2回以上callされた場合
        clearMocks(mockListener)

        assertThat(player.state, `is`(HlsPlayer.State.PAUSED))

        player.playerListener.onPlayerStateChanged(false, Player.STATE_ENDED)

        verify(exactly = 0) {
            mockListener.onEnded()
            mockListener.onStateChanged(any())
            mockListener.onSeekPositionChanged(any())
        }
        // endregion
    }
}
