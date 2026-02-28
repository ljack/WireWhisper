package com.wirewhisper.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wirewhisper.MainActivity
import com.wirewhisper.R
import com.wirewhisper.WireWhisperApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Core VPN service that captures all device traffic via a TUN interface.
 *
 * Lifecycle:
 * 1. [onStartCommand] with ACTION_START → establishes VPN, starts foreground, launches [TunProcessor]
 * 2. [onStartCommand] with ACTION_STOP → tears down VPN
 * 3. [onRevoke] → system revoked VPN permission
 *
 * The service does NOT block traffic in Phase 1; it mirrors all packets through
 * a protected relay while extracting metadata for the monitoring pipeline.
 */
class WireWhisperVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.wirewhisper.vpn.START"
        const val ACTION_STOP = "com.wirewhisper.vpn.STOP"
        private const val TAG = "WireWhisperVpn"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "wirewhisper_vpn"
        private const val MAX_PACKET_SIZE = 32_767
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunProcessor: TunProcessor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startVpn()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startVpn() {
        if (tunInterface != null) {
            Log.w(TAG, "VPN already running")
            return
        }

        val tun = establishTunInterface()
        if (tun == null) {
            Log.e(TAG, "Failed to establish TUN interface")
            stopSelf()
            return
        }
        tunInterface = tun

        val app = application as WireWhisperApp
        tunProcessor = TunProcessor(
            tunFd = tun,
            vpnService = this,
            flowTracker = app.flowTracker,
            uidResolver = app.uidResolver,
            hostnameResolver = app.hostnameResolver,
            scope = serviceScope,
        )
        tunProcessor!!.start()

        app.vpnRunning.value = true
        Log.i(TAG, "VPN started")
    }

    private fun stopVpn() {
        val app = applicationContext as? WireWhisperApp
        tunProcessor?.stop()
        tunProcessor = null

        tunInterface?.close()
        tunInterface = null

        app?.vpnRunning?.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun establishTunInterface(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("WireWhisper")
                // IPv4 TUN address – in a private range unlikely to collide
                .addAddress("10.120.0.1", 30)
                // IPv6 TUN address – unique local address
                .addAddress("fd00:db8:1::1", 126)
                // Capture all traffic
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                // DNS servers (traffic to these also flows through TUN, useful for DNS logging)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addDnsServer("2001:4860:4860::8888")
                .setMtu(MAX_PACKET_SIZE)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN builder failed", e)
            null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network Monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when WireWhisper is monitoring network traffic"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WireWhisperVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("WireWhisper Active")
            .setContentText("Monitoring network traffic")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }
}
