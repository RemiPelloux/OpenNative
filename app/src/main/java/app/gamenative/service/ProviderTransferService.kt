package app.gamenative.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.gamenative.R
import app.gamenative.provider.DebridResolverRegistry
import app.gamenative.provider.HosterAllowlist
import app.gamenative.provider.ProviderCatalogRepository
import app.gamenative.provider.ProviderCredentials
import app.gamenative.provider.ProviderInstallHandler
import app.gamenative.provider.ProviderSecretStore
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.ProviderTabPolicy
import app.gamenative.provider.ProviderTransferCoordinator
import app.gamenative.provider.WordpressMetadata
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ProviderTransferService : Service() {
    @Inject
    lateinit var catalog: ProviderCatalogRepository

    @Inject
    lateinit var transfers: ProviderTransferCoordinator

    @Inject
    lateinit var secrets: ProviderSecretStore

    @Inject
    lateinit var resolverRegistry: DebridResolverRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tabId = intent?.getStringExtra(EXTRA_TAB).orEmpty()
        val itemId = intent?.getStringExtra(EXTRA_ITEM).orEmpty()
        startForeground(NOTIFICATION_ID, notification(getString(R.string.provider_download)))
        if (tabId.isBlank() || itemId.isBlank()) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val key = "$tabId:$itemId"
        if (!running.add(key)) return START_STICKY
        scope.launch { runJob(tabId, itemId, key, startId) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runJob(tabId: String, itemId: String, key: String, startId: Int) {
        try {
            val tab = catalog.getTab(tabId)?.withGlobal() ?: return
            val item = catalog.getItem(tabId, itemId) ?: return
            notify(item.title)
            val hosts = HosterAllowlist.hostsFor(tab.feedUrl)
            val magnet = if (hosts == null) WordpressMetadata.magnetOf(item) else ""
            val links = HosterAllowlist.filter(WordpressMetadata.candidateLinks(item), tab.feedUrl)
            val downloadItem = item.copy(link = magnet.ifBlank { links.firstOrNull() ?: item.link })
            val available = File(filesDir.absolutePath).usableSpace
            var latest = transfers.enqueue(tab, downloadItem, available, includeWineHeadroom = false)
            try {
                latest = transfers.resolveAndDownload(
                    tab,
                    latest,
                    { false },
                    links,
                    item.link,
                    magnet,
                )
                if (ProviderTabPolicy.extractOnly(tab.feedUrl)) {
                    latest = ProviderInstallHandler.install(transfers, latest, item, tab)
                }
            } catch (error: Throwable) {
                val persisted = transfers.getJob(latest.jobId) ?: latest
                transfers.markFailed(persisted, error, cleanupStaging = true)
                throw error
            }
        } catch (error: Throwable) {
            Timber.tag("ProviderTransfer").e(error, "Background download failed")
        } finally {
            running.remove(key)
            stopIfIdle()
        }
    }

    private fun ProviderTab.withGlobal(): ProviderTab =
        ProviderCredentials.attachAvailable(this, resolverRegistry.selectedProvider, secrets)

    private fun notify(title: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(title))
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.provider_download))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Provider downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stopIfIdle() {
        if (running.isNotEmpty()) return
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "provider_transfer"
        private const val NOTIFICATION_ID = 51
        private const val EXTRA_TAB = "tabId"
        private const val EXTRA_ITEM = "itemId"

        fun start(context: Context, tabId: String, itemId: String) {
            val intent = Intent(context, ProviderTransferService::class.java)
                .putExtra(EXTRA_TAB, tabId)
                .putExtra(EXTRA_ITEM, itemId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
