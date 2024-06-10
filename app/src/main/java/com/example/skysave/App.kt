package com.example.skysave

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration


class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        val config = RealmConfiguration.Builder().build()
        Realm.setDefaultConfiguration(config)
    }
}