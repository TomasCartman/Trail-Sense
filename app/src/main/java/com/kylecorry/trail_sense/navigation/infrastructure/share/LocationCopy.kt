package com.kylecorry.trail_sense.navigation.infrastructure.share

import android.content.Context
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trailsensecore.domain.geo.Coordinate
import com.kylecorry.trailsensecore.infrastructure.persistence.Clipboard

class LocationCopy(private val context: Context, private val clipboard: Clipboard, private val prefs: UserPreferences) :
    ILocationSender {

    override fun send(location: Coordinate) {
        val formatService = FormatService(context)
        val locString = formatService.formatLocation(location)
        clipboard.copy(locString, context.getString(R.string.copied_to_clipboard_toast))
    }

}