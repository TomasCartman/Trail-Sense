package com.kylecorry.trail_sense.calibration.ui

import android.os.Bundle
import androidx.preference.*
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.GPS
import com.kylecorry.trailsensecore.infrastructure.sensors.gps.IGPS
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedGPS
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideGPS
import com.kylecorry.trail_sense.shared.views.CoordinatePreference
import com.kylecorry.trailsensecore.domain.geo.Coordinate
import com.kylecorry.trailsensecore.infrastructure.sensors.SensorChecker
import com.kylecorry.trailsensecore.infrastructure.system.IntentUtils
import com.kylecorry.trailsensecore.infrastructure.system.PermissionUtils
import com.kylecorry.trailsensecore.infrastructure.time.Throttle


class CalibrateGPSFragment : PreferenceFragmentCompat() {

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val sensorService by lazy { SensorService(requireContext()) }
    private val sensorChecker by lazy { SensorChecker(requireContext()) }
    private val throttle = Throttle(20)

    private lateinit var locationTxt: Preference
    private lateinit var autoLocationSwitch: SwitchPreferenceCompat
    private lateinit var permissionBtn: Preference
    private lateinit var locationOverridePref: CoordinatePreference
    private val formatService by lazy { FormatService(requireContext()) }

    private lateinit var gps: IGPS
    private lateinit var realGps: IGPS

    private var wasUsingRealGPS = false
    private var wasUsingCachedGPS = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.gps_calibration, rootKey)
        wasUsingRealGPS = shouldUseRealGPS()
        wasUsingCachedGPS = shouldUseCachedGPS()
        gps = sensorService.getGPS()
        realGps = getRealGPS()
        bindPreferences()
    }

    private fun bindPreferences() {
        locationTxt = findPreference(getString(R.string.pref_holder_location))!!
        autoLocationSwitch = findPreference(getString(R.string.pref_auto_location))!!
        permissionBtn = findPreference(getString(R.string.pref_gps_request_permission))!!
        locationOverridePref = findPreference(getString(R.string.pref_gps_override))!!
        locationOverridePref.setGPS(realGps)
        locationOverridePref.setLocation(prefs.locationOverride)
        locationOverridePref.setTitle(getString(R.string.pref_gps_override_title))

        locationOverridePref.setOnLocationChangeListener {
            prefs.locationOverride = it ?: Coordinate.zero
            resetGPS()
            update()
        }

        autoLocationSwitch.setOnPreferenceClickListener {
            locationOverridePref.isEnabled = isLocationOverrideEnabled()
            resetGPS()
            update()
            true
        }

        permissionBtn.setOnPreferenceClickListener {
            val intent = IntentUtils.appSettings(requireContext())
            startActivityForResult(intent, 1000)
            true
        }

        update()
    }

    override fun onResume() {
        super.onResume()
        if (gps.hasValidReading) {
            update()
        }
        startGPS()
    }

    override fun onPause() {
        super.onPause()
        stopGPS()
        locationOverridePref.pause()
    }

    private fun resetGPS() {
        stopGPS()
        gps = sensorService.getGPS()
        startGPS()
    }

    private fun startGPS() {
        gps.start(this::onLocationUpdate)
    }

    private fun stopGPS() {
        gps.stop(this::onLocationUpdate)
    }


    private fun onLocationUpdate(): Boolean {
        update()
        return true
    }

    private fun resetRealGPS() {
        locationOverridePref.pause()
        realGps = getRealGPS()
        locationOverridePref.setGPS(realGps)
    }

    private fun getRealGPS(): IGPS {
        return when {
            shouldUseRealGPS() -> {
                GPS(requireContext())
            }
            shouldUseCachedGPS() -> {
                CachedGPS(requireContext())
            }
            else -> {
                OverrideGPS(requireContext())
            }
        }
    }

    private fun isLocationOverrideEnabled(): Boolean {
        // Either there are no other options for GPS or auto location is off
        return !isAutoGPSPreferenceEnabled() || !prefs.useAutoLocation
    }

    private fun isAutoGPSPreferenceEnabled(): Boolean {
        // Only disable when GPS permission is denied
        return PermissionUtils.isLocationEnabled(requireContext())
    }

    private fun shouldUseCachedGPS(): Boolean {
        // Permission is granted, but GPS is disabled
        return PermissionUtils.isLocationEnabled(requireContext()) && !sensorChecker.hasGPS()
    }

    private fun shouldUseRealGPS(): Boolean {
        // When both permission is granted and GPS is enabled
        return sensorChecker.hasGPS()
    }

    private fun update() {
        if (throttle.isThrottled()) {
            return
        }

        val useReal = shouldUseRealGPS()
        val useCached = shouldUseCachedGPS()

        if (useReal != wasUsingRealGPS || useCached != wasUsingCachedGPS){
            resetRealGPS()
            resetGPS()
            wasUsingCachedGPS = useCached
            wasUsingRealGPS = useReal
        }


        permissionBtn.isVisible = !isAutoGPSPreferenceEnabled()
        autoLocationSwitch.isEnabled = isAutoGPSPreferenceEnabled()
        locationOverridePref.isEnabled = isLocationOverrideEnabled()

        locationTxt.summary = formatService.formatLocation(gps.location)
    }


}