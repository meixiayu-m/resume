package com.resumeagent.app.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.DiffKind
import com.resumeagent.app.domain.IssueSeverity
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ProjectInput
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeIssue
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.SeniorityLevel
import com.resumeagent.app.domain.TextDiffEngine
import com.resumeagent.app.export.ExportBundle
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_SOURCE = "source"
private const val TAB_ANALYSIS = "analysis"
private const val TAB_REWRITE = "rewrite"
private const val TAB_PREVIEW = "preview"
private const val TAB_LAYOUT = "layout"
private const val TAB_VERSIONS = "versions"
private const val TAB_EXPORT = "export"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val project = state.selectedProject
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("任务不存在", color = Muted)
        }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFinalDeleteDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable(project.id) {
        mutableStateOf(if (project.analysis == null) TAB_SOURCE else TAB_REWRITE)
    }
    LaunchedEffect(project.stage) {
        when (project.stage) {
            ProjectStage.ANALYZED -> tab = TAB_ANALYSIS
            ProjectStage.DRAFTED, ProjectStage.READY -> tab = TAB_REWRITE
            ProjectStage.IMPORTED -> tab = TAB_SOURCE
        }
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            project.targetRole.ifBlank { project.title.ifBlank { "简历任务" } },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            project.company.ifBlank { project.title },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除任务")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ProjectProgress(project)
            WorkTabs(
                selected = tab,
                hasAnalysis = project.analysis != null,
                hasDraft = project.draftSections.isNotEmpty(),
                onSelect = { tab = it }
            )
            when (tab) {
                TAB_ANALYSIS -> AnalysisTab(
                    project = project,
                    viewModel = viewModel,
                    onGoRewrite = { tab = TAB_REWRITE }
                )
                TAB_REWRITE -> RewriteTab(
                    project = project,
                    viewModel = viewModel,
                    onExport = { tab = TAB_EXPORT }
                )
                TAB_PREVIEW -> FullPreviewTab(project = project)
                TAB_LAYOUT -> OriginalLayoutTab(
                    project = project,
                    viewModel = viewModel,
                    locked = project.activeVersionId != null &&
                        project.activeVersionId == project.finalVersionId
                )
                TAB_VERSIONS -> VersionsTab(
                    project = project,
                    viewModel = viewModel,
                    onContinue = { versionId ->
                        viewModel.activateVersion(versionId)
                        tab = TAB_REWRITE
                    }
                )
                TAB_EXPORT -> ExportTab(
                    project = project,
                    export = state.lastExport,
                    onExport = viewModel::exportProject,
                    onPrepareLayout = viewModel::prepareOriginalLayoutRevision
                )
                else -> SourceTab(project = project, viewModel = viewModel)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("准备删除这个简历任务") },
            text = {
                Text("删除后可以释放本机空间，但该任务的原简历、诊断、版本历史和导出文件都会消失。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    showFinalDeleteDialog = true
                }) {
                    Text("继续", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
    if (showFinalDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showFinalDeleteDialog = false },
            title = { Text("再次确认永久删除") },
            text = {
                Text(
                    "将永久删除该任务的 ${project.versions.size} 个历史版本、原简历副本、JD 文件和已经导出的文件。此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinalDeleteDialog = false
                    viewModel.deleteProject()
                    onBack()
                }) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalDeleteDialog = false }) { Text("保留任务") }
            }
        )
    }
}

@Composable
private fun ProjectProgress(project: ResumeProject) {
    val progress = when (project.stage) {
        ProjectStage.IMPORTED -> 0.22f
        ProjectStage.ANALYZED -> 0.5f
        ProjectStage.DRAFTED -> 0.76f
        ProjectStage.READY -> 1f
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (project.stage) {
                    ProjectStage.IMPORTED -> "准备材料"
                    ProjectStage.ANALYZED -> "诊断完成"
                    ProjectStage.DRAFTED -> "等待人工审核"
                    ProjectStage.READY -> "交付稿已生成"
                },
                fontSize = 12.sp,
                color = Moss,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = Muted
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = Moss,
            trackColor = Color(0xFFE3DED3)
        )
        Text(
            "输入、诊断和改写进度自动保存到本机",
            color = Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun WorkTabs(
    selected: String,
    hasAnalysis: Boolean,
    hasDraft: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkTab("原简历", selected == TAB_SOURCE) { onSelect(TAB_SOURCE) }
        WorkTab("诊断", selected == TAB_ANALYSIS, enabled = hasAnalysis) { onSelect(TAB_ANALYSIS) }
        WorkTab("改写审核", selected == TAB_REWRITE, enabled = hasDraft) { onSelect(TAB_REWRITE) }
        WorkTab("完整预览", selected == TAB_PREVIEW, enabled = hasDraft) { onSelect(TAB_PREVIEW) }
        WorkTab("原版式修订", selected == TAB_LAYOUT, enabled = hasDraft) { onSelect(TAB_LAYOUT) }
        WorkTab("版本历史", selected == TAB_VERSIONS, enabled = hasDraft) { onSelect(TAB_VERSIONS) }
        WorkTab("导出", selected == TAB_EXPORT, enabled = hasDraft) { onSelect(TAB_EXPORT) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTab(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) Moss else Color.Transparent,
        contentColor = if (selected) Color.White else if (enabled) Ink else Muted.copy(alpha = 0.45f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SourceTab(project: ResumeProject, viewModel: MainViewModel) {
    val context = LocalContext.current
    var title by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.title)
    }
    var clientName by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.clientName)
    }
    var dueDate by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.dueDate)
    }
    var orderNote by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.orderNote)
    }
    var modeName by rememberSaveable(project.id, project.stage) {
        mutableStateOf(project.optimizationMode.name)
    }
    var seniorityName by rememberSaveable(project.id, project.stage) {
        mutableStateOf(project.seniorityLevel.name)
    }
    var targetRole by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.targetRole)
    }
    var company by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.company)
    }
    var industry by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.industry)
    }
    var resumeText by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.resumeText)
    }
    var jobDescription by rememberSaveable(
        project.id,
        project.stage,
        project.jobDescriptionFileName
    ) {
        mutableStateOf(project.jobDescription)
    }
    var supplements by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.supplements)
    }
    var replaceDialog by remember { mutableStateOf(false) }
    var jdFileDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::replaceResume)
    }
    val jdPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::replaceJobDescription)
    }
    val mode = OptimizationMode.valueOf(modeName)
    fun currentInput() = ProjectInput(
        title = title,
        clientName = clientName,
        dueDate = dueDate,
        orderNote = orderNote,
        targetRole = targetRole,
        company = company,
        industry = industry,
        optimizationMode = mode,
        seniorityLevel = SeniorityLevel.valueOf(seniorityName),
        resumeText = resumeText,
        jobDescription = jobDescription,
        supplements = supplements
    )
    val latestInput by rememberUpdatedState(currentInput())
    DisposableEffect(project.id) {
        onDispose { viewModel.autosaveInputs(latestInput) }
    }
    LaunchedEffect(
        project.id,
        title,
        clientName,
        dueDate,
        orderNote,
        modeName,
        seniorityName,
        targetRole,
        company,
        industry,
        resumeText,
        jobDescription,
        supplements
    ) {
        delay(600)
        viewModel.autosaveInputs(currentInput())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FormSection("优化方式") {
                OptimizationModeSelector(mode) { modeName = it.name }
                Text(
                    when (mode) {
                        OptimizationMode.TARGETED -> "分析具体 JD 的岗位匹配和关键词差距。"
                        OptimizationMode.ROLE_ONLY -> "只有岗位方向，不猜测具体公司的招聘要求。"
                        OptimizationMode.GENERAL -> "不判断岗位匹配，只优化事实、结构、表达和可读性。"
                    },
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        item {
            FormSection("接单信息") {
                DetailTextField("任务名称", title, { title = it })
                DetailTextField("客户称呼", clientName, { clientName = it }, "仅保存在本机")
                DetailTextField("交付日期", dueDate, { dueDate = it }, "例如：2026-11-10")
                DetailTextField("订单备注", orderNote, { orderNote = it }, "例如：重点核对项目管理经历", minLines = 2)
            }
        }
        item {
            FormSection("岗位信息") {
                if (mode != OptimizationMode.GENERAL) {
                    DetailTextField(
                        if (mode == OptimizationMode.TARGETED) "目标岗位" else "岗位方向",
                        targetRole,
                        { targetRole = it }
                    )
                }
                SectionCaption("目标公司")
                DetailTextField("", company, { company = it }, "可空")
                SectionCaption("行业")
                DetailTextField("", industry, { industry = it }, "例如：制造业、互联网")
                SenioritySelector(
                    selected = SeniorityLevel.valueOf(seniorityName),
                    onSelect = { seniorityName = it.name }
                )
            }
        }
        item {
            FormSection("原简历") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Moss)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            project.resumeFileName.ifBlank { "手动录入的简历文字" },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${resumeText.length} 字", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { replaceDialog = true }) { Text("重新导入") }
                }
                HorizontalDivider(color = Line)
                DetailTextField(
                    label = "简历正文",
                    value = resumeText,
                    onValueChange = { resumeText = it },
                    placeholder = "简历正文",
                    minLines = 12
                )
            }
        }
        if (mode == OptimizationMode.TARGETED) {
            item {
                FormSection("目标岗位 JD") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            project.jobDescriptionFileName.ifBlank { "未导入 JD 文件" },
                            modifier = Modifier.weight(1f),
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { jdFileDialog = true }) { Text("从文件导入") }
                    }
                    DetailTextField(
                        label = "招聘要求",
                        value = jobDescription,
                        onValueChange = { jobDescription = it },
                        placeholder = "岗位职责、任职要求、加分项…",
                        minLines = 10
                    )
                }
            }
        }
        item {
            FormSection("补充事实（可选）") {
                Text(
                    "如果知道原简历漏掉的业务规模、个人贡献、技术栈或结果，可以先写在这里。不要把猜测写成事实。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                DetailTextField(
                    "补充信息",
                    supplements,
                    { supplements = it },
                    "例如：项目服务 12 万日活用户；我负责其中支付模块…",
                    minLines = 4
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.saveAndAnalyze(currentInput()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moss)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存并开始诊断", fontWeight = FontWeight.Bold)
            }
            Text(
                if (project.analysis != null) {
                    "重新诊断会先更新事实账本，并清除当前 AI 改写稿。"
                } else {
                    "诊断会先提取事实账本，再根据当前优化方式检查简历。"
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                color = Muted,
                fontSize = 12.sp
            )
        }
    }

    if (replaceDialog) {
        AlertDialog(
            onDismissRequest = { replaceDialog = false },
            title = { Text("重新导入原简历") },
            text = { Text("这会覆盖当前简历文字，并清除已经生成的诊断和改写稿。") },
            confirmButton = {
                TextButton(onClick = {
                    replaceDialog = false
                    picker.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/pdf",
                            "text/plain",
                            "image/*"
                        )
                    )
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { replaceDialog = false }) { Text("取消") }
            }
        )
    }
    if (jdFileDialog) {
        AlertDialog(
            onDismissRequest = { jdFileDialog = false },
            title = { Text("从文件导入 JD") },
            text = { Text("支持 DOCX、PDF、TXT 和招聘截图，导入后会覆盖当前 JD 文字。") },
            confirmButton = {
                TextButton(onClick = {
                    jdFileDialog = false
                    jdPicker.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/pdf",
                            "text/plain",
                            "image/*"
                        )
                    )
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { jdFileDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AnalysisTab(
    project: ResumeProject,
    viewModel: MainViewModel,
    onGoRewrite: () -> Unit
) {
    val context = LocalContext.current
    val analysis = project.analysis
    if (analysis == null) {
        EmptyTab("还没有诊断结果", "回到“原简历”页，保存材料并开始诊断。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScoreCard(
                analysis,
                isGeneral = project.optimizationMode == OptimizationMode.GENERAL
            )
        }
        if (project.ruleChecks.isNotEmpty()) {
            item {
                AnalysisSection("规则检查") {
                    project.ruleChecks.forEach { check ->
                        Row(
                            Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                if (check.passed) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = if (check.passed) Moss else Coral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(check.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(check.detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
        if (project.factLedger.isNotEmpty()) {
            item {
                AnalysisSection("事实账本") {
                    Text(
                        "诊断和改写使用以下原文事实作为依据。这里只提取事实，不补充猜测。",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    project.factLedger.take(40).forEach { fact ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                fact.id,
                                color = Moss,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(38.dp)
                            )
                            Text(fact.quote, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                    if (project.factLedger.size > 40) {
                        Text("其余 ${project.factLedger.size - 40} 条已保存，不在此处展开。", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (project.draftSections.isNotEmpty() || analysis.recommendedSectionOrder.isNotEmpty()) {
            item {
                AnalysisSection("章节顺序建议") {
                    if (project.draftSections.isNotEmpty()) {
                        Text("当前预览顺序", color = Muted, fontSize = 11.sp)
                        Text(
                            project.draftSections.joinToString(" → ") { it.title },
                            color = Ink,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    if (analysis.shouldReorderSections && analysis.recommendedSectionOrder.isNotEmpty()) {
                        Text(
                            analysis.sectionOrderAdvice,
                            color = Ink,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        Text("建议顺序", color = Muted, fontSize = 11.sp)
                        Text(
                            analysis.recommendedSectionOrder.joinToString(" → "),
                            color = Moss,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                        Text(
                            "应用后完整预览会按新顺序显示。导出仍保留原顺序版，并尝试生成沿用原模板的调整顺序版。",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                        OutlinedButton(
                            onClick = viewModel::applySuggestedSectionOrder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("应用模块顺序")
                        }
                    } else {
                        Text(
                            "当前顺序没有明显影响岗位重点的问题，暂时无需调整。",
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
        item {
            AnalysisSection("匹配优势") {
                if (analysis.strengths.isEmpty()) {
                    Text("暂未发现足够有力的岗位匹配证据。", color = Muted)
                } else {
                    analysis.strengths.forEach { StrengthRow(it) }
                }
            }
        }
        item {
            AnalysisSection("问题清单") {
                if (analysis.issues.isEmpty()) {
                    Text("没有发现明显问题，但仍建议通读事实与时间线。", color = Muted)
                } else {
                    analysis.issues.forEachIndexed { index, issue ->
                        IssueCard(issue)
                        if (index != analysis.issues.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        if (analysis.missingQuestions.isNotEmpty() || analysis.clientQuestionMessage.isNotBlank()) {
            item {
                AnalysisSection("需要向候选人确认") {
                    Text(
                        "可以直接把下面的话发给客户。问题只围绕具体经历，避免让对方感觉是在填表。",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    val clientMessage = clientMessageFor(analysis)
                    Surface(
                        color = Color(0xFFF0F5F2),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            clientMessage,
                            modifier = Modifier.padding(13.dp),
                            color = Ink,
                            fontSize = 13.sp,
                            lineHeight = 21.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            copyText(context, "给客户的核实消息", clientMessage)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("复制给客户的消息")
                    }
                    HorizontalDivider(color = Line)
                    Text("内部核实点", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    analysis.missingQuestions.forEachIndexed { index, question ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Text(
                                "${index + 1}.",
                                color = Moss,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(question, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
        if (analysis.riskWarnings.isNotEmpty()) {
            item {
                AnalysisSection("风险提醒") {
                    analysis.riskWarnings.forEach {
                        Row(
                            Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Amber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(it, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onGoRewrite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Moss)
            ) {
                Text("补充事实并开始改写", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScoreCard(analysis: ResumeAnalysis, isGeneral: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D5448)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${analysis.overallScore}",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isGeneral) "质量评分" else "适配评分",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    analysis.fitSummary,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            analysis.dimensions.forEach { DimensionRow(it.name, it.score, it.detail) }
            Text(
                "评分是内部修改参考，不代表录用概率。",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DimensionRow(name: String, score: Int, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("$score", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = Color(0xFF9DE0C8),
            trackColor = Color.White.copy(alpha = 0.13f)
        )
        if (detail.isNotBlank()) {
            Text(detail, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun RewriteTab(
    project: ResumeProject,
    viewModel: MainViewModel,
    onExport: () -> Unit
) {
    var supplements by rememberSaveable(project.id, project.supplements) {
        mutableStateOf(project.supplements)
    }
    val latestSupplements by rememberUpdatedState(supplements)
    DisposableEffect(project.id) {
        onDispose { viewModel.autosaveSupplements(latestSupplements) }
    }
    LaunchedEffect(project.id, supplements) {
        delay(600)
        viewModel.autosaveSupplements(supplements)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FormSection("补充候选人事实") {
                Text(
                    "回答诊断中的问题，或补充业绩、规模、职责边界。没有证据的数字会保留为“待补充”。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                DetailTextField(
                    "补充信息",
                    supplements,
                    { supplements = it },
                    "按问题逐条回答…",
                    minLines = 5
                )
                Button(
                    onClick = { viewModel.saveAndGenerateDraft(supplements) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss)
                ) {
                    Icon(
                        if (project.draftSections.isEmpty()) Icons.Outlined.AutoAwesome else Icons.Outlined.Refresh,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (project.draftSections.isEmpty()) "开始改写" else "按补充信息重新生成")
                }
            }
        }
        if (project.draftSections.isEmpty()) {
            item { EmptyTab("还没有改写稿", "补充事实后点击“开始改写”。") }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("逐段审核", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${project.acceptedSections.size}/${project.draftSections.size} 已采用",
                        color = Moss,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            item {
                AnalysisSection("修改后评价") {
                    Text(
                        "本次采用 ${project.acceptedSections.size} 个章节，${project.draftSections.count { it.changeNote.isNotBlank() }} 个章节记录了修改原因，${project.draftSections.count { it.benefit.isNotBlank() }} 个章节记录了修改收益。",
                        color = Ink,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        "剩余核实项 ${project.verificationChecklist.size} 条，第二模型审核 ${project.secondReviewFindings.size} 条。",
                        color = if (
                            project.verificationChecklist.isEmpty() &&
                            project.secondReviewFindings.isEmpty()
                        ) {
                            Moss
                        } else {
                            Coral
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            project.draftSections.forEachIndexed { index, section ->
                item(key = section.id) {
                    SectionEditorCard(
                        section = section,
                        locked = project.activeVersionId != null &&
                            project.activeVersionId == project.finalVersionId,
                        canMoveUp = index > 0,
                        canMoveDown = index < project.draftSections.lastIndex,
                        onDecision = { accepted, keepOriginal ->
                            viewModel.setSectionDecision(section.id, accepted, keepOriginal)
                        },
                        onSave = { viewModel.updateSection(section.id, it) },
                        onMoveUp = { viewModel.moveSection(section.id, -1) },
                        onMoveDown = { viewModel.moveSection(section.id, 1) }
                    )
                }
            }
            item {
                FactAuditCard(project)
            }
            item {
                Button(
                    onClick = onExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("审核完成，准备导出", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SectionEditorCard(
    section: DraftSection,
    locked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDecision: (Boolean, Boolean) -> Unit,
    onSave: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var content by remember(section.id, section.content) { mutableStateOf(section.content) }
    var showAdvanced by remember(section.id) { mutableStateOf(false) }
    var editing by remember(section.id) { mutableStateOf(false) }
    val latestContent by rememberUpdatedState(content)
    DisposableEffect(section.id) {
        onDispose {
            if (latestContent != section.content) onSave(latestContent)
        }
    }
    LaunchedEffect(section.id, content) {
        delay(600)
        if (content != section.content) onSave(content)
    }
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (section.accepted) WarmWhite else Color(0xFFE9E6DF)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (section.accepted) Line else Line.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, fontWeight = FontWeight.Bold)
                    Text(
                        if (locked) {
                            "最终版已锁定"
                        } else if (!section.accepted) {
                            "不写入交付版"
                        } else if (section.keepOriginal) {
                            "使用原简历内容"
                        } else {
                            "使用 AI 改写内容"
                        },
                        color = if (section.accepted) Moss else Muted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp && !locked) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown && !locked) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FilterChip(
                    selected = section.accepted && !section.keepOriginal,
                    onClick = { onDecision(true, false) },
                    enabled = !locked,
                    label = { Text("采用改写") }
                )
                FilterChip(
                    selected = section.accepted && section.keepOriginal,
                    onClick = { onDecision(true, true) },
                    enabled = !locked &&
                        (section.originalContent.isNotBlank() || section.sourceEvidence.isNotBlank()),
                    label = { Text("保留原文") }
                )
                FilterChip(
                    selected = !section.accepted,
                    onClick = { onDecision(false, false) },
                    enabled = !locked,
                    label = { Text("从最终稿移除") }
                )
            }
            ReviewTextBlock(
                label = "原文",
                text = section.originalContent.ifBlank { section.sourceEvidence },
                compareWith = content,
                background = Color(0xFFF2F0EA),
                highlightColor = Color(0xFFFFE0D4)
            )
            if (section.accepted && section.keepOriginal) {
                ReviewTextBlock(
                    label = "最终稿使用原文",
                    text = section.originalContent.ifBlank { section.sourceEvidence },
                    compareWith = content,
                    background = Color(0xFFE8F1ED),
                    highlightColor = Color.Transparent
                )
            } else {
                ReviewTextBlock(
                    label = "改写后",
                    text = content,
                    compareWith = section.originalContent.ifBlank { section.sourceEvidence },
                    background = Color(0xFFF0F5F2),
                    highlightColor = Color(0xFFCDEBDD)
                )
                TextButton(
                    onClick = { editing = !editing },
                    enabled = !locked && section.accepted
                ) {
                    Text(if (editing) "收起编辑" else "编辑改写文字")
                }
                if (editing && section.accepted) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        enabled = !locked,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 20,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            if (section.changeNote.isNotBlank()) {
                Text(
                    "修改原因：${limitReason(section.changeNote)}",
                    color = Ink,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            if (section.verificationStatus.isNotBlank()) {
                Surface(
                    color = Color(0xFFFFEFEA),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "需要核实：${section.verificationStatus}",
                        modifier = Modifier.padding(10.dp),
                        color = Coral,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            if (content.contains("待补充")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("此处仍需核对或补充事实", color = Amber, fontSize = 12.sp)
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "收起高级信息" else "查看收益、依据和逐句对照")
            }
            if (showAdvanced) {
                if (limitReason(section.changeNote) != section.changeNote) {
                    Text(
                        "完整修改说明：${section.changeNote}",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                if (section.benefit.isNotBlank()) {
                    Text("修改收益：${section.benefit}", color = Moss, fontSize = 12.sp)
                }
                if (section.sourceEvidence.isNotBlank()) {
                    Text(
                        "原文依据：${section.sourceEvidence}",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Surface(
                    color = Color(0xFFF4F2EC),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("逐句变化", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        TextDiffEngine.compare(
                            section.originalContent.ifBlank { section.sourceEvidence },
                            content
                        ).filter { it.kind != DiffKind.SAME }
                            .take(20)
                            .forEach { line ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        when (line.kind) {
                                            DiffKind.SAME -> "未变"
                                            DiffKind.CHANGED -> "改写"
                                            DiffKind.ADDED -> "新增"
                                            DiffKind.REMOVED -> "删除"
                                        },
                                        color = if (line.kind == DiffKind.CHANGED) Amber else Coral,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (line.original.isNotBlank()) {
                                        Text("原：${line.original}", color = Muted, fontSize = 12.sp)
                                    }
                                    if (line.revised.isNotBlank()) {
                                        Text("新：${line.revised}", color = Ink, fontSize = 12.sp)
                                    }
                                }
                            }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { content = section.content },
                    modifier = Modifier.weight(1f),
                    enabled = !locked && section.accepted && !section.keepOriginal
                ) { Text("恢复 AI 稿") }
                Button(
                    onClick = { onSave(content) },
                    enabled = !locked && section.accepted && !section.keepOriginal &&
                        content != section.content,
                    modifier = Modifier.weight(1f)
                ) { Text("保存修改") }
            }
        }
    }
}

@Composable
private fun ReviewTextBlock(
    label: String,
    text: String,
    compareWith: String,
    background: Color,
    highlightColor: Color
) {
    val highlighted = highlightChangedText(
        text.ifBlank { "没有可显示的内容" },
        compareWith,
        highlightColor
    )
    Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
            Text(
                highlighted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

private fun limitReason(value: String): String {
    val sentences = value.split(Regex("""(?<=[。；;！!？?])"""))
        .map(String::trim)
        .filter(String::isNotBlank)
    return sentences.take(2).joinToString("").ifBlank { value.trim() }
}

private fun highlightChangedText(
    text: String,
    compareWith: String,
    highlightColor: Color
): AnnotatedString {
    if (highlightColor == Color.Transparent || compareWith.isBlank() || text == compareWith) {
        return AnnotatedString(text)
    }
    var prefix = 0
    while (
        prefix < text.length &&
        prefix < compareWith.length &&
        text[prefix] == compareWith[prefix]
    ) {
        prefix++
    }
    var suffix = 0
    while (
        suffix < text.length - prefix &&
        suffix < compareWith.length - prefix &&
        text[text.lastIndex - suffix] == compareWith[compareWith.lastIndex - suffix]
    ) {
        suffix++
    }
    val changedEnd = text.length - suffix
    return buildAnnotatedString {
        append(text.substring(0, prefix))
        if (changedEnd > prefix) {
            withStyle(SpanStyle(background = highlightColor)) {
                append(text.substring(prefix, changedEnd))
            }
        }
        if (suffix > 0) append(text.substring(changedEnd))
    }
}

@Composable
private fun FactAuditCard(project: ResumeProject) {
    val context = LocalContext.current
    AnalysisSection("事实核验") {
        Text(
            "以下内容用于内部检查，不会写入最终简历 DOCX。",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        HorizontalDivider(color = Line)
        Text("需客户核实清单", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (project.verificationChecklist.isEmpty()) {
            Text("当前没有自动发现的存疑事实，仍建议逐项向客户确认。", color = Muted, fontSize = 13.sp)
        } else {
            project.verificationChecklist.forEachIndexed { index, item ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}.",
                        color = Coral,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(item, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
            OutlinedButton(
                onClick = {
                    copyText(
                        context,
                        "需客户核实清单",
                        project.verificationChecklist.mapIndexed { index, item ->
                            "${index + 1}. $item"
                        }.joinToString("\n")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("复制核实清单")
            }
        }
        HorizontalDivider(color = Line)
        Text("数据溯源表", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (project.traceability.isEmpty()) {
            Text("AI 没有返回逐条来源，导出时会提示人工复核。", color = Muted, fontSize = 13.sp)
        } else {
            project.traceability.forEachIndexed { index, entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("${index + 1}. ${entry.fact}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("修订：${entry.rewrittenText}", color = Muted, fontSize = 12.sp)
                    Text(
                        "依据：${entry.sourceEvidence.ifBlank { "未提供" }}",
                        color = if (entry.sourceEvidence.isBlank()) Coral else Moss,
                        fontSize = 12.sp
                    )
                    Text("状态：${entry.status.ifBlank { "待核实" }}", color = Amber, fontSize = 12.sp)
                }
            }
        }
        if (project.secondReviewFindings.isNotEmpty()) {
            HorizontalDivider(color = Line)
            Text(
                "第二模型审核${project.secondReviewProvider.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            project.secondReviewFindings.forEachIndexed { index, finding ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}.",
                        color = Coral,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(finding, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun FullPreviewTab(project: ResumeProject) {
    val context = LocalContext.current
    val sections = project.acceptedSections
    val fullText = buildString {
        appendLine(project.targetRole.ifBlank { "简历" })
        if (project.company.isNotBlank()) appendLine(project.company)
        sections.forEach { section ->
            appendLine()
            appendLine(section.title)
            appendLine(section.content)
        }
    }.trim()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                color = Color(0xFFFFF1D8),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("完整内容预览", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "这里按最终采用状态合并所有章节。导出的 DOCX 分页仍建议用 Word 检查。",
                        color = Color(0xFF77551B),
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                    if (project.layoutRevision.isNotEmpty()) {
                        Text(
                            "原版式修订稿会在此基础上保留原 Word 模板；本页显示的是文字内容。",
                            color = Moss,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
        if (sections.isEmpty()) {
            item {
                EmptyTab("没有可预览的内容", "回到改写审核页，至少采用一个章节。")
            }
        } else {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Column(
                            Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                project.targetRole.ifBlank { "简历" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (project.company.isNotBlank()) {
                                Text(project.company, color = Muted, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = Line)
                            sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        section.title,
                                        color = Moss,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        section.content,
                                        fontSize = 13.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { copyText(context, "简历完整预览", fullText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制完整文字")
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun OriginalLayoutTab(project: ResumeProject, viewModel: MainViewModel, locked: Boolean) {
    val isDocx = project.localResumePath?.endsWith(".docx", ignoreCase = true) == true
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FormSection("原版式修订") {
                Text(
                    "直接在客户原 DOCX 的文字段落上替换内容，保留照片、字体、表格、页眉页脚和模板结构。",
                    color = Ink,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                if (!isDocx) {
                    Text(
                        "当前原简历不是 DOCX，无法在原模板中直接改字。请要求客户提供 DOCX，或使用重新排版版本。",
                        color = Coral,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        "先生成逐段修改预览，再检查每一段；确认后到导出页生成 DOCX。应用模块顺序后，会额外生成调整顺序版。",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = viewModel::prepareOriginalLayoutRevision,
                        enabled = project.analysis != null && !locked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Moss)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (project.layoutRevision.isEmpty()) {
                                "生成逐段修改预览"
                            } else {
                                "重新生成逐段修改预览"
                            }
                        )
                    }
                }
            }
        }
        if (project.layoutRevision.isNotEmpty()) {
            item {
                Text(
                    "预览 ${project.layoutRevision.size} 处文字修改。未列出的段落保持原文。",
                    color = Moss,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
            items(project.layoutRevision, key = { it.index }) { edit ->
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(containerColor = WarmWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("第 ${edit.index} 段", fontWeight = FontWeight.Bold)
                        Text("原文：${edit.original}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("改为：${edit.revised}", fontSize = 13.sp, lineHeight = 19.sp)
                        if (edit.reason.isNotBlank()) {
                            Text("原因：${edit.reason}", fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        TextButton(
                            onClick = { viewModel.removeLayoutRevision(edit.index) },
                            enabled = !locked
                        ) {
                            Text("保留原文", color = if (locked) Muted else Moss)
                        }
                    }
                }
            }
        } else if (isDocx && project.analysis != null) {
            item {
                Text(
                    "还没有原版式修改计划。生成预览后，这里会逐段显示原文和改写内容。",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun VersionsTab(
    project: ResumeProject,
    viewModel: MainViewModel,
    onContinue: (String) -> Unit
) {
    var request by rememberSaveable(project.id) { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FormSection("客户返修") {
                Text(
                    "直接粘贴客户反馈。系统会在当前版本基础上生成一个新版本，旧版本不会被覆盖。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                DetailTextField(
                    "客户要求",
                    request,
                    { request = it },
                    "例如：第二段项目经历太长，帮我压缩但不要改变事实",
                    minLines = 4
                )
                Button(
                    onClick = {
                        viewModel.reviseDraft(request)
                        request = ""
                    },
                    enabled = request.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("基于当前版本继续修改")
                }
            }
        }
        item {
            Text(
                "版本记录",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
        if (project.versions.isEmpty()) {
            item {
                EmptyTab("还没有历史版本", "完成第一次改写后，每次生成或返修都会保留一个版本。")
            }
        } else {
            items(project.versions.asReversed(), key = { it.id }) { version ->
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (version.id == project.activeVersionId) {
                            Color(0xFFE8F1ED)
                        } else {
                            WarmWhite
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (version.id == project.activeVersionId) Moss else Line
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(version.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (version.id == project.activeVersionId) {
                                Spacer(Modifier.width(7.dp))
                                VersionBadge("当前")
                            }
                            if (version.id == project.finalVersionId) {
                                Spacer(Modifier.width(7.dp))
                                VersionBadge("最终交付")
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                                    .format(Date(version.createdAt)),
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            version.instruction.ifBlank { "初次生成版本" },
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            "采用 ${version.draftSections.count { it.accepted }}/${version.draftSections.size} 段；核实 ${version.verificationChecklist.size} 条",
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onContinue(version.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (version.id == project.activeVersionId) "继续编辑" else "恢复并继续") }
                            OutlinedButton(
                                onClick = {
                                    if (version.id == project.finalVersionId) {
                                        viewModel.clearFinalVersion()
                                    } else {
                                        viewModel.markFinalVersion(version.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (version.id == project.finalVersionId) {
                                        "取消最终锁定"
                                    } else {
                                        "设为最终版"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun VersionBadge(label: String) {
    Surface(color = Color(0xFFDCEBE5), shape = CircleShape) {
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
private fun ExportTab(
    project: ResumeProject,
    export: ExportBundle?,
    onExport: () -> Unit,
    onPrepareLayout: () -> Unit
) {
    val context = LocalContext.current
    val placeholderCount = project.acceptedSections.sumOf { section ->
        Regex("待补充").findAll(section.content).count()
    }
    val riskyUnaccepted = project.draftSections.count {
        !it.accepted && it.verificationStatus.isNotBlank()
    }
    val looksLikeSecret = Regex("""sk-[A-Za-z0-9_-]{12,}|Bearer\s+[A-Za-z0-9._-]{12,}""")
        .containsMatchIn(project.resumeText)
    val sourceIsDocx = project.localResumePath?.endsWith(".docx", ignoreCase = true) == true
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F0EB)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Moss)
                    Text("交付前检查", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "导出只生成客户要用的 DOCX，不包含诊断、核实清单、溯源表或内部备注。",
                        color = Muted,
                        lineHeight = 20.sp
                    )
                    Text(
                        "应用模板生成的文件可以直接编辑；DOCX 原模板修订会保留照片和原有版式。",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    HorizontalDivider(color = Color(0xFFC6DDD3))
                    Text("已采用章节：${project.acceptedSections.size} 个", fontWeight = FontWeight.Medium)
                    Text(
                        "核实清单：${project.verificationChecklist.size} 条；溯源记录：${project.traceability.size} 条",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    if (project.secondReviewFindings.isNotEmpty()) {
                        Text(
                            "第二模型审核：${project.secondReviewFindings.size} 条，需要逐条查看",
                            color = Coral,
                            fontSize = 13.sp
                        )
                    }
                    if (sourceIsDocx && project.layoutRevision.isEmpty()) {
                        Text(
                            "请先到“原版式修订”页生成并检查逐段修改预览。导出后只生成保留原模板的 DOCX。",
                            color = Amber,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    } else if (sourceIsDocx) {
                        Text(
                            if (project.sectionOrderApplied) {
                                "已准备 ${project.layoutRevision.size} 处文字修改。导出会生成原顺序版和调整顺序版两个 DOCX，均沿用原模板；若无法可靠识别模块边界，会提示原因。"
                            } else {
                                "已准备 ${project.layoutRevision.size} 处文字修改。导出只生成一个保留原模板和原顺序的 DOCX。"
                            },
                            color = Moss,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    } else {
                        Text(
                            "当前来源不是 DOCX，将按应用模板生成一个可编辑 DOCX，不额外生成 PDF。",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        if (placeholderCount == 0) {
                            "没有发现“待补充”占位符。请仍然核对经历真实性。"
                        } else {
                            "发现 $placeholderCount 处“待补充”占位符。建议先补充或确认，否则不要直接交付。"
                        },
                        color = if (placeholderCount == 0) Moss else Coral,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    if (riskyUnaccepted > 0) {
                        Text(
                            "还有 $riskyUnaccepted 个风险段落未采用，请确认它们确实应该排除。",
                            color = Coral,
                            fontSize = 13.sp
                        )
                    }
                    if (looksLikeSecret) {
                        Text(
                            "简历正文疑似包含 API Key 或 Bearer 凭证，交付前请删除。",
                            color = Coral,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        item {
            AnalysisSection("将写入的内容") {
                project.acceptedSections.forEachIndexed { index, section ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(
                            "${index + 1}.",
                            color = Moss,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(25.dp)
                        )
                        Text(section.title, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Button(
                onClick = if (sourceIsDocx && project.layoutRevision.isEmpty()) {
                    onPrepareLayout
                } else {
                    onExport
                },
                enabled = project.acceptedSections.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.IosShare, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (sourceIsDocx && project.layoutRevision.isEmpty()) {
                        "先生成原版式修改预览"
                    } else {
                        "生成修订版 DOCX"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (export != null) {
            item {
                AnalysisSection("最近一次导出") {
                    export.warning?.let {
                        Text(it, color = Amber, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    export.files.forEachIndexed { index, file ->
                        ExportFileRow(file.name)
                        Button(
                            onClick = { shareFiles(context, listOf(file)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (index == 0) Moss else Color(0xFF526C78)
                            )
                        ) {
                            Icon(Icons.Outlined.IosShare, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (index == 0) "分享修订版" else "分享调整顺序修订版")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ExportFileRow(name: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Moss, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnalysisSection(title: String, content: @Composable () -> Unit) {
    FormSection(title, content)
}

@Composable
private fun StrengthRow(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = Moss,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun IssueCard(issue: ResumeIssue) {
    val color = when (issue.severity) {
        IssueSeverity.CRITICAL -> Coral
        IssueSeverity.WARNING -> Amber
        IssueSeverity.INFO -> Moss
    }
    Surface(
        color = color.copy(alpha = 0.07f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (issue.severity) {
                        IssueSeverity.CRITICAL -> "高风险"
                        IssueSeverity.WARNING -> "需修改"
                        IssueSeverity.INFO -> "可优化"
                    },
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(issue.title, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            }
            if (issue.evidence.isNotBlank()) {
                Text("证据：${issue.evidence}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Text("建议：${issue.advice}", fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun EmptyTab(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(30.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DetailTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { if (label.isNotBlank()) Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Muted) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = if (minLines > 1) 30 else 1,
        shape = RoundedCornerShape(13.dp)
    )
}

@Composable
private fun SectionCaption(text: String) {
    Text(text, color = Muted, fontSize = 12.sp)
}

private fun shareFiles(context: Context, files: List<File>) {
    val uris = files.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享交付文件"))
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun clientMessageFor(analysis: ResumeAnalysis): String {
    val questions = analysis.missingQuestions
        .take(6)
        .mapIndexed { index, question ->
            "${index + 1}. ${question.trim().removeSuffix("。")}。"
        }
        .joinToString("\n")
    if (questions.isBlank()) return analysis.clientQuestionMessage
    return """
        您好，您的简历我已经看过了，想跟您确认几个地方，主要是避免修改时把您的工作内容和成果写偏。

        $questions

        您可以按编号直接回复，记不清的地方写“不确定”，或者告诉我大概情况也可以，不用勉强补充。
    """.trimIndent()
}
