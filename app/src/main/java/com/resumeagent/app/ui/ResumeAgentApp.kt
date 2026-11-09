package com.resumeagent.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resumeagent.app.domain.AiProviderId
import com.resumeagent.app.domain.AiProviderSettings
import com.resumeagent.app.domain.AiSettings
import com.resumeagent.app.domain.AiModelInfo
import com.resumeagent.app.domain.ClientAddressStyle
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ProjectInput
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.SeniorityLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ROUTE_HOME = "home"
private const val ROUTE_CREATE = "create"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun ResumeAgentApp(mainViewModel: MainViewModel = viewModel()) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()
    val saveableStateHolder = rememberSaveableStateHolder()
    var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    var settingsReturnRoute by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    val snackbar = remember { SnackbarHostState() }
    fun openSettings() {
        settingsReturnRoute = route
        route = ROUTE_SETTINGS
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            mainViewModel.clearError()
        }
    }
    LaunchedEffect(state.selectedProject?.id, route) {
        if (route == ROUTE_CREATE && state.selectedProject != null) {
            route = ROUTE_DETAIL
        }
    }

    BackHandler(enabled = route != ROUTE_HOME) {
        route = if (route == ROUTE_SETTINGS) settingsReturnRoute else ROUTE_HOME
    }

    Box(Modifier.fillMaxSize()) {
        saveableStateHolder.SaveableStateProvider(route) {
            when (route) {
                ROUTE_CREATE -> NewProjectScreen(
                    hasApiKey = mainViewModel.hasApiKey(),
                    onBack = { route = ROUTE_HOME },
                    onOpenSettings = ::openSettings,
                    onCreate = mainViewModel::createProject
                )
                ROUTE_DETAIL -> DetailScreen(
                    viewModel = mainViewModel,
                    onBack = { route = ROUTE_HOME },
                    onOpenSettings = ::openSettings
                )
                ROUTE_SETTINGS -> SettingsScreen(
                    viewModel = mainViewModel,
                    onBack = { route = settingsReturnRoute }
                )
                else -> HomeScreen(
                    state = state,
                    onOpenProject = {
                        mainViewModel.selectProject(it)
                        route = ROUTE_DETAIL
                    },
                    onNewProject = {
                        saveableStateHolder.removeState(ROUTE_CREATE)
                        mainViewModel.selectProject(null)
                        route = ROUTE_CREATE
                    },
                    onOpenSettings = ::openSettings,
                    onDeleteProject = mainViewModel::deleteProjectById
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        )

        state.busyMessage?.let { message ->
            BusyOverlay(message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: MainUiState,
    onOpenProject: (ResumeProject) -> Unit,
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var deleteCandidate by remember { mutableStateOf<ResumeProject?>(null) }
    var finalDeleteCandidate by remember { mutableStateOf<ResumeProject?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("简历 Agent", fontWeight = FontWeight.Bold)
                        Text(
                            "只在本机处理你的接单任务",
                            style = MaterialTheme.typography.labelSmall,
                            color = Moss
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProject,
                containerColor = Moss,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建任务", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AgentHero(
                    projectCount = state.projects.size,
                    readyCount = state.projects.count { it.stage == ProjectStage.READY }
                )
            }
            if (state.projects.isEmpty() && state.initialized) {
                item {
                    EmptyProjects()
                }
            } else {
                item {
                    Text(
                        "接单任务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project) },
                        onDelete = { deleteCandidate = project }
                    )
                }
            }
        }
    }
    deleteCandidate?.let { project ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除这份简历任务") },
            text = { Text("删除会释放本机空间，同时移除原简历、诊断、版本历史和导出文件。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    finalDeleteCandidate = project
                }) {
                    Text("继续", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
            }
        )
    }
    finalDeleteCandidate?.let { project ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { finalDeleteCandidate = null },
            title = { Text("再次确认永久删除") },
            text = {
                Text(
                    "将删除“${project.title.ifBlank { project.targetRole.ifBlank { "通用简历优化" } }}”，包括 ${project.versions.size} 个历史版本和全部导出文件。此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finalDeleteCandidate = null
                    onDeleteProject(project.id)
                }) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { finalDeleteCandidate = null }) { Text("保留") }
            }
        )
    }
}

@Composable
private fun AgentHero(projectCount: Int, readyCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF173E36), Color(0xFF276D5F), Color(0xFF3B8774))
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("事实优先的简历工作台", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("特定岗位、岗位方向或通用优化", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                    }
                }
                Text(
                    "先提取事实，再按目标诊断和改写。没有 JD 时也可以只按岗位方向或通用质量优化。",
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 21.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    HeroMetric("$projectCount", "接单任务")
                    HeroMetric("$readyCount", "已生成交付稿")
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(project: ResumeProject, onClick: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(containerColor = WarmWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFE8F1ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Moss)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        project.targetRole.ifBlank { project.title },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(project.clientName, project.company, project.industry)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ").ifBlank { "未填写公司/行业" },
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(stageColor(project.stage).copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        project.stage.label(),
                        color = stageColor(project.stage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "删除任务",
                        tint = Muted
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project.analysis?.let {
                        if (project.optimizationMode == OptimizationMode.GENERAL) {
                            "质量评分 ${it.overallScore}"
                        } else {
                            "适配评分 ${it.overallScore}"
                        }
                    } ?: "等待诊断",
                    color = if (project.analysis == null) Muted else Moss,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                if (project.dueDate.isNotBlank()) {
                    Text("交付 ${project.dueDate}", color = Amber, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(project.updatedAt)),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyProjects() {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(22.dp))
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("还没有简历任务", fontWeight = FontWeight.Bold)
            Text(
                "点“新建任务”，可选择特定岗位、只有岗位方向或完全通用优化。",
                color = Muted,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectScreen(
    hasApiKey: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreate: (ProjectInput, Uri?, Uri?) -> Unit
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var targetRole by rememberSaveable { mutableStateOf("") }
    var industry by rememberSaveable { mutableStateOf("") }
    var clientName by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var orderNote by rememberSaveable { mutableStateOf("") }
    var clientAddressStyleName by rememberSaveable {
        mutableStateOf(ClientAddressStyle.AUTO.name)
    }
    var modeName by rememberSaveable { mutableStateOf(OptimizationMode.TARGETED.name) }
    var seniorityName by rememberSaveable { mutableStateOf(SeniorityLevel.AUTO.name) }
    var resumeText by rememberSaveable { mutableStateOf("") }
    var jobDescription by rememberSaveable { mutableStateOf("") }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var selectedJdUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedJdName by rememberSaveable { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        selectedName = uri?.let { queryDisplayName(context, it) }.orEmpty()
    }
    val jdFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedJdUri = uri
        selectedJdName = uri?.let { queryDisplayName(context, it) }.orEmpty()
    }
    val mode = OptimizationMode.valueOf(modeName)

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Text("新建简历任务", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                if (!hasApiKey) {
                    SetupWarning(onOpenSettings)
                }
            }
            item {
                FormSection("优化方式") {
                    OptimizationModeSelector(mode) { modeName = it.name }
                    Text(
                        when (mode) {
                            OptimizationMode.TARGETED -> "有具体岗位和 JD，会分析岗位匹配、关键词差距和定向改写。"
                            OptimizationMode.ROLE_ONLY -> "知道想投的岗位方向，但没有具体 JD。只分析岗位方向相关性，不猜测招聘要求。"
                            OptimizationMode.GENERAL -> "没有明确目标岗位或 JD。只做通用质量、事实、结构和表达优化。"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            item {
                FormSection("岗位信息") {
                    if (mode != OptimizationMode.GENERAL) {
                        ResumeTextField(
                            "目标岗位 *",
                            targetRole,
                            { targetRole = it },
                            "例如：高级产品经理"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            ResumeTextField("目标公司", company, { company = it }, "可空")
                        }
                        Box(Modifier.weight(1f)) {
                            ResumeTextField("行业", industry, { industry = it }, "例如：SaaS")
                        }
                    }
                    SenioritySelector(
                        selected = SeniorityLevel.valueOf(seniorityName),
                        onSelect = { seniorityName = it.name }
                    )
                    ResumeTextField("任务名称", title, { title = it }, "不填则使用目标岗位")
                }
            }
            item {
                FormSection("接单信息") {
                    ResumeTextField("客户姓名或称呼", clientName, { clientName = it }, "例如：李明、李同学")
                    ClientAddressSelector(
                        selected = ClientAddressStyle.valueOf(clientAddressStyleName),
                        onSelect = { clientAddressStyleName = it.name }
                    )
                    ResumeTextField("交付日期", dueDate, { dueDate = it }, "例如：2026-11-10")
                    ResumeTextField(
                        "订单备注",
                        orderNote,
                        { orderNote = it },
                        "例如：重点突出项目管理经验",
                        minLines = 2
                    )
                }
            }
            item {
                FormSection("原简历") {
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/pdf",
                                    "text/plain",
                                    "image/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 13.dp)
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedName.isBlank()) "选择 DOCX / PDF / 图片" else selectedName)
                    }
                    if (selectedName.isNotBlank()) {
                        Text(
                            "已选择文件，创建时优先使用文件提取内容。",
                            color = Moss,
                            fontSize = 12.sp
                        )
                        TextButton(onClick = {
                            selectedUri = null
                            selectedName = ""
                        }) {
                            Text("取消选择")
                        }
                    }
                    Text(
                        "也可以直接粘贴文字。扫描 PDF 和图片会调用视觉模型 OCR。",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    ResumeTextField(
                        "粘贴简历文字${if (selectedUri == null) " *" else "（可选）"}",
                        resumeText,
                        { resumeText = it },
                        "复制原简历内容到这里…",
                        minLines = 8
                    )
                }
            }
            if (mode == OptimizationMode.TARGETED) {
                item {
                    FormSection("岗位 JD") {
                        OutlinedButton(
                            onClick = {
                                jdFilePicker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/pdf",
                                        "text/plain",
                                        "image/*"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (selectedJdName.isBlank()) "从文件或招聘截图导入 JD" else selectedJdName)
                        }
                        ResumeTextField(
                            "粘贴招聘要求 *",
                            jobDescription,
                            { jobDescription = it },
                            "建议包含岗位职责、任职要求、加分项…",
                            minLines = 9
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onCreate(
                            ProjectInput(
                                title = title,
                                clientName = clientName,
                                dueDate = dueDate,
                                orderNote = orderNote,
                                clientAddressStyle = ClientAddressStyle.valueOf(clientAddressStyleName),
                                targetRole = targetRole,
                                company = company,
                                industry = industry,
                                optimizationMode = mode,
                                seniorityLevel = SeniorityLevel.valueOf(seniorityName),
                                resumeText = resumeText,
                                jobDescription = jobDescription
                            ),
                            selectedUri,
                            selectedJdUri
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("创建任务", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val initial = remember { viewModel.readSettings() }
    var providers by remember { mutableStateOf(initial.providers) }
    var activeText by rememberSaveable { mutableStateOf(initial.activeTextProvider.name) }
    var activeVision by rememberSaveable { mutableStateOf(initial.activeVisionProvider.name) }
    var activeReview by rememberSaveable { mutableStateOf(initial.activeReviewProvider.name) }
    var dualReviewEnabled by rememberSaveable { mutableStateOf(initial.dualReviewEnabled) }
    var expandedProvider by rememberSaveable { mutableStateOf(initial.activeTextProvider.name) }
    var keys by remember { mutableStateOf<Map<AiProviderId, String>>(emptyMap()) }
    var configuredProviders by remember {
        mutableStateOf(
            initial.providers.filter { viewModel.hasApiKey(it.id) }
                .map { it.id }
                .toSet()
        )
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Text("AI 设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FormSection("多模型配置") {
                    Text(
                        "可以同时保存智谱、Kimi、DeepSeek、千问和自定义 OpenAI 兼容接口的 Key。分别选择当前文本分析与 OCR 服务。",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                    val selectedText = providers.firstOrNull { it.id.name == activeText }
                    val selectedVision = providers.firstOrNull { it.id.name == activeVision }
                    Surface(color = Color(0xFFE8F1ED), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前文本 AI：${selectedText?.label ?: "未选择"}", fontSize = 13.sp)
                            Text("当前 OCR AI：${selectedVision?.label ?: "未选择"}", fontSize = 13.sp)
                        }
                    }
                }
            }
            items(providers, key = { it.id.name }) { provider ->
                ProviderSettingsCard(
                    provider = provider,
                    expanded = expandedProvider == provider.id.name,
                    configured = provider.id in configuredProviders,
                    keyDraft = keys[provider.id].orEmpty(),
                    isTextProvider = provider.id.name == activeText,
                    isVisionProvider = provider.id.name == activeVision,
                    models = appState.modelsByProvider[provider.id].orEmpty(),
                    loadingModels = appState.modelLoadingProvider == provider.id,
                    onExpand = {
                        expandedProvider = if (expandedProvider == provider.id.name) {
                            ""
                        } else {
                            provider.id.name
                        }
                    },
                    onKeyChange = { keys = keys + (provider.id to it) },
                    onProviderChange = { changed ->
                        providers = providers.map { if (it.id == changed.id) changed else it }
                    },
                    onSelectText = { activeText = provider.id.name },
                    onSelectVision = { activeVision = provider.id.name },
                    onRefreshModels = {
                        viewModel.fetchModels(provider, keys[provider.id].orEmpty())
                    },
                    onClearKey = {
                        viewModel.clearApiKey(provider.id)
                        configuredProviders = configuredProviders - provider.id
                        keys = keys - provider.id
                    }
                )
            }
            item {
                FormSection("双模型审核") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("开启第二模型事实审核", fontWeight = FontWeight.Medium)
                            Text(
                                "默认关闭。开启后，主模型改写，审核模型只提出问题，不自动修改正文。",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Switch(
                            checked = dualReviewEnabled,
                            onCheckedChange = { dualReviewEnabled = it }
                        )
                    }
                    if (dualReviewEnabled) {
                        Text("选择审核 AI", color = Muted, fontSize = 12.sp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            providers.forEach { provider ->
                                FilterChip(
                                    selected = activeReview == provider.id.name,
                                    onClick = { activeReview = provider.id.name },
                                    label = { Text(provider.label) }
                                )
                            }
                        }
                        if (activeReview == activeText) {
                            Text(
                                "审核 AI 与主改写 AI 相同。可以运行，但事实交叉检查价值会降低。",
                                color = Coral,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.saveSettings(
                            AiSettings(
                                providers = providers,
                                activeTextProvider = AiProviderId.valueOf(activeText),
                                activeVisionProvider = AiProviderId.valueOf(activeVision),
                                dualReviewEnabled = dualReviewEnabled,
                                activeReviewProvider = AiProviderId.valueOf(activeReview)
                            ),
                            keys
                        )
                        configuredProviders = configuredProviders + keys.filterValues { it.isNotBlank() }.keys
                        keys = emptyMap()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("保存全部模型设置", fontWeight = FontWeight.Bold)
                }
            }
            item {
                FormSection("事实保真规则") {
                    Text(
                        "改写只允许调整语序、删除空话和正式化表达。禁止编造数字、职级、团队规模、项目归属、成果，禁止升级职责边界，禁止添加无依据的 JD 关键词。",
                        color = Ink,
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                    Text(
                        "发现新增数字、职责升级或无依据关键词时，App 会自动取消该段采用，并加入核实清单。",
                        color = Coral,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                }
            }
            item {
                FormSection("本机数据") {
                    Text(
                        "简历任务保存在 App 私有目录，系统备份已关闭。导出的文件通过手机系统分享面板发送。",
                        color = Muted,
                        lineHeight = 20.sp
                    )
                    HorizontalDivider(color = Line)
                    Text(
                        "删除任务会同时删除该任务的原始文件。API Key 不会写入任何导出文件。",
                        color = Ink,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(
    provider: AiProviderSettings,
    expanded: Boolean,
    configured: Boolean,
    keyDraft: String,
    isTextProvider: Boolean,
    isVisionProvider: Boolean,
    models: List<AiModelInfo>,
    loadingModels: Boolean,
    onExpand: () -> Unit,
    onKeyChange: (String) -> Unit,
    onProviderChange: (AiProviderSettings) -> Unit,
    onSelectText: () -> Unit,
    onSelectVision: () -> Unit,
    onRefreshModels: () -> Unit,
    onClearKey: () -> Unit
) {
    var showModels by remember { mutableStateOf(false) }
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = WarmWhite),
        border = androidx.compose.foundation.BorderStroke(
            if (isTextProvider || isVisionProvider) 1.5.dp else 1.dp,
            if (isTextProvider || isVisionProvider) Moss else Line
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (configured) Moss else Line)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.label, fontWeight = FontWeight.Bold)
                    Text(
                        if (configured) "Key 已保存在本机" else "未配置 Key",
                        color = if (configured) Moss else Muted,
                        fontSize = 11.sp
                    )
                }
                if (isTextProvider) ProviderBadge("文本 AI")
                if (isVisionProvider) {
                    Spacer(Modifier.width(5.dp))
                    ProviderBadge("OCR")
                }
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "收起" else "配置", color = Moss, fontSize = 12.sp)
            }
            if (expanded) {
                HorizontalDivider(color = Line)
                ResumeTextField(
                    "${provider.label} API Key",
                    keyDraft,
                    onKeyChange,
                    if (configured) "已保存；输入新 Key 可覆盖" else "输入 API Key",
                    visualTransformation = PasswordVisualTransformation()
                )
                ResumeTextField(
                    "接口地址",
                    provider.endpoint,
                    { onProviderChange(provider.copy(endpoint = it)) },
                    "https://…"
                )
                ResumeTextField(
                    "模型列表地址",
                    provider.modelsEndpoint,
                    { onProviderChange(provider.copy(modelsEndpoint = it)) },
                    "通常为接口地址中的 /chat/completions 换成 /models"
                )
                ResumeTextField(
                    "文本模型",
                    provider.textModel,
                    { onProviderChange(provider.copy(textModel = it)) },
                    "填写账号可用的完整模型 ID"
                )
                ResumeTextField(
                    "视觉 OCR 模型",
                    provider.visionModel,
                    { onProviderChange(provider.copy(visionModel = it)) },
                    "留空表示此服务不用于 OCR"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onRefreshModels,
                        enabled = !loadingModels,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (loadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (loadingModels) "正在获取模型" else "通过 API 获取模型")
                    }
                    if (models.isNotEmpty()) {
                        TextButton(onClick = { showModels = !showModels }) {
                            Text(if (showModels) "收起列表" else "选择模型")
                        }
                    }
                }
                if (showModels && models.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFF0F3EF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "接口返回 ${models.size} 个模型。列表通常不区分文本和视觉能力，请按模型 ID 自行指定用途。",
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                            models.take(50).forEach { model ->
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        model.id,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    model.contextLength?.let { length ->
                                        Text("上下文 $length", color = Muted, fontSize = 10.sp)
                                    }
                                    Row {
                                        TextButton(
                                            onClick = {
                                                onProviderChange(provider.copy(textModel = model.id))
                                            }
                                        ) { Text("设为文本") }
                                        TextButton(
                                            onClick = {
                                                onProviderChange(provider.copy(visionModel = model.id))
                                            }
                                        ) { Text("设为 OCR") }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSelectText,
                        modifier = Modifier.weight(1f),
                        enabled = !isTextProvider && provider.textModel.isNotBlank()
                    ) {
                        Text(
                            if (isTextProvider) "当前文本 AI" else if (provider.textModel.isBlank()) {
                                "先填文本模型"
                            } else {
                                "设为文本 AI"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = onSelectVision,
                        modifier = Modifier.weight(1f),
                        enabled = !isVisionProvider && provider.visionModel.isNotBlank()
                    ) {
                        Text(
                            if (isVisionProvider) "当前 OCR AI" else if (provider.visionModel.isBlank()) {
                                "无视觉模型"
                            } else {
                                "设为 OCR AI"
                            }
                        )
                    }
                }
                if (configured) {
                    TextButton(onClick = onClearKey) {
                        Text("清除 ${provider.label} 的 Key", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderBadge(label: String) {
    Surface(
        color = Color(0xFFDCEBE5),
        shape = CircleShape
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MossDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SetupWarning(onOpenSettings: () -> Unit) {
    Surface(
        color = Color(0xFFFFF1D8),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "还没有配置 AI 接口。可以先创建文字简历任务，分析和 OCR 前需要设置 Key。",
                modifier = Modifier.weight(1f),
                color = Color(0xFF77551B),
                fontSize = 13.sp
            )
            TextButton(onClick = onOpenSettings) { Text("去设置") }
        }
    }
}

@Composable
internal fun FormSection(title: String, content: @Composable () -> Unit) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = WarmWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
internal fun OptimizationModeSelector(
    selected: OptimizationMode,
    onSelect: (OptimizationMode) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == OptimizationMode.TARGETED,
            onClick = { onSelect(OptimizationMode.TARGETED) },
            label = { Text("特定岗位") }
        )
        FilterChip(
            selected = selected == OptimizationMode.ROLE_ONLY,
            onClick = { onSelect(OptimizationMode.ROLE_ONLY) },
            label = { Text("只有岗位方向") }
        )
        FilterChip(
            selected = selected == OptimizationMode.GENERAL,
            onClick = { onSelect(OptimizationMode.GENERAL) },
            label = { Text("通用优化") }
        )
    }
}

@Composable
internal fun SenioritySelector(selected: SeniorityLevel, onSelect: (SeniorityLevel) -> Unit) {
    Text("目标职级", color = Muted, fontSize = 12.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SeniorityLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                label = {
                    Text(
                        when (level) {
                            SeniorityLevel.AUTO -> "自动"
                            SeniorityLevel.ENTRY -> "应届"
                            SeniorityLevel.JUNIOR -> "初级"
                            SeniorityLevel.MID -> "中级"
                            SeniorityLevel.SENIOR -> "资深"
                            SeniorityLevel.LEAD -> "负责人"
                            SeniorityLevel.MANAGER -> "管理"
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun ClientAddressSelector(
    selected: ClientAddressStyle,
    onSelect: (ClientAddressStyle) -> Unit
) {
    Text("客户沟通称呼", color = Muted, fontSize = 12.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        ClientAddressStyle.entries.forEach { style ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelect(style) },
                label = {
                    Text(
                        when (style) {
                            ClientAddressStyle.AUTO -> "自动"
                            ClientAddressStyle.STUDENT -> "同学"
                            ClientAddressStyle.INFORMAL -> "直接称呼"
                            ClientAddressStyle.FORMAL -> "正式"
                        }
                    )
                }
            )
        }
    }
    Text(
        "自动模式按目标职级选择称呼，也可在这个任务里固定设置。",
        color = Muted,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
}

@Composable
private fun ResumeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted.copy(alpha = 0.75f)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = if (minLines > 1) 20 else 1,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun BusyOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x990D1815))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmWhite),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = Moss, strokeWidth = 3.dp)
                Text(message, fontWeight = FontWeight.SemiBold)
                Text(
                    "处理期间可以切换到其他 App。通知栏会显示任务状态，完成后回来查看即可。",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else uri.lastPathSegment
    } ?: "已选择文件"

private fun ProjectStage.label(): String = when (this) {
    ProjectStage.IMPORTED -> "待诊断"
    ProjectStage.ANALYZED -> "已诊断"
    ProjectStage.DRAFTED -> "待审核"
    ProjectStage.READY -> "已导出"
}

private fun stageColor(stage: ProjectStage): Color = when (stage) {
    ProjectStage.IMPORTED -> Muted
    ProjectStage.ANALYZED -> Amber
    ProjectStage.DRAFTED -> Coral
    ProjectStage.READY -> Moss
}
