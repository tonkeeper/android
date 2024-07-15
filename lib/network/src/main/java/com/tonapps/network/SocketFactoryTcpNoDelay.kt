package com.tonapps.network

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class SocketFactoryTcpNoDelay: SocketFactory() {

    private val tcpNoDelay = true
    private val socketFactory = getDefault()

    override fun createSocket(): Socket {
        val socket = socketFactory.createSocket()
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(host: String?, port: Int): Socket {
        val socket = socketFactory.createSocket(host, port)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket {
        val socket = socketFactory.createSocket(host, port, localHost, localPort)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socket = socketFactory.createSocket(host, port)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket {
        val socket = socketFactory.createSocket(address, port, localAddress, localPort)
        socket.tcpNoDelay = tcpNoDelay
        return socket
    }
}