package dev.michelle.trails.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.health.connect.client.HealthConnectClient

object HealthConnectSetup {
    fun openData(context: Context) {
        try {
            val intent = HealthConnectClient.getHealthConnectManageDataIntent(context, AndroidHealthConnect.PROVIDER)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: ActivityNotFoundException) {
            open(context)
        }
    }

    fun open(context: Context) {
        val available = HealthConnectClient.getSdkStatus(context, AndroidHealthConnect.PROVIDER) == HealthConnectClient.SDK_AVAILABLE
        val intent = if (available) {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${AndroidHealthConnect.PROVIDER}"))
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: ActivityNotFoundException) {
            val fallback = if (available) Intent(Settings.ACTION_SETTINGS) else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${AndroidHealthConnect.PROVIDER}"))
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (unavailable: ActivityNotFoundException) {
                Toast.makeText(context, "Open Health Connect from your Android settings.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
