package moe.rikaaa0928.uot

import android.util.Log
import com.google.protobuf.ByteString
import dad.xiaomi.uog.Udp
import dad.xiaomi.uog.UdpServiceGrpcKt
//import io.grpc.internal.DnsNameResolverProvider
import io.grpc.okhttp.OkHttpChannelBuilder
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
import java.util.concurrent.atomic.AtomicBoolean

class UogClient(val lPort: Int, val endpoint: String, val password: String) {
    //    private var service: UdpServiceGrpcKt.UdpServiceCoroutineStub? = null
    var req: Channel<Udp.UdpReq>? = null
    var udpSocket: DatagramSocket? = null
    private val stop = AtomicBoolean(true)
    private var lastAddr = ""
    private var lastPort = 0
    private val bufferSize = 65535;

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        if (req != null) {
            return
        }
        stop.set(false)
        GlobalScope.launch {
            while (!stop.get()) {
                try {
                    val url = URL(endpoint)
                    val builder = OkHttpChannelBuilder.forAddress(url.host, url.port)
                    if (!url.protocol.equals("https")) {
                        builder.usePlaintext()
                    }
                    val channel = builder.build()
                    val service = UdpServiceGrpcKt.UdpServiceCoroutineStub(channel)
                    if (req != null) {
                        req!!.close()
                    }
                    req = Channel<Udp.UdpReq>()
                    val res = service.startStream(req!!.consumeAsFlow());
                    udpSocket = DatagramSocket(lPort)
                    udpSocket!!.soTimeout = 1000 * 3
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
                                udpSocket!!.send(packet)
                            }
                        } catch (ignore: Exception) {

                        } finally {
                            Log.d("UogClient", "grpc read stop")
                        }
                    }
                    val buffer = ByteArray(bufferSize)
                    val packet = DatagramPacket(buffer, bufferSize)
                    while (!stop.get()) {
                        try {
                            udpSocket!!.receive(packet)
                            if (lastAddr.isEmpty()) {
                                lastAddr = packet.address.hostName;
                                lastPort = packet.port
                            } else if (lastAddr != packet.address.hostName || lastPort != packet.port) {
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
                                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100L)
                            }
                        }
                    }
                    Log.d("UogClient", "grpc write stop")
                } catch (e: Exception) {
                    Log.e("UogClient", "all", e)
                }
            }
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
            udpSocket!!.close()
        } catch (e: Exception) {
            Log.e("UogClient", "udpSocket close", e)
        }
    }
}