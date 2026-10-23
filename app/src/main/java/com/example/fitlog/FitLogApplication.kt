package com.example.fitlog

import android.app.Application

class FitLogApplication : Application() {
    internal val container: AppContainer by lazy { AppContainer(this) }
}
