package com.daylight.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.daylight.app.ui.*

class MainActivity : ComponentActivity() {
    private val model: DaylightViewModel by viewModels()
    private val incoming = mutableStateOf<String?>(null)
    private var routeHandled = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeHandled = savedInstanceState?.getBoolean("routeHandled") ?: false
        incoming.value = if (routeHandled) null else intent.getStringExtra("route")
        setContent { DaylightApp(model, incoming.value) {
            routeHandled = true
            incoming.value = null
            intent.removeExtra("route")
        } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeHandled = false
        incoming.value = intent.getStringExtra("route")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("routeHandled", routeHandled)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        model.execute(reschedule = true) { }
    }
}
