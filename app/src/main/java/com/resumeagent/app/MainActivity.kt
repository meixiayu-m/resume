package com.resumeagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.resumeagent.app.ui.ResumeAgentApp
import com.resumeagent.app.ui.ResumeAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ResumeAgentTheme {
                ResumeAgentApp()
            }
        }
    }
}
