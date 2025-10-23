package de.paull

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.collections.List
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.json.JSONArray
import org.json.JSONObject
import javax.xml.parsers.DocumentBuilderFactory
import de.paull.lib.files.ConfigHandler

class StationMonitor(val eva: String = "8000207") : Runnable {

    private val BASE_URL = "https://apis.deutschebahn.com/db-api-marketplace/apis/timetables/v1/"
    private val api = ConfigHandler.get("API-KEY")
    private val clientId = ConfigHandler.get("CLIENT-ID")
    private val lastStation = ConfigHandler.get("END_STATION")
    private val httpClient = OkHttpClient()
    private val thread = Thread(this)
    private val trainsInList = 10
    private val scoreboard = Scoreboard()

    private var running = true
    private var trains: MutableList<Train> = mutableListOf()
    private var lastRequest: Long = 0
    var stationName = ""
        private set

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
        thread.start()
    }

    fun stop() {
        running = false
        thread.interrupt()
        onShutdown()
    }

    private fun MutableList<Train>.getTrainById(id: String): Train? {
        for (train in this)
            if (train.sameId(id))
                return train
        return null
    }

    private fun load() {
        fun getTimeDate(hours: Int = 0): List<String> {
            val nowInBerlin = ZonedDateTime.now(ZoneId.of("Europe/Berlin")).plusHours(hours.toLong())
            val date = nowInBerlin.format(DateTimeFormatter.ofPattern("yyMMdd"))
            val hour = nowInBerlin.format(DateTimeFormatter.ofPattern("HH"))
            return listOf(date, hour)
        }

        // Filter departed trains out
        val newList = mutableListOf<Train>()
        for (train in trains) {
            if (!train.hasDeparted())
                newList.add(train)
        }

        // If trains have not changed, the recent changes will do
        if (newList.size == trainsInList) return trains.getChangesForTrains()

        // Fill up the list with later trains
        var hourOffset = 0
        while (newList.size < trainsInList && hourOffset < 10) {
            val list = getTimeDate(hourOffset)
            newList.getPlannedTrainsByTime(list[0], list[1])
            hourOffset++
        }
        newList.getChangesForTrains(true)
        // Fix order and size of list
        trains = newList.sorted().toMutableList()
        while (trains.size > trainsInList) trains.removeAt(trains.size - 1)
        trains.forEach{train -> scoreboard.registerTrain(train)}
    }

    private fun stringToNode(raw : String?) : Node? {
        if (raw == null) return null
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc: Document = builder.parse(raw.byteInputStream())
        doc.documentElement.normalize()
        return doc.getElementsByTagName("timetable")?.item(0)
    }

    private fun MutableList<Train>.getPlannedTrainsByTime(date : String, time : String) {
        val raw = request("${BASE_URL}plan/$eva/$date/$time")
        val list = stringToNode(raw)
        if (list == null) return

        stationName = list.attributes.item(0).nodeValue

        for (node in ChildIterator(list.childNodes)) {
            val id = node.attributes.getNamedItem("id")?.nodeValue ?: continue
            // Get Destination
            var dp : Node? = null
            for (child in ChildIterator(node.childNodes)) {
                if (child.nodeNa        this.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")me.trim() != "dp") continue
                dp = child
                break
            }
            // filter for all to Aachen Hbf
            if (dp == null) continue
            if (!(dp.attributes.getNamedItem("ppth")?.nodeValue?.contains("Aachen Hbf") ?: false)) continue
            if (this.getTrainById(id) != null) continue // Filter trains already monitored
            val train = Train(node, stationName)
            if (!train.free) continue // Filter "free" trains
            this.add(train)
        }
    }

    private fun MutableList<Train>.getChangesForTrains(f : Boolean = false) {
        var full = f
        if (System.currentTimeMillis() - lastRequest > 60000) full = true
        val raw = request("${BASE_URL}${if (full) "f" else "r"}chg/$eva")
        val list = stringToNode(raw)
        if (list == null) return
        lastRequest = System.currentTimeMillis()

        for (node in ChildIterator(list.childNodes)) {
            val id = node.attributes?.getNamedItem("id")?.nodeValue ?: continue
            val train = getTrainById(id) ?: continue
            train.applyChanges(node)
        }
    }

    private fun request(url: String) : String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("DB-Client-Id", clientId)
            .addHeader("DB-Api-Key", api)
            .addHeader("accept", "application/xml")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                response.close()
                return body
            } else if (response.code == 400) {
                System.err.println("Invalid request for url:\n$url\nDisabling monitoring for this station...")
                StationManager.removeSelf(this)
                stop()
            }
        }
        return null
    }

    fun toJSON() : JSONObject {
        val json = JSONObject()
        val array = JSONArray()
        trains.forEach {t -> array.put(t.toJSON())}
        json.put("next", array)
        return json.put("score", scoreboard.toJSON())
    }

    /**
     * Main Thread for updating the trains
     */
    override fun run() {
        while (running) {
            load()
            if (Main.DEBUG && trains.size == 0) System.err.println("StationMonitor: No trains in list")
            sleep()
        }
    }

    fun sleep() {
        try {
            Thread.sleep(20000) // 30 sec
        } catch (_: InterruptedException) {}
    }

    fun onShutdown() {
        try {
            httpClient.connectionPool.evictAll()
            val executor = httpClient.dispatcher.executorService
            executor.shutdown()
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                executor.shutdownNow()
            httpClient.cache?.close()
        } catch (e: Exception) {
            println("Error during OkHttp shutdown: ${e.message}")
        }
    }
}
