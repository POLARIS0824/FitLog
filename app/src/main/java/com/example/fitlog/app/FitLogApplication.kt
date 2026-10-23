package com.example.fitlog.app

import android.app.Application

class FitLogApplication : Application() {
    internal val container: AppContainer by lazy { AppContainer(this) }
}
