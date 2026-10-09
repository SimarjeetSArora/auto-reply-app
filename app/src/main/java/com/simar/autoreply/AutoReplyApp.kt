package com.simar.autoreply

import android.app.Application
import com.simar.autoreply.data.AppStore

class AutoReplyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
    }
}
