package top.ethan2048.easyllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.ethan2048.easyllm.data.AppRepository
import top.ethan2048.easyllm.ui.MainScreen
import top.ethan2048.easyllm.ui.theme.EasyLLMTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(applicationContext)
        enableEdgeToEdge()
        setContent {
            EasyLLMTheme {
                MainScreen(repository = repository)
            }
        }
    }
}