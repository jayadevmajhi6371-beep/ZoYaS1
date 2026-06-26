package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.PermissionsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val hasPermissions = viewModel.hasPermissions.value
        
        val launcher = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
          viewModel.checkPermissions(this)
        }

        LaunchedEffect(Unit) {
          viewModel.checkPermissions(this@MainActivity)
          viewModel.bindService(this@MainActivity)
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
          if (hasPermissions) {
            MainScreen(
              stateFlow = viewModel.assistantState,
              onToggleAssistant = { viewModel.toggleAssistant() }
            )
          } else {
            PermissionsScreen(
              onGrantPermissions = {
                launcher.launch(
                  arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.CALL_PHONE,
                    android.Manifest.permission.POST_NOTIFICATIONS
                  )
                )
              }
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    viewModel.unbindService(this)
  }
}
