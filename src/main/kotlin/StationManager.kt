package de.paull

import de.paull.lib.files.ConfigHandler
import org.json.JSONObject
import org.json.JSONArray

class StationManager() {

    companion object {
        val stationList : MutableList<StationMonitor> = mutableListOf()
        fun removeSelf(sm : StationMonitor) = stationList.remove(sm)
    }

    init {
        val stations = ConfigHandler.get("STATIONS").trim().split(Regex("\\s+"))
        for (eva in stations) {
            val station = StationMonitor(eva)
            println("Start monitoring for station $eva")
            stationList.add(station)
        }
    }

    fun getStationByEva(eva : String) : StationMonitor? {
        for (station in stationList)
            if (station.eva == eva)
                return station
        return null
    }

    fun toJSON() : JSONObject {
        val json = JSONObject()
        val array = JSONArray()
        stationList.forEach {s -> 
            val obj = JSONObject()
            obj.put("eva", s.eva).put("name", s.stationName)
            array.put(obj)
        }
        return json.put("list", array)
    }
}