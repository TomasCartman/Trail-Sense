package com.kylecorry.trail_sense.database

import android.content.Context
import com.kylecorry.sensorfilters.KalmanFilter
import com.kylecorry.trail_sense.models.PressureAltitudeReading
import java.time.Duration
import java.time.Instant
import java.util.*

object PressureHistoryRepository: Observable(),
    IPressureHistoryRepository {

    private const val FILE_NAME = "pressure.csv"
    private val keepDuration: Duration = Duration.ofHours(48)

    private var readings: MutableList<PressureAltitudeReading> = mutableListOf()

    private var loaded = false

    override fun getAll(context: Context): List<PressureAltitudeReading> {
        if (!loaded){
            loadFromFile(
                context
            )
        }
        return readings

//        val newReadings = mutableListOf<PressureAltitudeReading>()
//        if (readings.isNotEmpty()){
//            val pressureFilter = KalmanFilter(0.05, 0.75, readings.first().pressure.toDouble())
//            val altitudeFilter = KalmanFilter(0.5, 0.75, readings.first().altitude.toDouble())
//            for (reading in readings){
//                val pressure = pressureFilter.filter(reading.pressure.toDouble()).toFloat()
//                val altitude = altitudeFilter.filter(reading.altitude.toDouble()).toFloat()
//                newReadings.add(PressureAltitudeReading(reading.time, pressure, altitude))
//            }
//        }
//
//        return newReadings
    }

    override fun add(context: Context, reading: PressureAltitudeReading): PressureAltitudeReading {
        if (!loaded){
            loadFromFile(
                context
            )
        }
        readings.add(reading)
        removeOldReadings()
        saveToFile(
            context
        )
        setChanged()
        notifyObservers()
        return reading
    }

    private fun removeOldReadings(){
        readings.removeIf { Duration.between(it.time, Instant.now()) > keepDuration }
    }

    private fun loadFromFile(context: Context) {
        val readings = mutableListOf<PressureAltitudeReading>()
        if (!context.getFileStreamPath(FILE_NAME).exists()) return
        context.openFileInput(FILE_NAME).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val splitLine = line.split(",")
                val time = splitLine[0].toLong()
                val pressure = splitLine[1].toFloat()
                val altitude = splitLine[2].toFloat()
                readings.add(
                    PressureAltitudeReading(
                        Instant.ofEpochMilli(time),
                        pressure,
                        altitude
                    )
                )
            }
        }
        loaded = true
        PressureHistoryRepository.readings = readings
        removeOldReadings()
        setChanged()
        notifyObservers()
    }

    private fun saveToFile(context: Context){
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
            val output = readings.joinToString("\n") { reading ->
                "${reading.time.toEpochMilli()},${reading.pressure},${reading.altitude}"
            }
            it.write(output.toByteArray())
        }
    }

}