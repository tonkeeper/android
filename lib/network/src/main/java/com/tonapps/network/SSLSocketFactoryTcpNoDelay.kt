package com.tonapps.network

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.util.Arrays
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class SSLSocketFactoryTcpNoDelay : SSLSocketFactory() {
    private val tcpNoDelay = true

    val trustManager: X509TrustManager
    val sslSocketFactory: SSLSocketFactory

    init {
        // From
        // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/-builder/ssl-socket-factory
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
            ("Unexpected default trust managers:"
                    + Arrays.toString(trustManagers))
        }
        trustManager = trustManagers[0] as X509TrustManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        sslSocketFactory = sslContext.socketFactory
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return sslSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return sslSocketFactory.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket {
        val socket = sslSocketFactory.createSocket()
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val socket = sslSocketFactory.createSocket(s, host, port, autoClose)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        val socket = sslSocketFactory.createSocket(host, port)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val socket = sslSocketFactory.createSocket(host, port, localHost, localPort)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        val socket = sslSocketFactory.createSocket(host, port)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val socket = sslSocketFactory.createSocket(address, port, localAddress, localPort)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }
}