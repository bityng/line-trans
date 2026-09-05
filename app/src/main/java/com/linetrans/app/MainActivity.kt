package com.linetrans.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.linetrans.app.data.DocRepository
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.ui.AppRoot
import com.linetrans.app.ui.theme.LineTransTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepository.init(applicationContext)
        DocRepository.init(applicationContext)
        setContent {
            LineTransTheme {
                AppRoot()
            }
        }
    }
}
