package com.sbtracker

import android.app.Application
import com.sbtracker.data.Db
import com.sbtracker.data.Prefs

/**
 * App-scoped singletons, poor-man's DI. One object graph, no framework magic.
 */
class App : Application() {

    val db:    Db    by lazy { Db.get(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    companion object {
        private lateinit var INSTANCE: App
        fun get(): App = INSTANCE
    }
}
