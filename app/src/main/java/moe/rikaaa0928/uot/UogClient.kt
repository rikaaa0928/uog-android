package moe.rikaaa0928.uot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import uniffi.uog.startClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UogClient(val lPort: Int, val endpoint: String, val password: String) : BroadcastReceiver() {
    //    private var service: UdpServiceGrpcKt.UdpServiceCoroutineStub? = null

    private val stop = AtomicBoolean(true)
    private val waitNet: AtomicReference<CountDownLatch> = AtomicReference(null)


    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        stop.set(false)
        GlobalScope.launch {
            while (!stop.get()) {
                try {
                    val res = startClient("127.0.0.1:$lPort", endpoint, password)
                    Log.e("UogClient", "startClient exit $res")
                } catch (e: Exception) {
                    Log.e("UogClient", "all", e)
                } finally {
                    val l = waitNet.get()
                    if (l != null) {
                        l.await()
                    } else {
                        TimeUnit.SECONDS.sleep(1)
                    }
                }
            }
            Log.d("UotClient", "exit main loop")
        }
    }

    fun stop() {
        stop.set(true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var isWifiConnected = false
            var isMobileDataConnected = false


            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities != null) {
                isWifiConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                isMobileDataConnected =
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
            if (!isWifiConnected && !isMobileDataConnected) {
                waitNet.compareAndSet(null, CountDownLatch(1))
            } else {
                val l = waitNet.getAndSet(null)
                l?.countDown()
            }
        }
    }
}