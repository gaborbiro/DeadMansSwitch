package app.gaborbiro.deadmansswitch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.common.api.GoogleApiClient
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mediaRouter: MediaRouter
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var castApi: Cast.CastApi
    private lateinit var notificationManager: NotificationManager
    private var originalMute: Boolean? = null
    private var originalInterruptionFilter: Int? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaRouter = MediaRouter.getInstance(this)
        mediaRouter.addCallback(
            MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(getString(R.string.cast_app_id)))
                .build(),
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )

        notificationManager = getSystemService(NotificationManager::class.java)
        button_dead_mans_switch.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    originalMute?.let {
                        castApi.setMute(googleApiClient, it)
                        log("Unmuted")
                    }
                    originalInterruptionFilter?.let {
                        notificationManager.setInterruptionFilter(it)
                    }

                    if (!castApi.isMute(googleApiClient)) {
                        originalMute = castApi.isMute(googleApiClient)
                        originalInterruptionFilter = notificationManager.currentInterruptionFilter
                        log("Armed")
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (originalMute != null) {
                        castApi.setMute(googleApiClient, true)
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                        log("Muted")
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isViewContains(
                            button_dead_mans_switch,
                            event.x.toInt(),
                            event.y.toInt()
                        ) && originalMute != null
                    ) {
                        log("Canceled")
                        originalMute = null
                        originalInterruptionFilter = null
                    }
                    true
                }
            }
            false
        }
        log(Date().toLocaleString())
        log("Searching for Living Room TV...\nMake sure you are connected to the same WiFi network")
    }

    private fun isViewContains(view: View, rx: Int, ry: Int): Boolean {
        return Rect().let {
            view.getDrawingRect(it)
            it.contains(rx, ry)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if the notification policy access has been granted for the app.
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply(::startActivity)
        }
    }

    override fun onDestroy() {
        mediaRouter.removeCallback(callback)
        originalMute?.let {
            castApi.setMute(googleApiClient, it)
        }
        originalInterruptionFilter?.let {
            notificationManager.setInterruptionFilter(it)
        }
        if (::castApi.isInitialized) {
            castApi.leaveApplication(googleApiClient)
            googleApiClient.disconnect()
        }
        super.onDestroy()
    }

    private var callback: MediaRouter.Callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (route.name == "Living Room TV") {
                log("Living Room TV found")
                setupGoogleApiClient(route)
            }
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (route.name == "Living Room TV") {
                log("Living Room TV lost 1")
                googleApiClient.disconnect()
            }
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {

        }
    }

    private fun setupGoogleApiClient(route: MediaRouter.RouteInfo) {
        val device = CastDevice.getFromBundle(route.extras)

        googleApiClient = GoogleApiClient.Builder(this@MainActivity)
            .addApi(Cast.API, Cast.CastOptions.Builder(device, Cast.Listener()).build())
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {
                    println("onConnected")
                    log("Living Room TV connected")
                    button_dead_mans_switch.isEnabled = true
                    castApi = Cast.CastApi.zza()
                    castApi.joinApplication(googleApiClient)
                }

                override fun onConnectionSuspended(p0: Int) {
                    println("onConnectionSuspended")
                    log("Living Room TV lost 2")
                    button_dead_mans_switch.isEnabled = false
                }
            })
            .addOnConnectionFailedListener {
                println(it)
                log("Living Room TV lost 3")
                button_dead_mans_switch.isEnabled = false
            }
            .build()
        googleApiClient.connect()
    }

    private fun log(text: String) {
        label_cast_device.text =
            text + label_cast_device.text.let { if (it.isNotBlank()) "\n$it" else "" }
    }
}