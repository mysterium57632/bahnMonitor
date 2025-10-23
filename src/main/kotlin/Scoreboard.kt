package de.paull

import java.time.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.mutableListOf
import org.json.JSONObject
import org.json.JSONArray

class Scoreboard(val isGlobal : Boolean = false, val dayTrainCount : Int = 3, val weekTrainCount : Int = 5) {

    companion object {
        fun scheduleForTime(task : () -> Unit) {
            val zone = ZoneId.of("Europe/Berlin")
            val targetTime = LocalTime.of(3, 0)
            val scheduler = Executors.newSingleThreadScheduledExecutor()

            fun schedule() {
                val now = ZonedDateTime.now(zone)
                var nextRun = now.withHour(targetTime.hour).withMinute(targetTime.minute).withSecond(0).withNano(0)
                if (now >= nextRun) nextRun = nextRun.plusHours(24)
                val delay = Duration.between(now, nextRun).toMillis()

                scheduler.schedule({
                    task()
                    schedule()
                }, delay, TimeUnit.MILLISECONDS)
            }
            schedule()
        }

        val allScoreboards : MutableList<Scoreboard> = mutableListOf()
        val global = Scoreboard(true, 3, 5)

        val compareDelay = Comparator<Train> { t1, t2 ->
            t2.delay.toInt().compareTo(t1.delay.toInt())
        }

        val comparePlannedDeparture = Comparator<Train> { t1, t2 ->
            t1.departureTime.toLong().compareTo(t2.departureTime.toLong())
        }

        val onNewDay = {
            for (s in allScoreboards) s.newDay()
        }

        init {
            scheduleForTime(onNewDay)
        }
    }

    private var topToday: CopyOnWriteArrayList<Train> = CopyOnWriteArrayList()
    private var topWeek: CopyOnWriteArrayList<Train> = CopyOnWriteArrayList()

    init { allScoreboards.add(this) }

    fun registerTrain(train : Train) {
        if (!topToday.contains(train)) {
            topToday.add(train)
            topToday.sortWith(compareDelay)
            if (Main.DEBUG && topToday.size == 0) System.err.println("Scoreboard: Should not be empty!")
            topToday.cleanUp()
        }

        if (!topWeek.contains(train)) {
            topWeek.add(train)
            topToday.sortWith(compareDelay)
            topWeek.cleanUp()
        }

        if (!isGlobal) global.registerTrain(train)
    }

    fun getThree() : LocalDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).withHour(3).withMinute(0).withSecond(0).withNano(0)

    fun newDay() {
        // remove yesterdays trains out of list
        val newList : CopyOnWriteArrayList<Train> = CopyOnWriteArrayList()
        val today = getThree()
        for (train in topToday)
            if (!train.departureDateTime.isBefore(today))
                newList.add(train)
        topToday = newList


        // remove trains older than 7 days out of this list
        val newWeek : CopyOnWriteArrayList<Train> = CopyOnWriteArrayList()
        val weekAgo = today.minusDays(7)
        for (train in topWeek)
            if (!train.departureDateTime.isBefore(weekAgo))
                newWeek.add(train)
        // Check if there are more than 5 trains in the list
        if (newWeek.size < weekTrainCount) {
            topWeek = newWeek
            return
        }
        // remove all trains older than the top n
        var oldest = newWeek[0].departureDateTime
        for (i in 1..weekTrainCount)
            if (newWeek[i].departureDateTime.isBefore(oldest))
                oldest = newWeek[i].departureDateTime
        topWeek = CopyOnWriteArrayList(newWeek.filter {it.departureDateTime.isAfter(oldest.minusMinutes(1))})
    }

    fun getTopWeek() : List<Train> = topWeek.copyAndReduce(weekTrainCount)

    fun getTopDay() : List<Train> = topToday.copyAndReduce(dayTrainCount)

    fun MutableList<Train>.copyAndReduce(num : Int) : List<Train> {
        val value : MutableList<Train> = mutableListOf()
        // Get tomorrows day
        var next = getThree()
        if (next.isBefore(LocalDateTime.now(ZoneId.of("Europe/Berlin"))))
            next = next.plusDays(1)
        for (train in this)
            if (train.departureDateTime.isBefore(next))
                value.add(train)
        return value.filter { it.delay.toInt() > 0 }.take(num)
    }

    fun MutableList<Train>.cleanUp() : MutableList<Train> {
        return this.filter {it.hasDeparted() && it.delay.toInt() == 0}.toMutableList()
    }

    fun toJSON() : JSONObject {
        val json = JSONObject()
        val arrayToday = JSONArray()
        getTopDay().forEach {t -> arrayToday.put(t.toJSON())}
        json.put("day", arrayToday)
        val arrayWeek = JSONArray()
        getTopWeek().forEach {t -> arrayWeek.put(t.toJSON())}
        json.put("week", arrayWeek)
        return json
    }
}