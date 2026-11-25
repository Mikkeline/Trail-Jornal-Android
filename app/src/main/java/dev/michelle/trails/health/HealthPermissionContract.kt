package dev.michelle.trails.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import dev.michelle.trails.domain.HealthPermissionMode

/** The app requests read or share access explicitly, without depending on SDK record classes. */
class HealthPermissionContract : ActivityResultContract<HealthPermissionMode, Set<String>>() {
    private val delegate = PermissionController.createRequestPermissionResultContract(AndroidHealthConnect.PROVIDER)
    override fun createIntent(context: Context, input: HealthPermissionMode): Intent {
        val permissions = if (input == HealthPermissionMode.READ_WORKOUTS) AndroidHealthConnect.readPermissions else AndroidHealthConnect.writePermissions
        return delegate.createIntent(context, permissions)
    }
    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = delegate.parseResult(resultCode, intent)
}
