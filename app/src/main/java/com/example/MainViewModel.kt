package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AssistantState
import com.example.service.NJAssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _isBound = mutableStateOf(false)
    val isBound: State<Boolean> = _isBound

    private var assistantService: NJAssistantService? = null

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _hasPermissions = mutableStateOf(false)
    val hasPermissions: State<Boolean> = _hasPermissions

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NJAssistantService.LocalBinder
            assistantService = binder.getService()
            _isBound.value = true
            
            viewModelScope.launch {
                assistantService?.getAssistantState()?.collect {
                    _assistantState.value = it
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _isBound.value = false
            assistantService = null
        }
    }

    fun checkPermissions(context: Context) {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        _hasPermissions.value = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, NJAssistantService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent) // Ensure it stays alive
    }

    fun unbindService(context: Context) {
        if (_isBound.value) {
            context.unbindService(serviceConnection)
            _isBound.value = false
        }
    }

    fun toggleAssistant() {
        if (_assistantState.value == AssistantState.IDLE) {
            assistantService?.startAssistant()
        } else {
            assistantService?.stopAssistant()
        }
    }
}
