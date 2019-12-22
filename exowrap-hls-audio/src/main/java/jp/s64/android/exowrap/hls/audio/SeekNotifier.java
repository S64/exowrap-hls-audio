package jp.s64.android.exowrap.hls.audio;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

class SeekNotifier {

    private final long seekCheckIntervalMillis;
    private final Handler handler;

    @Nullable
    private HlsPlayer instance = null;

    SeekNotifier(@NonNull Handler handler, long seekCheckIntervalMillis) {
        this.handler = handler;
        this.seekCheckIntervalMillis = seekCheckIntervalMillis;
    }

    public void stop() {
        this.instance = null;
    }

    public void start(@NonNull HlsPlayer instance) {
        this.instance = instance;
        postDelayed(0);
    }

    private final Runnable runnable = new Runnable() {

        @Override
        public void run() {
            if (instance == null) { return; }

            if (instance.state == HlsPlayer.State.PLAYING) {
                instance.notifySeekPositionManually();
            }

            postDelayed(seekCheckIntervalMillis);
        }

    };

    private void postDelayed(long seekCheckIntervalMillis) {
        if (instance == null) {
            throw new IllegalStateException();
        }
        handler.postDelayed(runnable, seekCheckIntervalMillis);
    }

}
