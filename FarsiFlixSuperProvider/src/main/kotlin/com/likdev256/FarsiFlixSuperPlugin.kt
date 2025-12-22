package com.likdev256

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FarsiFlixSuperPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FarsiFlixSuperProvider())
    }
}
