package uy.autovideo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import uy.autovideo.shared.CarBridge
import uy.autovideo.shared.MotionSettings
import uy.autovideo.shared.YouTubeLinks

/** App del teléfono: enviar videos al auto, permisos e instrucciones. */
class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var sendStatus: TextView
    private lateinit var permStatus: TextView
    private lateinit var permButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        sendStatus = findViewById(R.id.send_status)
        permStatus = findViewById(R.id.perm_status)
        permButton = findViewById(R.id.perm_button)

        findViewById<Button>(R.id.send_button).setOnClickListener { sendInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendInput()
                true
            } else {
                false
            }
        }
        permButton.setOnClickListener { requestPermissionsNow() }
        setUpMotionSettings()

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        input.setText(text)
        sendInput()
    }

    private fun sendInput() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) {
            sendStatus.text = getString(R.string.status_empty)
            return
        }
        val url = YouTubeLinks.resolve(text)
        if (url == null) {
            sendStatus.text = getString(R.string.status_not_youtube)
            return
        }
        val delivered = CarBridge.send(url)
        sendStatus.text = getString(if (delivered) R.string.status_sent else R.string.status_pending)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsNow() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQUEST_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionUi()
    }

    private fun updatePermissionUi() {
        if (hasLocationPermission()) {
            permStatus.text = getString(R.string.perm_ok)
            permButton.visibility = View.GONE
        } else {
            permStatus.text = getString(R.string.perm_missing)
            permButton.visibility = View.VISIBLE
        }
    }

    /** Velocidad mínima y segundos de movimiento sostenido antes de tapar la imagen. */
    private fun setUpMotionSettings() {
        val speedBar = findViewById<SeekBar>(R.id.motion_speed)
        val speedLabel = findViewById<TextView>(R.id.motion_speed_label)
        val secondsBar = findViewById<SeekBar>(R.id.motion_seconds)
        val secondsLabel = findViewById<TextView>(R.id.motion_seconds_label)

        // Las barras empiezan en 0: se desplazan por el mínimo de cada ajuste.
        speedBar.max = MotionSettings.MAX_SPEED_KMH - MotionSettings.MIN_SPEED_KMH
        speedBar.progress = MotionSettings.speedKmh(this) - MotionSettings.MIN_SPEED_KMH
        secondsBar.max = MotionSettings.MAX_SECONDS - MotionSettings.MIN_SECONDS
        secondsBar.progress = MotionSettings.sustainedSeconds(this) - MotionSettings.MIN_SECONDS

        fun refreshLabels() {
            speedLabel.text = getString(R.string.motion_speed_label, MotionSettings.speedKmh(this))
            secondsLabel.text = getString(R.string.motion_seconds_label, MotionSettings.sustainedSeconds(this))
        }
        refreshLabels()

        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                MotionSettings.setSpeedKmh(this@MainActivity, progress + MotionSettings.MIN_SPEED_KMH)
                refreshLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        secondsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                MotionSettings.setSustainedSeconds(this@MainActivity, progress + MotionSettings.MIN_SECONDS)
                refreshLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
    }
}
