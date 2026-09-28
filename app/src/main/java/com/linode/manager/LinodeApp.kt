package com.linode.manager

import android.app.Application

class LinodeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.linode.manager.data.CrashLog
            .install(this)
        container = AppContainer(this)
    }
}
