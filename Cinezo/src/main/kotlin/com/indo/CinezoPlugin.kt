package com.indo

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CinezoPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Cinezo(context.applicationContext))
    }
}
