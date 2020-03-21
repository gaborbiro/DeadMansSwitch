package app.gaborbiro.deadmansswitch

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions


class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val buttonActions: MutableList<String> = ArrayList()

        buttonActions.add(MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK)
        buttonActions.add(MediaIntentReceiver.ACTION_STOP_CASTING)

        val compatButtonActionsIndicies = intArrayOf(0, 1)

        val notificationOptions = NotificationOptions.Builder()
            .setActions(buttonActions, compatButtonActionsIndicies)
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(MainActivity::class.java.name)
            .build()

        return CastOptions.Builder()
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setReceiverApplicationId(context.getString(R.string.cast_app_id))
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? {
        return null
    }
}