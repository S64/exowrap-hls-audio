package jp.s64.android.exowrap.hls.audio.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    private val url by lazy { findViewById<EditText>(R.id.url) }
    private val launch by lazy { findViewById<Button>(R.id.launch) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        launch.setOnClickListener {
            startActivity(
                    SessionActivity.newIntent(
                            this@MainActivity,
                            url.text.toString()
                    )
            )
        }
    }

}
