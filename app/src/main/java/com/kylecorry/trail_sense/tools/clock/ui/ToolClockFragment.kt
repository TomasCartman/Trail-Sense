package com.kylecorry.trail_sense.tools.clock.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolClockBinding
import com.kylecorry.trail_sense.databinding.FragmentToolDepthBinding
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.clock.infrastructure.NextMinuteBroadcastReceiver
import com.kylecorry.trailsensecore.infrastructure.system.AlarmUtils
import com.kylecorry.trailsensecore.infrastructure.system.UiUtils
import com.kylecorry.trailsensecore.infrastructure.time.Intervalometer
import java.time.*
import java.time.temporal.ChronoUnit

class ToolClockFragment : Fragment() {
    private var _binding: FragmentToolClockBinding? = null
    private val binding get() = _binding!!

    private val formatService by lazy { FormatService(requireContext()) }
    private val sensorService by lazy { SensorService(requireContext()) }
    private val gps by lazy { sensorService.getGPS(false) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val timer = Intervalometer { update() }

    private var gpsTime = Instant.now()
    private var systemTime = Instant.now()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentToolClockBinding.inflate(inflater, container, false)
        binding.pipButton.setOnClickListener {
            sendNextMinuteNotification()
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        gps.start(this::onGPSUpdate)
        binding.updatingClock.visibility = View.VISIBLE
        binding.pipButton.visibility = View.INVISIBLE
        timer.interval(20)
    }

    override fun onPause() {
        super.onPause()
        gps.stop(this::onGPSUpdate)
        timer.stop()
    }

    private fun onGPSUpdate(): Boolean {
        gpsTime = gps.time
        systemTime = Instant.now()
        binding.updatingClock.visibility = View.INVISIBLE
        binding.pipButton.visibility = View.VISIBLE
        return false
    }

    private fun update() {
        val systemDiff = Duration.between(systemTime, Instant.now())
        val currentTime = gpsTime.plus(systemDiff)
        val utcTime = ZonedDateTime.ofInstant(currentTime, ZoneId.of("UTC"))
        val myTime = ZonedDateTime.ofInstant(currentTime, ZoneId.systemDefault())
        binding.utcClock.text =
            getString(R.string.utc_format, formatService.formatTime(utcTime.toLocalTime()))
        binding.clock.text = formatService.formatTime(myTime.toLocalTime())
        binding.date.text = formatService.formatDate(myTime)
        binding.analogClock.time = myTime.toLocalTime()
        binding.analogClock.use24Hours = prefs.use24HourTime
    }

    private fun sendNextMinuteNotification() {
        val systemDiff = Duration.between(systemTime, Instant.now())
        val currentTime = gpsTime.plus(systemDiff)
        val myTime = ZonedDateTime.ofInstant(currentTime, ZoneId.systemDefault())

        val sendTime = myTime.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)

        UiUtils.shortToast(
            requireContext(),
            getString(
                R.string.pip_notification_scheduled,
                formatService.formatTime(sendTime.toLocalTime())
            )
        )

        AlarmUtils.set(
            requireContext(),
            sendTime,
            NextMinuteBroadcastReceiver.pendingIntent(
                requireContext(),
                formatService.formatTime(sendTime.toLocalTime())
            ),
            exact = true,
            allowWhileIdle = true
        )

    }
}