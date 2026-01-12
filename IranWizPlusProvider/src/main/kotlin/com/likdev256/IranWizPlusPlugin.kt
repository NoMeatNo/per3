package com.likdev256

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IranWizPlusPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(IranWizPlusProvider())
    }
}
