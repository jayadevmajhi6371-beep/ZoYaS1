package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NJAssistantApp
import com.example.R
import com.example.engine.ToolExecutionEngine
import com.example.manager.LiveSessionManager
import com.example.model.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

class NJAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sessionManager: LiveSessionManager
    private lateinit var toolEngine: ToolExecutionEngine

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NJAssistantService = this@NJAssistantService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        toolEngine = ToolExecutionEngine(this)
        sessionManager = LiveSessionManager(this, toolEngine, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Check if we should start listening immediately (wake word trigger would happen here)
        if (intent?.action == ACTION_START_LISTENING) {
            sessionManager.startSession()
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NJAssistantApp.CHANNEL_ID)
            .setContentTitle("NJ Assistant is Active")
            .setContentText("I'm here for you, sweetie. Just say my name.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun startAssistant() {
        sessionManager.startSession()
    }

    fun stopAssistant() {
        sessionManager.stopSession()
    }

    fun getAssistantState(): StateFlow<AssistantState> = sessionManager.state

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.stopSession()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_START_LISTENING = "com.example.ACTION_START_LISTENING"
    }
}
