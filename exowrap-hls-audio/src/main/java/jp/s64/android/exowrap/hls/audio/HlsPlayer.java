package jp.s64.android.exowrap.hls.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelector;

import java.io.IOException;

public class HlsPlayer {

    @NonNull
    State state = State.INITIALIZED;

    @NonNull
    private final ExoPlayer player;

    @NonNull
    private final HlsMediaSource source;

    @NonNull
    private final Handler handler;

    @NonNull
    private final Listener listener;

    @VisibleForTesting
    @Nullable
    SeekNotifier seekNotifier = null;

    @Nullable
    private Long totalDuration = null;

    @NonNull
    private final IDependencies deps;

    public HlsPlayer(
            @NonNull Context context,
            @NonNull Uri uri,
            @NonNull TrackSelector trackSelector,
            @NonNull LoadControl loadControl,
            @NonNull HlsMediaSource.Factory mediaSourceFactory,
            @NonNull Listener listener,
            long seekCheckIntervalMillis,
            @NonNull Handler handler
    ) {
        this(
                new SimpleExoPlayer.Builder(context)
                        .setTrackSelector(trackSelector)
                        .setLoadControl(loadControl)
                        .build(),
                mediaSourceFactory.createMediaSource(uri),
                listener,
                handler,
                new IDependencies() {

                    @NonNull
                    @Override
                    public SeekNotifier createSeekNotifier() {
                        return new SeekNotifier(handler, seekCheckIntervalMillis);
                    }

                    @NonNull
                    @Override
                    public PlaybackParameters createPlaybackParameters(
                            float speed,
                            float pitch,
                            boolean skipSilence
                    ) {
                        return new PlaybackParameters(speed, pitch, skipSilence);
                    }

                }
        );
    }

    public HlsPlayer(
            @NonNull Context context,
            @NonNull Uri uri,
            @NonNull TrackSelector trackSelector,
            @NonNull LoadControl loadControl,
            @NonNull HlsMediaSource.Factory mediaSourceFactory,
            @NonNull Listener listener
    ) {
       this(
               context,
               uri,
               trackSelector,
               loadControl,
               mediaSourceFactory,
               listener,
               100,
               new Handler()
       );
    }

    public HlsPlayer(
            @NonNull ExoPlayer player,
            @NonNull HlsMediaSource mediaSource,
            @NonNull Listener listener,
            @NonNull Handler handler,
            @NonNull IDependencies deps
    ) {
        this.player = player;
        this.source = mediaSource;
        this.handler = handler;
        this.listener = listener;
        this.deps = deps;
        init();
    }

    @VisibleForTesting
    final Player.EventListener playerListener = new Player.EventListener() {

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            destroyWithError(error);
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    assertInitialized();
                    switch (state) {
                        case INITIALIZED:
                        case PAUSED:
                            pause(true);
                            break;
                        case PLAYING:
                        case BUFFERING_IN_PLAY:
                            setState(State.BUFFERING_IN_PLAY);
                            break;
                        default:
                            throw new IllegalStateException();
                    }
                    break;
                case Player.STATE_READY:
                    assertInitialized();
                    switch (state) {
                        case INITIALIZED:
                        case BUFFERING_IN_PAUSE:
                            pause(false);
                            break;
                        case BUFFERING_IN_PLAY:
                            play(true, true);
                            break;
                        default:
                    }
                    break;
                case Player.STATE_ENDED:
                    onEnded();
                    break;
                case Player.STATE_IDLE:
                    destroy();
                    break;
                default:
                    destroy();
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public void onTimelineChanged(Timeline org, @Player.TimelineChangeReason int reason) {
            switch (reason) {
                case Player.TIMELINE_CHANGE_REASON_RESET:
                case Player.TIMELINE_CHANGE_REASON_DYNAMIC:
                    throw new IllegalStateException();
            }

            if (org.getWindowCount() != 1) {
                destroyWithError(
                        new UnsupportedOperationException(
                                "HlsPlayer is currently only supported single-window timeline."
                        )
                );
                return;
            }

            if (!(org instanceof SinglePeriodTimeline)) {
                destroyWithError(
                        new UnsupportedOperationException(
                                "HlsPlayer is currently only supported single-period timeline."
                        )
                );
                return;
            }

            SinglePeriodTimeline timeline = (SinglePeriodTimeline) org;
            Timeline.Window window = new Timeline.Window();
            {
                timeline.getWindow(0, window);
            }

            if (window.getDurationMs() == C.TIME_UNSET || window.isDynamic) {
                destroyWithError(
                        new UnsupportedOperationException(
                                "HlsPlayer has currently not supported the dynamic stream."
                        )
                );
                return;
            }

            setTotalDuration(window.getDurationMs());
        }

    };

    @VisibleForTesting
    final MediaSourceEventListener sourceListener = new MediaSourceEventListener() {

        @Override
        public void onMediaPeriodCreated(
                int windowIndex,
                MediaSource.MediaPeriodId mediaPeriodId
        ) {
            // no-op
        }

        @Override
        public void onMediaPeriodReleased(
                int windowIndex,
                MediaSource.MediaPeriodId mediaPeriodId
        ) {
            // no-op
        }

        @Override
        public void onLoadStarted(
                int windowIndex,
                @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                LoadEventInfo loadEventInfo,
                MediaLoadData mediaLoadData
        ) {
            // no-op
        }

        @Override
        public void onLoadCompleted(
                int windowIndex,
                @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                LoadEventInfo loadEventInfo,
                MediaLoadData mediaLoadData
        ) {
            // no-op
        }

        @Override
        public void onLoadCanceled(
                int windowIndex,
                @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                LoadEventInfo loadEventInfo,
                MediaLoadData mediaLoadData
        ) {
            // no-op
        }

        @Override
        public void onLoadError(
                int windowIndex,
                @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                LoadEventInfo loadEventInfo,
                MediaLoadData mediaLoadData,
                IOException error,
                boolean wasCanceled
        ) {
            if (state != State.DESTROYED && !wasCanceled && error != null) {
                listener.onWarn(error);
            }
        }

        @Override
        public void onReadingStarted(
                int windowIndex,
                MediaSource.MediaPeriodId mediaPeriodId
        ) {
            // no-op
        }

        @Override
        public void onUpstreamDiscarded(
                int windowIndex,
                MediaSource.MediaPeriodId mediaPeriodId,
                MediaLoadData mediaLoadData
        ) {
            // no-op
        }

        @Override
        public void onDownstreamFormatChanged(
                int windowIndex,
                @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                MediaLoadData mediaLoadData
        ) {
            // no-op
        }

    };

    @VisibleForTesting
    void init() {
        if (state != State.INITIALIZED) {
            destroy();
            throw new IllegalStateException();
        }

        player.setPlayWhenReady(false);
        source.addEventListener(handler, sourceListener);
        player.addListener(playerListener);
        player.prepare(source);
    }

    public void play() {
        play(
                !isBuffering(),
                false
        );
    }

    private void play(
            boolean currentlyInBuffering,
            boolean continueToReady
    ) {
        if (currentlyInBuffering && continueToReady) {
            assertBuffering();
        } else {
            assertPaused();
        }

        setState(State.PLAYING);
        player.setPlayWhenReady(true);

        if (seekNotifier == null) {
            seekNotifier = deps.createSeekNotifier();
            seekNotifier.start(this);
        }
    }

    public void pause() {
        pause(isBuffering());
    }

    private boolean isBuffering() {
        return state == State.BUFFERING_IN_PAUSE || state == State.BUFFERING_IN_PLAY;
    }

    private void pause(boolean inBuffering) {
        if (!inBuffering) {
            assertPausable();
        } else {
            assertBufferingPausable();
        }

        if (seekNotifier != null) {
            seekNotifier.stop();
            seekNotifier = null;
        }

        player.setPlayWhenReady(false);
        setState(inBuffering ? State.BUFFERING_IN_PAUSE : State.PAUSED);
    }

    private void onEnded() {
        assertInitialized();
        // ENDEDは2回以上呼ばれることがあるため
        if (state != State.PAUSED) {
            listener.onEnded();
            pause();
            setSeekPosition(0);
            if (seekNotifier == null) {
                notifySeekPositionManually();
            }
        }
    }

    public void destroy() {
        if (state == State.DESTROYED) {
            return; // no-op
        }
        source.removeEventListener(sourceListener);
        player.removeListener(playerListener);
        player.release();
        setState(State.DESTROYED);
    }

    public void setSeekPosition(long seekPosition) {
        assertInitialized();

        // いまのところWindowが変化するストリームはサポートしないため
        player.seekTo(0, seekPosition);
    }

    private void assertInitialized() {
        if (state == State.DESTROYED) {
            destroy();
            throw new IllegalStateException();
        }
    }

    private void assertPaused() {
        if (state != State.PAUSED && state != State.BUFFERING_IN_PAUSE) {
            destroy();
            throw new IllegalStateException();
        }
    }

    private void assertPausable() {
        if (state == State.DESTROYED || state == State.PAUSED) {
            destroy();
            throw new IllegalStateException();
        }
    }

    private void assertBufferingPausable() {
        if (state == State.DESTROYED || state == State.BUFFERING_IN_PAUSE) {
            destroy();
            throw new IllegalStateException();
        }
    }

    private void assertBuffering() {
        if (state != State.BUFFERING_IN_PLAY && state != State.BUFFERING_IN_PAUSE) {
            destroy();
            throw new IllegalStateException();
        }
    }

    private void destroyWithError(@NonNull Throwable throwable) {
        if(state == State.DESTROYED) {
            return;
        }

        listener.onError(throwable);

        destroy();
    }

    private void setState(@NonNull State state) {
        this.state = state;
        listener.onStateChanged(state);
    }

    private void setTotalDuration(long durationInMillis) {
        assertInitialized();
        if (this.totalDuration != null) {
            throw new IllegalStateException("Internal error. Duration is already set.");
        }
        this.totalDuration = durationInMillis;
    }

    public long getTotalDuration() {
        assertInitialized();
        if (totalDuration == null) {
            throw new IllegalStateException("Internal error. Duration is not set.");
        } else {
            return totalDuration;
        }
    }

    public void setSpeed(float speed) {
        assertInitialized();
        PlaybackParameters org = player.getPlaybackParameters();
        player.setPlaybackParameters(
                deps.createPlaybackParameters(
                        speed,
                        org.pitch,
                        org.skipSilence
                )
        );
    }

    public float getSpeed() {
        assertInitialized();
        return player.getPlaybackParameters().speed;
    }

    public enum State {
        INITIALIZED,
        BUFFERING_IN_PLAY,
        BUFFERING_IN_PAUSE,
        PLAYING,
        PAUSED,
        DESTROYED,
        //
        ;
    }

    public interface Listener {

        void onStateChanged(@NonNull State state);
        void onSeekPositionChanged(
                long seekPosition
        );
        void onError(@NonNull Throwable throwable);
        void onWarn(@NonNull Throwable throwable);
        void onEnded();

    }

    void notifySeekPositionManually() {
        listener.onSeekPositionChanged(
                player.getCurrentPosition()
        );
    }

    @VisibleForTesting
    interface IDependencies {

        @NonNull
        SeekNotifier createSeekNotifier();

        @NonNull
        PlaybackParameters createPlaybackParameters(float speed, float pitch, boolean skipSilence);

    }

}
