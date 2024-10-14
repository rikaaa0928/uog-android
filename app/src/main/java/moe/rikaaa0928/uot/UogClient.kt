package moe.rikaaa0928.uot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.protobuf.ByteString
import dad.xiaomi.uog.Udp
import dad.xiaomi.uog.UdpServiceGrpcKt
import io.grpc.android.AndroidChannelBuilder
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class UogClient(val lPort: Int, val endpoint: String, val password: String) : BroadcastReceiver() {
    //    private var service: UdpServiceGrpcKt.UdpServiceCoroutineStub? = null
    var req: Channel<Udp.UdpReq>? = null
    var udpSocket: AtomicReference<DatagramSocket?> = AtomicReference(null);
    private val stop = AtomicBoolean(true)
    private val waitNet: AtomicReference<CountDownLatch> = AtomicReference(null)
    private var lastAddr = ""
    private var lastPort = 0
    private val bufferSize = 65535;
    private val random = Random(1)

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        if (req != null) {
            return
        }
        stop.set(false)
        val url = URL(endpoint)
        val builder = AndroidChannelBuilder.forAddress(url.host, url.port)
        if (!url.protocol.equals("https")) {
            builder.usePlaintext()
        }
        GlobalScope.launch {
            while (!stop.get()) {
                try {
                    val id = random.nextInt(1000);
                    val channel = builder.build()
                    val service = UdpServiceGrpcKt.UdpServiceCoroutineStub(channel)
                    if (req != null) {
                        Log.d("UogClient", "reconnect req")
                        req!!.close()
                    }
                    if (udpSocket.get() != null) {
                        Log.d("UogClient", "re-listen udp")
                        udpSocket.get()!!.close()
                    }
                    req = Channel<Udp.UdpReq>()
                    val res = service.startStream(req!!.consumeAsFlow());
                    udpSocket.set(DatagramSocket(lPort))
                    udpSocket.get()!!.soTimeout = 1000 * 3

//                    val job = GlobalScope.launch {

//                    }

                    GlobalScope.launch {
                        try {
                            res.collect { t ->
                                val readBytes = t.payload.toByteArray()
                                if (lastAddr.isEmpty()) {
                                    return@collect
                                }
                                val packet = DatagramPacket(readBytes, readBytes.size)
                                packet.address = InetAddress.getByName(lastAddr)
                                packet.port = lastPort
                                udpSocket.get()!!.send(packet)
                            }
                        } catch (ignore: Exception) {

                        } finally {
                            Log.d("UogClient", "grpc read stop: $id")
                            udpSocket.get()!!.close()
//                            job.cancel()
                        }
                    }
//                    job.join()

                    val buffer = ByteArray(bufferSize)
                    val packet = DatagramPacket(buffer, bufferSize)
                    while (!stop.get()) {
                        try {
                            udpSocket.get()!!.receive(packet)
                            if (lastAddr.isEmpty()) {
                                lastAddr = packet.address.hostName;
                                lastPort = packet.port
                            } else if (lastAddr != packet.address.hostName || lastPort != packet.port) {
                                lastAddr = ""
                                lastPort = 0
                                break
                            }
                            val receivedData = packet.data.copyOfRange(0, packet.length)
                            req!!.send(
                                Udp.UdpReq.newBuilder().setAuth(password)
                                    .setPayload(ByteString.copyFrom(receivedData)).build()
                            )
                        } catch (e: Exception) {
                            if (e !is SocketTimeoutException) {
                                Log.e("UpgClient", "grpc write", e)
                                break
                            } else {
                                if (waitNet.get() != null) {
                                    Log.d("UogClient", "udp receive break no net: $id")
                                    break
                                }
                            }
                        }
                    }

                    Log.d("UogClient", "grpc write stopping: $id")
                    channel.shutdownNow()
                    while (true) {
                        val r = channel.awaitTermination(10, TimeUnit.MILLISECONDS)
                        if (r) {
                            break
                        }
                    }
                    Log.d("UogClient", "grpc write stop: $id")
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
        if (req == null) {
            return
        }
        try {
            req!!.close()
        } catch (e: Exception) {
            Log.e("UogClient", "req close", e)
        }
        try {
            udpSocket.get()!!.close()
        } catch (e: Exception) {
            Log.e("UogClient", "udpSocket close", e)
        }
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