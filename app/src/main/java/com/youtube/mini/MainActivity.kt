package com.youtube.mini

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtube.mini.ui.MiniTubeScreen
import com.youtube.mini.ui.MiniTubeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }

    @Composable
    private fun AppRoot() {
        val vm: MiniTubeViewModel = viewModel(factory = MiniTubeViewModel.factory(application))
        var granted by remember { mutableStateOf(hasReadVideoPermission()) }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(granted) {
            if (granted) {
                vm.refreshVideos()
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    granted = hasReadVideoPermission()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        MiniTubeScreen(
            hasMediaPermission = granted,
            permission = mediaPermission(),
            onPermissionResult = { granted = it },
            vm = vm,
        )
    }

    private fun mediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasReadVideoPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            mediaPermission(),
        ) == PackageManager.PERMISSION_GRANTED
    }
}
