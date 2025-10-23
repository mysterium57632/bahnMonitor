package de.paull

import org.w3c.dom.*
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalTime

class Train(val node: Node, stationName: String) : Comparable<Train> {

    // Timestamp format
    val format = DateTimeFormatter.ofPattern("yyMMddHHmm")

    // Fix data
    private val id: String = node.attributes?.getNamedItem("id")?.nodeValue ?: "unknown"
    var free = true
        private set
    private var type = ""               // Train type
    private var l = ""                  // Line number
    private var line = "unknown"        // Train type
    private var canceled = false

    // Changeable default data
    var departureTime = "unknown"
        private set
    val departureDateTime : LocalDateTime
        get() = LocalDateTime.parse(departureTime, format)
    private var platform = "unknown"
        set(value) {
            if (field != value)
                platformHistory += if (platformHistory == "") value else " -> $value"
            field = value
        }

    // Changeable data
    private var lastStation = stationName
    var realDepartureTime = ""
        private set
    private var platformHistory = ""

    // Other
    val delay : String
        get() {
            val t1 = LocalDateTime.parse(departureTime, format)
            val t2 = LocalDateTime.parse(realDepartureTime, format)
            return Duration.between(t1, t2).toMinutes().toString()
        }

    init {
        var info : Node? = null
        var arrival : Node? = null
        var departure : Node? = null

        ChildIterator(node.childNodes).forEach { node ->
            when(node.nodeName) {
                "tl" -> info = node
                "ar" -> arrival = node
                "dp" -> departure = node
            }
        }

        if (info != null) {
            type = info.getAttr("c", type)
            l = info.getAttr("n", l)
        }
        if (arrival != null) {
            platform = arrival.getAttr("pp", platform)
            l = arrival.getAttr("l", l)
        }
        if (departure != null) {
            platform = departure.getAttr("pp", platform)
            l = departure.getAttr("l", l)
            departureTime = departure.getAttr("pt", departureTime)
            val path = departure.getAttr("ppth", "")
            if (!path.isEmpty()) lastStation = path.split("|").last()
        }
        if (type == "ICE" || type == "EST" || type == "FLX") free = false
        line = type + l
        realDepartureTime = departureTime
    }

    fun applyChanges(node: Node) {
        var info : Node? = null
        var arrival : Node? = null
        var departure : Node? = null

        ChildIterator(node.childNodes).forEach { node ->
            when(node.nodeName) {
                "tl" -> info = node
                "ar" -> arrival = node
                "dp" -> departure = node
        }}

        realDepartureTime = departure.getAttr("ct", realDepartureTime)
        var path = departure.getAttr("ppth", "")
        if (!path.isEmpty()) lastStation = path.split("|").last()
        path = departure.getAttr("cpth", "")
        if (!path.isEmpty()) lastStation = path.split("|").last()
        platform = departure.getAttr("pp", platform)
    }

    fun hasDeparted(): Boolean {
        val format = DateTimeFormatter.ofPattern("yyMMddHHmm")
        val departureTime = LocalDateTime.parse(realDepartureTime, format)
        val now = LocalDateTime.now()
        return now.isAfter(departureTime.plusMinutes(5))
    }

    fun sameId(str : String) = id == str.trim()

    fun Node?.getAttr(name: String, default: String) = this?.attributes?.getNamedItem(name)?.nodeValue ?: default

    fun toJSON() : JSONObject {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val j = JSONObject()
        j.put("l", line)
        j.put("dep", departureDateTime.format(timeFormatter))
        j.put("dde", LocalDateTime.parse(realDepartureTime, format).format(timeFormatter))
        j.put("end", lastStation)
        j.put("plt", platformHistory)
        j.put("del", delay)
        j.put("can", canceled)
        j.put("dat", departureDateTime.format(dateFormatter))
        return j
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("Train $line (to $lastStation)\n")
        builder.append("-> dp $departureTime - $realDepartureTime ${if (delay.toInt() == 0) "" else "($delay)"} from $platform\n")
        builder.append("-> id: $id")
        return builder.toString()
    }

    /**
     * Compare departure time between two Trains
     */
    override fun compareTo(other: Train): Int {
        val dep = realDepartureTime.toLong()
        val oth = other.realDepartureTime.toLong()
        if (dep < oth) return -1
        if (dep > oth) return 1
        return 0
    }
}