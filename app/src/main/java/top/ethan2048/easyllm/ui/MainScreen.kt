package top.ethan2048.easyllm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.ethan2048.easyllm.core.model.Vendor
import top.ethan2048.easyllm.data.AppRepository
import top.ethan2048.easyllm.ui.screen.ApiConfigScreen
import top.ethan2048.easyllm.ui.screen.ChatScreen
import top.ethan2048.easyllm.ui.screen.ChatViewModel
import top.ethan2048.easyllm.ui.screen.McpConfigScreen
import top.ethan2048.easyllm.ui.screen.VendorDetailScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen {
    CHAT,
    API_CONFIG,
    MCP_CONFIG
}

@Composable
fun MainScreen(repository: AppRepository) {
    var showSidebar by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }
    var currentVendorId by remember { mutableStateOf<String?>(null) }
    var showModelSelector by remember { mutableStateOf(false) }
    var modelRefreshKey by remember { mutableStateOf(0) }
    var conversationRefreshKey by remember { mutableStateOf(0) }

    // 在 MainScreen 层创建 ChatViewModel，使其可被 Sidebar 回调控制
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(repository))

    // 响应式计算当前选中的模型名称
    val selectedModelName = remember(modelRefreshKey) {
        val vid = repository.activeVendorId
        val mid = repository.activeModelConfigId
        if (vid != null && mid != null) {
            val vendor = repository.vendors.find { it.id == vid }
            val modelConfig = vendor?.models?.find { it.id == mid }
            if (vendor != null && modelConfig != null) {
                "${vendor.name} - ${modelConfig.name}"
            } else null
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 主内容区域（带动画切换）
            Box(modifier = Modifier.weight(1f)) {
                when {
                    currentVendorId != null -> {
                        VendorDetailScreen(
                            vendorId = currentVendorId!!,
                            repository = repository,
                            onNavigateBack = { currentVendorId = null }
                        )
                    }
                    else -> {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                Screen.CHAT -> ChatScreen(
                                    repository = repository,
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = chatViewModel
                                )
                                Screen.API_CONFIG -> ApiConfigScreen(
                                    repository = repository,
                                    onNavigateToVendorDetail = { vendorId ->
                                        currentVendorId = vendorId
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Screen.MCP_CONFIG -> McpConfigScreen(
                                    repository = repository,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // 底栏 — 始终显示
            BottomStatusBar(
                selectedModelName = selectedModelName,
                onExpandSidebar = { showSidebar = true },
                onSelectModel = { showModelSelector = true }
            )
        }

        // 遮罩层
        if (showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                    .then(
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showSidebar = false
                        }
                    )
            )
        }

        // 侧边栏
        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
        ) {
            Sidebar(
                repository = repository,
                onClose = { showSidebar = false },
                onNavigateToScreen = { screen ->
                    showSidebar = false
                    currentVendorId = null
                    currentScreen = screen
                },
                onNewChat = {
                    chatViewModel.startNewConversation()
                    conversationRefreshKey++
                    showSidebar = false
                },
                onLoadConversation = { conversationId ->
                    chatViewModel.loadConversation(conversationId)
                    showSidebar = false
                },
                refreshKey = conversationRefreshKey
            )
        }

        // 模型选择弹窗
        if (showModelSelector) {
            ModelSelectorDialog(
                vendors = repository.vendors,
                activeVendorId = repository.activeVendorId,
                activeModelConfigId = repository.activeModelConfigId,
                onSelect = { vendorId, modelConfigId ->
                    repository.setActiveVendor(vendorId)
                    repository.setActiveModelConfig(modelConfigId)
                    showModelSelector = false
                    modelRefreshKey++
                },
                onDismiss = { showModelSelector = false }
            )
        }
    }
}

@Composable
private fun ModelSelectorDialog(
    vendors: List<Vendor>,
    activeVendorId: String?,
    activeModelConfigId: String?,
    onSelect: (vendorId: String, modelConfigId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            if (vendors.isEmpty()) {
                Text(
                    text = "暂无可用模型，请先在 API 设置中添加供应商和模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(vendors) { vendor ->
                        Text(
                            text = vendor.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (vendor.models.isEmpty()) {
                            Text(
                                text = "  暂无模型配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            vendor.models.forEach { modelConfig ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(vendor.id, modelConfig.id)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = vendor.id == activeVendorId && modelConfig.id == activeModelConfigId,
                                        onClick = {
                                            onSelect(vendor.id, modelConfig.id)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = modelConfig.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = modelConfig.model,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun BottomStatusBar(
    selectedModelName: String?,
    onExpandSidebar: () -> Unit,
    onSelectModel: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 模型选择器（可点击）
            Surface(
                onClick = onSelectModel,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectedModelName ?: "选择模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 三横杠汉堡菜单按钮
            IconButton(
                onClick = onExpandSidebar,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "展开菜单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun Sidebar(
    repository: AppRepository,
    onClose: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit,
    onNewChat: () -> Unit,
    onLoadConversation: (conversationId: String) -> Unit,
    refreshKey: Int
) {
    val conversations by remember(refreshKey) { mutableStateOf(repository.conversations) }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EasyLLM",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Menu, contentDescription = "关闭")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 对话记录
                Column(modifier = Modifier.weight(1f)) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "对话记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onNewChat) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("新建", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (conversations.isEmpty()) {
                        Text(
                            text = "暂无对话记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(conversations) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isActive = conversation.id == repository.activeConversationId,
                                    onClick = {
                                        onLoadConversation(conversation.id)
                                    },
                                    onDelete = {
                                        repository.deleteConversation(conversation.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // 导航链接
                Column {
                    HorizontalDivider()

                    Text(
                        text = "导航",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SidebarNavItem(
                        icon = Icons.Default.SmartToy,
                        title = "对话",
                        subtitle = "返回聊天界面",
                        onClick = {
                            onNavigateToScreen(Screen.CHAT)
                        }
                    )

                    SidebarNavItem(
                        icon = Icons.Default.Key,
                        title = "API 设置",
                        subtitle = "管理供应商和模型配置",
                        onClick = {
                            onNavigateToScreen(Screen.API_CONFIG)
                        }
                    )

                    SidebarNavItem(
                        icon = Icons.Default.Extension,
                        title = "MCP 设置",
                        subtitle = "配置模型上下文协议服务器",
                        onClick = {
                            onNavigateToScreen(Screen.MCP_CONFIG)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: top.ethan2048.easyllm.core.model.Conversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
