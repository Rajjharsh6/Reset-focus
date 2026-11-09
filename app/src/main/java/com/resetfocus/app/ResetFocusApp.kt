package com.resetfocus.app

import android.app.Application
import android.content.Context
import com.resetfocus.app.data.AppDatabase
import com.resetfocus.app.data.FocusState
import com.resetfocus.app.data.SettingsStore
import com.resetfocus.app.service.Notifier

class ResetFocusApp : Application() {
    lateinit var db: AppDatabase
    lateinit var settings: SettingsStore

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsStore(this)
        FocusState.init(this)
        Notifier.init(this)
    }
}

val Context.app: ResetFocusApp get() = applicationContext as ResetFocusApp
