package com.kylecorry.trail_sense.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigator.gps.GPS
import com.kylecorry.trail_sense.sensors.Barometer
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.abs

class BarometerAlarmReceiver: BroadcastReceiver(), Observer {

    private lateinit var context: Context
    private lateinit var barometer: Barometer
    private lateinit var gps: GPS

    private var hasLocation = false
    private var hasBarometerReading = false

    private val altitudeReadings = mutableListOf<Float>()
    private val pressureReadings = mutableListOf<Float>()

    private val MAX_BAROMETER_READINGS = 7
    private val MAX_GPS_READINGS = 5

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null){
            this.context = context
            barometer = Barometer(context)
            gps = GPS(context)

            gps.addObserver(this)
            gps.start(Duration.ofSeconds(1))

            barometer.addObserver(this)
            barometer.start()
        }
    }

    override fun update(o: Observable?, arg: Any?) {
        if (o == barometer) recordBarometerReading()
        if (o == gps) recordGPSReading()
    }

    private fun recordGPSReading(){
        altitudeReadings.add(gps.altitude.toFloat())

        if (altitudeReadings.size >= MAX_GPS_READINGS) {
            hasLocation = true
            gps.stop()
            gps.deleteObserver(this)

            if (hasBarometerReading) {
                gotAllReadings()
            }
        }
    }

    private fun getBestReadings(readings: List<Float>, threshold: Float = 5f): List<Float> {
        var bestReadings = mutableListOf<Float>()
        for (i in readings.indices){
            val same = mutableListOf<Float>()
            for (j in readings.indices){
                val diff = abs(readings[i] - readings[j])
                if (diff <= threshold){
                    same.add(readings[j])
                }
            }

            if (same.size > bestReadings.size){
                bestReadings = same
            }
        }

        return bestReadings
    }

    private fun getTrueAltitude(readings: List<Float>): Float {
        val bestReadings = getBestReadings(readings, 10f)

        val average = bestReadings.average().toFloat()

        if (average != 0f && bestReadings.size > MAX_GPS_READINGS / 2){
            return average
        }

        val lastAltitude = getLastAltitude()
        if (lastAltitude == 0.0 && bestReadings.isNotEmpty()){
            return bestReadings.average().toFloat()
        }

        return lastAltitude.toFloat()
    }

    private fun getTruePressure(readings: List<Float>): Float {
        val bestReadings = getBestReadings(readings, 0.1f)

        if (bestReadings.size > MAX_BAROMETER_READINGS / 2){
            return bestReadings.average().toFloat()
        }

        val lastPressure = getLastPressure()
        if (lastPressure == 0.0f && bestReadings.isNotEmpty()){
            return bestReadings.average().toFloat()
        }

        return lastPressure
    }

    private fun recordBarometerReading(){
        pressureReadings.add(barometer.pressure)

        if (pressureReadings.size >= MAX_BAROMETER_READINGS) {
            hasBarometerReading = true
            barometer.stop()
            barometer.deleteObserver(this)

            if (hasLocation) {
                gotAllReadings()
            }
        }
    }

    private fun gotAllReadings(){
        PressureHistoryRepository.add(context, PressureReading(Instant.now(), getTruePressure(pressureReadings), getTrueAltitude(altitudeReadings).toDouble()))

        createNotificationChannel()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sentAlert = prefs.getBoolean(context.getString(R.string.pref_just_sent_alert), false)
        val useSeaLevel = prefs.getBoolean(context.getString(R.string.pref_use_sea_level_pressure), false)

        if (WeatherUtils.isStormIncoming(PressureHistoryRepository.getAll(context), useSeaLevel)){

            val shouldSend = prefs.getBoolean(context.getString(R.string.pref_send_storm_alert), true)
            if (shouldSend && !sentAlert) {
                val builder = NotificationCompat.Builder(context, "Alerts")
                    .setSmallIcon(R.drawable.ic_alert)
                    .setContentTitle("Storm Alert")
                    .setContentText("A storm might be approaching")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                with(NotificationManagerCompat.from(context)) {
                    notify(0, builder.build())
                }
                prefs.edit {
                    putBoolean(context.getString(R.string.pref_just_sent_alert), true)
                }
            }
        } else {
            with(NotificationManagerCompat.from(context)) {
                cancel(0)
            }
            prefs.edit {
                putBoolean(context.getString(R.string.pref_just_sent_alert), false)
            }
        }


        Log.i("BarometerAlarmReceiver", "Got all readings recorded at ${ZonedDateTime.now()}")

    }

    private fun getLastAltitude(): Double {
        PressureHistoryRepository.getAll(context).reversed().forEach {
            if (it.altitude != 0.0) return it.altitude
        }

        return 0.0
    }

    private fun getLastPressure(): Float {
        PressureHistoryRepository.getAll(context).reversed().forEach {
            if (it.pressure != 0.0f) return it.pressure
        }

        return 0.0f
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alerts"
            val descriptionText = "Storm alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("Alerts", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}