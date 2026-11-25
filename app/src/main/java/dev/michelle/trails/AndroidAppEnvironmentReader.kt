package dev.michelle.trails

import android.content.Context
import android.os.Build
import dev.michelle.trails.domain.AppEnvironment
import dev.michelle.trails.domain.AppEnvironmentReader

class AndroidAppEnvironmentReader(context: Context) : AppEnvironmentReader {
    private val context = context.applicationContext
    @Suppress("DEPRECATION")
    override fun read(): AppEnvironment {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppEnvironment(info.versionName ?: "unknown",
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), Build.VERSION.SDK_INT)
    }
}
