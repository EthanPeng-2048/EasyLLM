package top.ethan2048.easyllm.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.ethan2048.easyllm.core.model.ModelConfig
import top.ethan2048.easyllm.data.AppRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    vendorId: String,
    repository: AppRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VendorDetailViewModel = viewModel(
        factory = VendorDetailViewModel.Factory(vendorId, repository)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val vendor = uiState.vendor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vendor?.name ?: "供应商详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showVendorEditDialog() }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑供应商")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showModelConfigDialog() },
                modifier = modifier.padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加模型")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 供应商信息卡片
            vendor?.let { v ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Endpoint",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = v.endpoint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "模型数量: ${v.models.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "模型配置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (v.models.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "还没有配置模型",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右下角 + 添加新模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(v.models, key = { it.id }) { modelConfig ->
                            ModelConfigCard(
                                modelConfig = modelConfig,
                                isActive = repository.activeModelConfigId == modelConfig.id,
                                onSelect = { viewModel.setActiveModelConfig(modelConfig.id) },
                                onEdit = { viewModel.showModelConfigDialog(modelConfig) },
                                onDelete = { viewModel.deleteModelConfig(modelConfig.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 供应商编辑对话框
    if (uiState.showVendorEditDialog && vendor != null) {
        VendorEditDialog(
            vendor = vendor,
            onDismiss = { viewModel.dismissVendorEditDialog() },
            onSave = { name, endpoint, apiKey ->
                viewModel.saveVendor(name, endpoint, apiKey)
            }
        )
    }

    // 模型配置对话框
    if (uiState.showModelConfigDialog && vendor != null) {
        ModelConfigDialog(
            modelConfig = uiState.editingModelConfig,
            availableModels = uiState.availableModels,
            isLoadingModels = uiState.isLoadingModels,
            modelLoadError = uiState.modelLoadError,
            vendorEndpoint = vendor.endpoint,
            vendorApiKey = vendor.apiKey,
            onLoadModels = {
                viewModel.loadAvailableModels(vendor.endpoint, vendor.apiKey)
            },
            onDismiss = { viewModel.dismissModelConfigDialog() },
            onSave = { name, model, temperature, topK, topP, maxTokens, contextLength ->
                viewModel.saveModelConfig(name, model, temperature, topK, topP, maxTokens, contextLength)
            }
        )
    }
}

@Composable
private fun ModelConfigCard(
    modelConfig: ModelConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = onSelect
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelConfig.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = modelConfig.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "温度: ${modelConfig.temperature} | TopK: ${modelConfig.topK} | TopP: ${modelConfig.topP} | Max: ${modelConfig.maxTokens}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun VendorEditDialog(
    vendor: top.ethan2048.easyllm.core.model.Vendor,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(vendor.name) }
    var endpoint by remember { mutableStateOf(vendor.endpoint) }
    var apiKey by remember { mutableStateOf(vendor.apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, endpoint, apiKey) },
                enabled = name.isNotBlank() && endpoint.isNotBlank() && apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModelConfigDialog(
    modelConfig: ModelConfig?,
    availableModels: List<top.ethan2048.easyllm.core.model.Model>,
    isLoadingModels: Boolean,
    modelLoadError: String?,
    vendorEndpoint: String,
    vendorApiKey: String,
    onLoadModels: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Float, Int, Float, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(modelConfig?.name ?: "") }
    var model by remember { mutableStateOf(modelConfig?.model ?: "") }
    var temperature by remember { mutableFloatStateOf(modelConfig?.temperature ?: 0.7f) }
    var topK by remember { mutableIntStateOf(modelConfig?.topK ?: 40) }
    var topP by remember { mutableFloatStateOf(modelConfig?.topP ?: 1.0f) }
    var maxTokens by remember { mutableStateOf(modelConfig?.maxTokens?.toString() ?: "4096") }
    var contextLength by remember { mutableStateOf(modelConfig?.contextLength?.toString() ?: "128000") }
    var showModelDropdown by remember { mutableStateOf(false) }

    val isEditing = modelConfig != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑模型配置" else "添加模型配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("例如: GPT-4 快速模式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 模型选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("gpt-4o") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showModelDropdown = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "选择模型")
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = showModelDropdown && availableModels.isNotEmpty(),
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        availableModels.forEach { modelItem ->
                            DropdownMenuItem(
                                text = { Text(modelItem.id) },
                                onClick = {
                                    model = modelItem.id
                                    showModelDropdown = false
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (!isLoadingModels && vendorEndpoint.isNotBlank() && vendorApiKey.isNotBlank()) {
                                onLoadModels()
                            }
                        },
                        enabled = !isLoadingModels && vendorEndpoint.isNotBlank() && vendorApiKey.isNotBlank()
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "加载模型")
                        }
                    }
                }

                modelLoadError?.let { error ->
                    Text(
                        text = "加载失败: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (availableModels.isNotEmpty()) {
                    TextButton(onClick = { showModelDropdown = true }) {
                        Text("从可用模型中选择 (${availableModels.size} 个)")
                    }
                }

                // Temperature
                Text("Temperature: ${String.format("%.2f", temperature)}")
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    steps = 19
                )

                // TopK
                Text("TopK: $topK")
                Slider(
                    value = topK.toFloat(),
                    onValueChange = { topK = it.toInt() },
                    valueRange = 1f..100f,
                    steps = 98
                )

                // TopP
                Text("TopP: ${String.format("%.2f", topP)}")
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0f..1f,
                    steps = 99
                )

                // Max Tokens
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = { Text("Max Tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Context Length
                OutlinedTextField(
                    value = contextLength,
                    onValueChange = { contextLength = it },
                    label = { Text("上下文长度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        model,
                        temperature,
                        topK,
                        topP,
                        maxTokens.toIntOrNull() ?: 4096,
                        contextLength.toIntOrNull() ?: 128000
                    )
                },
                enabled = name.isNotBlank() && model.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
