package moe.rikaaa0928.uot

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.gson.Gson

class UogGrpc : Service() {
    var client: UogClient? = null
    val channelId = "UogChannel"
    var connectivityManager: ConnectivityManager? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configJson = intent?.getStringExtra("config")
        if (configJson != null) {
            val config = Gson().fromJson(configJson, Config::class.java)
            if (client != null) {
                connectivityManager?.unregisterNetworkCallback(client!!)
                client?.stop()
                sendMessage("Client stopped")
            }
            initializeClient(config)
            client?.start()
        } else {
            Log.e("UotGrpc", "No configuration received")
            stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    private fun initializeClient(config: Config) {
        client = UogClient(
            config.listenPort.toInt(),
            config.grpcEndpoint,
            config.password,
            connectivityManager!!,
            applicationContext
        )

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager!!.registerNetworkCallback(networkRequest, client!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (client != null) {
            client?.stop()
//            sendMessage("Service destroyed, client stopped")
            connectivityManager?.unregisterNetworkCallback(client!!)
        }
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

    private fun sendMessage(message: String) {
        val intent = Intent(MainActivity.MESSAGE_ACTION).apply {
            setPackage(packageName)  // 确保广播只发送给本应用
            putExtra("message", message)
        }
        sendBroadcast(intent)
        // 添加日志以便调试
        Log.d("UogGrpc", "Sending broadcast message: $message")
    }
}
