package io.github.anthodingo.onyxfilter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.anthodingo.onyxfilter.ui.OnyxFilterRoot
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as OnyxFilterApplication).repository
        setContent {
            OnyxFilterTheme {
                OnyxFilterRoot(repository)
            }
        }
    }
}
