package moe.rikaaa0928.uot

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings.Global
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uniffi.uog.UogRust
//import uniffi.uog.startClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UogClient(
    val lPort: Int,
    val endpoint: String,
    val password: String,
    val connectivityManager: ConnectivityManager
) :
    ConnectivityManager.NetworkCallback() {
    //    private var service: UdpServiceGrpcKt.UdpServiceCoroutineStub? = null

    private val stop = AtomicBoolean(true)
    private val waitNet: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))
    private val c: AtomicReference<UogRust?> = AtomicReference(null)
    fun start() {
        stop.set(false)
        GlobalScope.launch {
            while (!stop.get()) {
                try {
                    c.compareAndSet(null, UogRust())
                    val res = c.get()?.client("127.0.0.1:$lPort", endpoint, password)
                    Log.e("UogClient", "startClient exit $res")
                    c.getAndSet(null)?.stop()
                } catch (e: Throwable) {
                    Log.e("UogClient", "all", e)
                } finally {
                    val l = waitNet.get()
                    if (l != null) {
                        l.await()
                    } else if (!stop.get()) {
                        TimeUnit.SECONDS.sleep(1)
                    }
                }
            }
            Log.d("UotClient", "exit main loop")
        }
    }

    fun stop() {
        stop.set(true)
        c.getAndSet(null)?.stop()
    }

//    override fun onReceive(context: Context, intent: Intent) {
//        if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
//            try {
//                val connectivityManager =
//                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//                var isWifiConnected = false
//                var isMobileDataConnected = false
//
//
//                val network = connectivityManager.activeNetwork
//                val capabilities = connectivityManager.getNetworkCapabilities(network)
//
//                if (capabilities != null) {
//                    isWifiConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
//                    isMobileDataConnected =
//                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
//                }
//                if (!isWifiConnected && !isMobileDataConnected) {
//                    waitNet.compareAndSet(null, CountDownLatch(1))
//                } else {
//                    val l = waitNet.getAndSet(null)
//                    l?.countDown()
//                }
//            } catch (e: Exception) {
//                Log.e("UogClient", "onReceive", e)
//            }
//        }
//    }

    override fun onAvailable(network: Network) {
        Log.d("NetworkCallback", "Network onAvailable: $network")
        // 网络可用时调用
        val l = waitNet.getAndSet(null)
        l?.countDown()
    }

    override fun onLost(network: Network) {
        // 网络丢失时调用
        Log.d("NetworkCallback", "Network onLost: $network")
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            Log.d("NetworkCallback", "设备当前没有活跃的网络连接")
            waitNet.compareAndSet(null, CountDownLatch(1))
        } else {
            Log.d("NetworkCallback", "设备仍有其他活跃的网络连接")
        }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        // 网络能力变化时调用,可以检查具体的网络类型
        Log.d("NetworkCallback", "Network onCapabilitiesChanged: $network $networkCapabilities")
    }
}