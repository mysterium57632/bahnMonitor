package de.paull

import de.paull.lib.files.*
import de.paull.lib.output.*

/**
 * Züge nach Aachen (RE1 / RE9)
 * Anzeige mit den nächsten 5 REs richtung Aachen gemessen nach Abfahrzeit des Bezugsbahnhof
 * Als Bezugsbahnhof kann man zwischen Bahnhöfen auf der Strecke wählen (HBF und Ehrenfeld?)
 * 
 * Idee: Alle Züge die über den Bezugsbahnhof fahren und ebenfalls in Aachen halten
 * -> Herausfinden, welche Züge von einem Bahnhof nach Aachen fahren
 * 
 * Jeder Zug soll einmal speichern:
 * - ID
 * - Linie
 * - Gleis am Bezugsbahnhof
 * - nächste Haltestelle (aktuelle Position)
 * - regulaere Ankunftszeit Bezugsbahnhof und Aachen
 * - reale Ankunftszeit Bezugsbahnhof und Aachen
 * - Verspätung in Minuten Bezugsbahnhof und Aachen
 */

fun main() {
    val m = Main()
    ConfigHandler("config.cfg", "CONFIG", m, m)
    ConfigHandler.printConfig()
    Main.DEBUG = ConfigHandler.get("DEBUG").equals("true")
    Output(Main.DEBUG, "de.paull")
    println("Running...")

    val manager = StationManager()
    WebServer(manager).start()
}

class Main : ConfigHandler.InitializeConfig, ConfigHandler.InitializeHiddenConfig {

    companion object {
        var DEBUG = false
    }

    override fun iniDefaultConfig(): HashMap<String, String> {
        val map = HashMap<String, String>()
        map["DEBUG"] = "false"
        map["LOG_FILE"] = "log/server.log"
        map["ERR_FILE"] = "log/server.err"
        map["STATIONS"] = "8000207 "
        map["END_STATION"] = "Aachen Hbf"
        map["PORT"] = "8888"
        return map
    }

    override fun iniHiddenConfig(): HashMap<String, String> {
        val map = HashMap<String, String>()
        map["API-KEY"] = "---"
        map["CLIENT-ID"] = "---"
        return map
    }
}