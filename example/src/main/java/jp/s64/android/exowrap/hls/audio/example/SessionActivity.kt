package jp.s64.android.exowrap.hls.audio.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Toast
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import jp.s64.android.exowrap.hls.audio.HlsPlayer
import kotlin.math.min

class SessionActivity : AppCompatActivity() {

    companion object {

        private const val TAG = "SessionActivity"

        private const val EXTRA_URL
                = "jp.s64.android.exowrap.hls.audio.example.SessionActivity.EXTRA_URL"

        fun newIntent(context: Context, url: String): Intent {
            return Intent(
                    context,
                    SessionActivity::class.java
            ).apply {
                putExtra(EXTRA_URL, url)
            }
        }

    }

    private lateinit var url: String

    private lateinit var player: HlsPlayer

    private val play by lazy { findViewById<Button>(R.id.play) }
    private val pause by lazy { findViewById<Button>(R.id.pause) }
    private val seek by lazy { findViewById<SeekBar>(R.id.seek) }
    private val speed by lazy { findViewById<SeekBar>(R.id.speed) }
    private val progress by lazy { findViewById<ProgressBar>(R.id.progress) }
    private val ended by lazy { findViewById<ImageView>(R.id.ended) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.session_activity)

        url = intent.getStringExtra(EXTRA_URL)

        player = HlsPlayer(
                this,
                Uri.parse(url),
                DefaultTrackSelector(this),
                DefaultLoadControl(),
                HlsMediaSource.Factory(DefaultDataSourceFactory(this, "MyUserAgent"))
                        .setLoadErrorHandlingPolicy(
                                object : DefaultLoadErrorHandlingPolicy() {

                                    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                                        return Int.MAX_VALUE
                                    }

                                }
                        ),
                listener
        )

        play.setOnClickListener {
            player.play()
        }

        pause.setOnClickListener {
            player.pause()
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // no-op
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seek.progress
                val newPosition = if (progress > 0) {
                    (progress.toFloat() / seek.max.toFloat()) * player.totalDuration.toFloat()
                } else {
                    0F
                }.toLong()
                player.setSeekPosition(newPosition)
            }

        })

        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                player.speed = if (progress == 0) {
                    -0.01F
                } else {
                    (progress * 2) / 100F
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

        })
    }

    private val listener = object : HlsPlayer.Listener {

        override fun onEnded() {
            ended.visibility = View.VISIBLE
            toast("onEnded")
        }

        override fun onStateChanged(state: HlsPlayer.State) {
            log("onStateChanged: ${state}")

            if (state == HlsPlayer.State.BUFFERING_IN_PAUSE) {
                play.isEnabled = false
                pause.isEnabled = false
                progress.visibility = View.VISIBLE
            } else if (state == HlsPlayer.State.BUFFERING_IN_PLAY) {
                progress.visibility = View.VISIBLE
            } else {
                play.isEnabled = true
                pause.isEnabled = true
                progress.visibility = View.GONE

                when (state) {
                    HlsPlayer.State.PLAYING -> true
                    HlsPlayer.State.PAUSED -> false
                    else -> null
                }.let {
                    if (it != null) {
                        play.visibility = if (it) View.GONE else View.VISIBLE
                        pause.visibility = if (it) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        override fun onSeekPositionChanged(seekPosition: Long) {
            log("pos: ${seekPosition}")
            val newProgress: Float = if (seekPosition > 0) {
                seekPosition.toFloat() / player.totalDuration.toFloat()
            } else {
                0F
            }
            val inInt = min((newProgress * seek.max).toInt(), seek.max)
            seek.progress = inInt
        }

        override fun onError(throwable: Throwable) {
            toast("onError: ${throwable}")
            finish()
        }

        override fun onWarn(throwable: Throwable) {
            toast("onWarn: ${throwable}")
        }

    }

    override fun onStop() {
        super.onStop()
        player.destroy();
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT)
                .show()
        log(msg)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }

}
