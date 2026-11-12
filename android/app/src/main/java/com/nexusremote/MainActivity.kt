package com.nexusremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nexusremote.data.local.DevicePreferences
import com.nexusremote.data.local.NexusDatabase
import com.nexusremote.discovery.NetworkDiscoveryManager
import com.nexusremote.protocols.ProtocolRegistry
import com.nexusremote.security.KeystoreSecretManager
import com.nexusremote.ui.NexusRemoteApp
import com.nexusremote.viewmodel.DeviceManagementViewModel
import com.nexusremote.viewmodel.RemoteControlViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize core Singletons
        val database = NexusDatabase.getInstance(applicationContext)
        val deviceDao = database.deviceDao()
        val keystoreSecretManager = KeystoreSecretManager(applicationContext)
        val discoveryManager = NetworkDiscoveryManager(applicationContext)
        val devicePreferences = DevicePreferences(applicationContext)
        val protocolRegistry = ProtocolRegistry(applicationContext, keystoreSecretManager)

        val deviceViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceManagementViewModel(deviceDao, discoveryManager, devicePreferences, keystoreSecretManager) as T
                }
            }
        )[DeviceManagementViewModel::class.java]

        val remoteViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RemoteControlViewModel(protocolRegistry, applicationContext, devicePreferences) as T
                }
            }
        )[RemoteControlViewModel::class.java]

        setContent {
            NexusRemoteApp(
                deviceViewModel = deviceViewModel,
                remoteViewModel = remoteViewModel
            )
        }
    }
}
