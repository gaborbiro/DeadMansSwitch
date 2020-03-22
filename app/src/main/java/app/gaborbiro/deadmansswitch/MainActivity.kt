package app.gaborbiro.deadmansswitch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    private var googleApiClient: GoogleApiClient? = null
    private lateinit var castApi: Cast.CastApi
    private lateinit var notificationManager: NotificationManager
    private var originalMute: Boolean? = null
    private var originalInterruptionFilter: Int? = null
    private lateinit var adapter: ArrayAdapter<String>
    private val devices: MutableMap<String, MediaRouter.RouteInfo> = mutableMapOf()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationManager = getSystemService(NotificationManager::class.java)
        mediaRouter = MediaRouter.getInstance(this)

        button_dead_mans_switch.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    originalMute?.let {
                        castApi.setMute(googleApiClient, it)
                        log("Unmuted")
                    }
                    originalInterruptionFilter?.let {
                        if (notificationManager.currentInterruptionFilter != it) {
                            notificationManager.setInterruptionFilter(it)
                        }
                    }

                    originalMute = castApi.isMute(googleApiClient)
                    originalInterruptionFilter = notificationManager.currentInterruptionFilter
                    log("Armed")
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (originalMute != null) {
                        castApi.setMute(googleApiClient, true)
                        if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        }
                        log("Muted")
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!button_dead_mans_switch.contains(
                            event.x,
                            event.y
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
        adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        spinner_device_selector.adapter = adapter
        spinner_device_selector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position > 0) {
                        val deviceName = adapter.getItem(position)!!
                        log("Connecting to $deviceName")
                        setupGoogleApiClient(devices[deviceName]!!)
                    } else {
                        if (googleApiClient?.isConnected == true || googleApiClient?.isConnecting == true) {
                            log("Disconnecting")
                            button_dead_mans_switch.isEnabled = false
                            googleApiClient?.disconnect()
                            googleApiClient = null
                        }
                    }
                }
            }
        adapter.add("Select a device")
        clearlog()
        startScanning()
        button_dead_mans_switch.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        log(Date().toLocaleString())
    }

    private fun startScanning() {
        // Check if the notification policy access has been granted for the app.
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply(::startActivity)
        } else {
            mediaRouter.addCallback(
                MediaRouteSelector.Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(getString(R.string.cast_app_id)))
                    .build(),
                callback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            log("Scanning for devices...")
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
            googleApiClient?.disconnect()
        }
        super.onDestroy()
    }

    private var callback: MediaRouter.Callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (!devices.containsKey(route.name)) {
                adapter.add(route.name)
                devices[route.name] = route
            }
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            adapter.remove(route.name)
            devices.remove(route.name)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (!devices.containsKey(route.name)) {
                adapter.add(route.name)
                devices[route.name] = route
            }
        }
    }

    private fun setupGoogleApiClient(route: MediaRouter.RouteInfo) {
        val device = CastDevice.getFromBundle(route.extras)

        googleApiClient?.disconnect()
        googleApiClient = GoogleApiClient.Builder(this@MainActivity)
            .addApi(Cast.API, Cast.CastOptions.Builder(device, Cast.Listener()).build())
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {
                    log("Connected")
                    button_dead_mans_switch.isEnabled = true
                    castApi = Cast.CastApi.zza()
                    castApi.joinApplication(googleApiClient)
                }

                override fun onConnectionSuspended(p0: Int) {
                    log("Connection suspended")
                    button_dead_mans_switch.isEnabled = false
                }
            })
            .addOnConnectionFailedListener {
                log("Connection failed")
                spinner_device_selector.setSelection(0)
            }
            .build().also { it.connect() }
    }

    private fun clearlog() {
        text_log.text = null
    }

    private fun log(text: String) {
        text_log.text =
            text + text_log.text.let { if (it.isNotBlank()) "\n$it" else "" }
    }
}