package moe.rikaaa0928.uot

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class UotGrpc : Service() {
    var client: UogClient? = null
    val channelId = "UogChannel"
    var connectivityManager: ConnectivityManager? = null
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        startForegroundService()
        // TODO: Initialize gRPC stream here
        val sharedPreferences = getSharedPreferences("AppConfig", MODE_PRIVATE)
        client = UogClient(
            sharedPreferences.getString("ListenPort", "0")!!.toInt(),
            sharedPreferences.getString("GrpcEndpoint", "")!!,
            sharedPreferences.getString("Password", "")!!,
            connectivityManager!!
        )
//        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
//        registerReceiver(client, filter)
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager!!.registerNetworkCallback(networkRequest, client!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: Handle gRPC stream start/stop based on intent extras
        client!!.start()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Clean up gRPC stream here
        client!!.stop()
        connectivityManager!!.unregisterNetworkCallback(client!!)
//        client!!.destroy()
//        unregisterReceiver(client)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "uog", NotificationManager.IMPORTANCE_DEFAULT);
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager;
            notificationManager.createNotificationChannel(channel);
        }
    }

    private fun startForegroundService() {
        try {
            createNotificationChannel();
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("uog")
                .setContentText("uog is running")
                // Create the notification to display while the service is running
                .build()
            ServiceCompat.startForeground(
                /* service = */ this,
                /* id = */ 100, // Cannot be 0
                /* notification = */ notification,
                /* foregroundServiceType = */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                Log.e("startForegroundService", "not allowed", e)
                // App not in a valid state to start foreground service
                // (e.g. started from bg)
            } else {
                Log.e("startForegroundService", "other", e)
            }
            // ...
        }
    }
}