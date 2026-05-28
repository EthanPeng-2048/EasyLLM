package top.ethan2048.easyllm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.ethan2048.easyllm.data.AppRepository
import top.ethan2048.easyllm.ui.screen.ApiConfigScreen
import top.ethan2048.easyllm.ui.screen.ChatScreen
import top.ethan2048.easyllm.ui.screen.McpConfigScreen
import top.ethan2048.easyllm.ui.screen.VendorDetailScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SidebarContent {
    NONE,
    MCP,
    API
}

@Composable
fun MainScreen(repository: AppRepository) {
    var showSidebar by remember { mutableStateOf(false) }
    var sidebarContent by remember { mutableStateOf(SidebarContent.NONE) }
    var currentVendorId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentVendorId != null) {
            VendorDetailScreen(
                vendorId = currentVendorId!!,
                repository = repository,
                onNavigateBack = { currentVendorId = null }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ChatScreen(
                    repository = repository,
                    modifier = Modifier.weight(1f)
                )

                BottomStatusBar(
                    repository = repository,
                    onExpandSidebar = {
                        showSidebar = true
                        sidebarContent = SidebarContent.NONE
                    }
                )
            }
        }

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
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Sidebar(
                repository = repository,
                sidebarContent = sidebarContent,
                onContentChange = { sidebarContent = it },
                onClose = { showSidebar = false },
                onNavigateToVendorDetail = { vendorId ->
                    showSidebar = false
                    currentVendorId = vendorId
                }
            )
        }
    }
}

@Composable
private fun BottomStatusBar(
    repository: AppRepository,
    onExpandSidebar: () -> Unit
) {
    val vendors by remember { mutableStateOf(repository.vendors) }

    val activeVendorId = repository.activeVendorId
    val activeModelConfigId = repository.activeModelConfigId

    val selectedModelName = remember(activeVendorId, activeModelConfigId, vendors) {
        if (activeVendorId != null && activeModelConfigId != null) {
            val vendor = vendors.find { it.id == activeVendorId }
            val modelConfig = vendor?.models?.find { it.id == activeModelConfigId }
            if (vendor != null && modelConfig != null) {
                "${vendor.name} - ${modelConfig.name}"
            } else null
        } else null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
            Surface(
                onClick = { },
                shape = RoundedCornerShape(8.dp),
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

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.DataArray,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Context: 0 tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                onClick = onExpandSidebar,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "展开菜单",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    repository: AppRepository,
    sidebarContent: SidebarContent,
    onContentChange: (SidebarContent) -> Unit,
    onClose: () -> Unit,
    onNavigateToVendorDetail: (String) -> Unit
) {
    val conversations by remember { mutableStateOf(repository.conversations) }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "菜单",
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
                Column(modifier = Modifier.weight(1f)) {
                    HorizontalDivider()
                    Text(
                        text = "对话记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    if (conversations.isEmpty()) {
                        Text(
                            text = "暂无对话记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(conversations) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isActive = conversation.id == repository.activeConversationId,
                                    onClick = {
                                        repository.setActiveConversation(conversation.id)
                                        onClose()
                                    },
                                    onDelete = {
                                        repository.deleteConversation(conversation.id)
                                    }
                                )
                            }
                        }
                    }
                }

                Column {
                    HorizontalDivider()

                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SidebarItem(
                        icon = Icons.Default.Extension,
                        title = "MCP 设置",
                        subtitle = "配置模型上下文协议",
                        isExpanded = sidebarContent == SidebarContent.MCP,
                        onClick = {
                            onContentChange(
                                if (sidebarContent == SidebarContent.MCP)
                                    SidebarContent.NONE
                                else
                                    SidebarContent.MCP
                            )
                        }
                    )

                    if (sidebarContent == SidebarContent.MCP) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            McpConfigScreen(
                                repository = repository,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    }

                    SidebarItem(
                        icon = Icons.Default.Key,
                        title = "API 设置",
                        subtitle = "管理供应商和模型",
                        isExpanded = sidebarContent == SidebarContent.API,
                        onClick = {
                            onContentChange(
                                if (sidebarContent == SidebarContent.API)
                                    SidebarContent.NONE
                                else
                                    SidebarContent.API
                            )
                        }
                    )

                    if (sidebarContent == SidebarContent.API) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            ApiConfigScreen(
                                repository = repository,
                                onNavigateToVendorDetail = onNavigateToVendorDetail,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    }
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
private fun SidebarItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isExpanded)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface
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
                tint = if (isExpanded)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
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
