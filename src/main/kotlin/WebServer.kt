package de.paull

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.*
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import de.paull.lib.files.ConfigHandler

class WebServer(val manager: StationManager) : HttpHandler {

    fun start() {
        val port = ConfigHandler.getInteger("PORT")
        if (port == -1) {
            System.err.println("Could not read port of config file... terminating")
            return
        }
        val server = HttpServer.create(InetSocketAddress(8080), 0)
        server.createContext("/", this)
        server.executor = null
        server.start()
        println("Server started at http://localhost:$port/")
    }

    override fun handle(e: HttpExchange?) {
        if (e == null) return

        if (e.requestMethod == "OPTIONS") {
            e.responseHeaders.add("Access-Control-Allow-Origin", "*")
            e.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            e.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
            e.sendResponseHeaders(204, -1)
            return
        }
        
        val url = e.requestURI.toASCIIString()
        if (!url.startsWith("/bahn/")) return

        if (url.startsWith("/bahn/list")) {
            val resp = manager.toJSON().toString()
            e.sendString(resp)
            return
        }

        if (url.startsWith("/bahn/eva/")) {
            val sections = url.split("/")
            if (sections.size < 2) return e.fileNotFound()
            val eva = sections[sections.size - 1].trim()
            val station = manager.getStationByEva(eva)
            if (station == null) return e.fileNotFound()
            return e.sendString(station.toJSON().toString())
        }

        // Every other request
        e.fileNotFound()
    }

    private fun HttpExchange.fileNotFound() {
        val resp = "Eva-Station not found\n".toByteArray(StandardCharsets.UTF_8)
        this.applyDefaultHeaders()
        this.sendResponseHeaders(404, resp.size.toLong())
        this.responseBody.use { it.write(resp) }
    }

    private fun HttpExchange.sendString(str: String) {
        val resp = "$str\n".toByteArray(StandardCharsets.UTF_8)
        this.applyDefaultHeaders()
        this.sendResponseHeaders(200, resp.size.toLong())
        this.responseBody.use { it.write(resp) }
    }

    private fun HttpExchange.applyDefaultHeaders() {
        this.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        this.responseHeaders.add("Access-Control-Allow-Origin", "*")
        this.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        this.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }
}