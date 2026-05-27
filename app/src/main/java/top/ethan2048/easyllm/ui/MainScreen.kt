package top.ethan2048.easyllm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.ethan2048.easyllm.data.AppRepository
import top.ethan2048.easyllm.ui.screen.ApiConfigScreen
import top.ethan2048.easyllm.ui.screen.ChatScreen
import top.ethan2048.easyllm.ui.screen.McpConfigScreen

private data class NavItem(
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem("聊天", Icons.AutoMirrored.Filled.Chat),
    NavItem("API", Icons.Default.Settings),
    NavItem("MCP", Icons.Default.Dns)
)

@Composable
fun MainScreen(repository: AppRepository) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> ChatScreen(
                repository = repository,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> ApiConfigScreen(
                repository = repository,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> McpConfigScreen(
                repository = repository,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
