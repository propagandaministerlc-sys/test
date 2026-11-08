package app.klar

import android.app.Application

class KlarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HearingRepository.init(this)
    }
}
