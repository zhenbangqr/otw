package com.zhenbang.otw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zhenbang.otw.ui.screen.HomeScreen
import com.zhenbang.otw.ui.screen.ScreenZPAPI
import com.zhenbang.otw.ui.theme.OnTheWayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnTheWayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    ScreenZPAPI(modifier = Modifier.padding(innerPadding)
                    HomeScreen(modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenPreview() {
    OnTheWayTheme {
        HomeScreen()
    }
}