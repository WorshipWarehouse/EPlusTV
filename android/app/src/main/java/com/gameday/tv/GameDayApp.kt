package com.gameday.tv

import android.app.Application
import android.content.Context

class GameDayApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as GameDayApp).container
    }
}
