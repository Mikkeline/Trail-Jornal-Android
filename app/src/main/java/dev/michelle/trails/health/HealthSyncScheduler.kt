package dev.michelle.trails.health

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.michelle.trails.TrailApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Android's persistent scheduler is an adapter; Room remains the source of pending work. */
class HealthSyncScheduler(context: Context) {
    private val context = context.applicationContext
    fun schedule() {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, HealthSyncJobService::class.java))
            .setMinimumLatency(2_000)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true)
            .build()
        check(context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS) {
            "Android could not schedule health updates. They will retry when Trail Journal is opened."
        }
    }
    companion object { const val JOB_ID = 7401 }
}

class HealthSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                val report = (application as TrailApplication).container.healthAutoSync.synchronize()
                jobFinished(params, report.retryNeeded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
