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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.IssueSeverity
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ResumeAnalysis
import com.resumeagent.app.domain.ResumeIssue
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.export.ExportBundle
import kotlinx.coroutines.delay
import java.io.File

private const val TAB_SOURCE = "source"
private const val TAB_ANALYSIS = "analysis"
private const val TAB_REWRITE = "rewrite"
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
                            project.targetRole.ifBlank { "简历任务" },
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
                    onGoRewrite = { tab = TAB_REWRITE }
                )
                TAB_REWRITE -> RewriteTab(
                    project = project,
                    viewModel = viewModel,
                    onExport = { tab = TAB_EXPORT }
                )
                TAB_EXPORT -> ExportTab(
                    project = project,
                    export = state.lastExport,
                    onExport = viewModel::exportProject
                )
                else -> SourceTab(project = project, viewModel = viewModel)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这个任务？") },
            text = { Text("任务记录、原简历副本和导出的中间文件都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteProject()
                    onBack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
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
    var jobDescription by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.jobDescription)
    }
    var supplements by rememberSaveable(project.id, project.stage, project.resumeFileName) {
        mutableStateOf(project.supplements)
    }
    var replaceDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::replaceResume)
    }
    LaunchedEffect(
        project.id,
        targetRole,
        company,
        industry,
        resumeText,
        jobDescription,
        supplements
    ) {
        delay(600)
        viewModel.autosaveInputs(
            targetRole,
            company,
            industry,
            resumeText,
            jobDescription,
            supplements
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FormSection("岗位信息") {
                DetailTextField("目标岗位", targetRole, { targetRole = it })
                SectionCaption("目标公司")
                DetailTextField("", company, { company = it }, "可空")
                SectionCaption("行业")
                DetailTextField("", industry, { industry = it }, "例如：制造业、互联网")
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
        item {
            FormSection("目标岗位 JD") {
                DetailTextField(
                    label = "招聘要求",
                    value = jobDescription,
                    onValueChange = { jobDescription = it },
                    placeholder = "岗位职责、任职要求、加分项…",
                    minLines = 10
                )
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
                onClick = {
                    viewModel.saveAndAnalyze(
                        targetRole,
                        company,
                        industry,
                        resumeText,
                        jobDescription,
                        supplements
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
                Text("保存并开始诊断", fontWeight = FontWeight.Bold)
            }
            Text(
                if (project.analysis != null) "重新诊断会清除当前 AI 改写稿，但不会删除原简历。" else "诊断会结合岗位 JD 检查每条经历的证据。",
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
}

@Composable
private fun AnalysisTab(project: ResumeProject, onGoRewrite: () -> Unit) {
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
        item { ScoreCard(analysis) }
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
private fun ScoreCard(analysis: ResumeAnalysis) {
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
                        Text("匹配度", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
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
                "匹配度是内部修改参考，不代表录用概率。",
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
            project.draftSections.forEachIndexed { index, section ->
                item(key = section.id) {
                    SectionEditorCard(
                        section = section,
                        canMoveUp = index > 0,
                        canMoveDown = index < project.draftSections.lastIndex,
                        onToggle = { viewModel.toggleSection(section.id) },
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onSave: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var content by remember(section.id, section.content) { mutableStateOf(section.content) }
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
                Checkbox(checked = section.accepted, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(section.title, fontWeight = FontWeight.Bold)
                    Text(
                        if (section.accepted) "将写入交付稿" else "已排除",
                        color = if (section.accepted) Moss else Muted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移")
                }
            }
            if (section.changeNote.isNotBlank()) {
                Surface(
                    color = Color(0xFFF0F5F2),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "修改说明：${section.changeNote}",
                        modifier = Modifier.padding(10.dp),
                        color = MossDark,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            if (section.verificationStatus.isNotBlank()) {
                Surface(
                    color = Color(0xFFFFEFEA),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Coral.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("事实风险，需核实后才应采用", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            section.verificationStatus,
                            color = Ink,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            if (section.sourceEvidence.isNotBlank()) {
                Text(
                    "原文依据：${section.sourceEvidence}",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 30,
                shape = RoundedCornerShape(12.dp)
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { content = section.content },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复内容") }
                Button(
                    onClick = { onSave(content) },
                    enabled = content != section.content,
                    modifier = Modifier.weight(1f)
                ) { Text("保存修改") }
            }
        }
    }
}

@Composable
private fun FactAuditCard(project: ResumeProject) {
    val context = LocalContext.current
    AnalysisSection("事实核验") {
        Text(
            "以下内容会作为附录写入最终 DOCX 和 PDF。没有原文依据的事项必须由客户确认。",
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
    }
}

@Composable
private fun ExportTab(project: ResumeProject, export: ExportBundle?, onExport: () -> Unit) {
    val context = LocalContext.current
    val placeholderCount = project.acceptedSections.sumOf { section ->
        Regex("待补充").findAll(section.content).count()
    }
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
                        "将生成可编辑 DOCX、固定版式 PDF 和一份中文修改报告。",
                        color = Muted,
                        lineHeight = 20.sp
                    )
                    HorizontalDivider(color = Color(0xFFC6DDD3))
                    Text("已采用章节：${project.acceptedSections.size} 个", fontWeight = FontWeight.Medium)
                    Text(
                        "核实清单：${project.verificationChecklist.size} 条；溯源记录：${project.traceability.size} 条",
                        color = Muted,
                        fontSize = 13.sp
                    )
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
                onClick = onExport,
                enabled = project.acceptedSections.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.IosShare, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("生成交付文件", fontWeight = FontWeight.Bold)
            }
        }
        if (export != null) {
            item {
                AnalysisSection("最近一次导出") {
                    ExportFileRow(export.docx.name)
                    ExportFileRow(export.pdf.name)
                    ExportFileRow(export.report.name)
                    Button(
                        onClick = { shareFiles(context, export) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Moss)
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("通过手机分享")
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

private fun shareFiles(context: Context, export: ExportBundle) {
    val uris = listOf(export.docx, export.pdf, export.report).map { file: File ->
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
    val generated = analysis.clientQuestionMessage.trim()
    if (generated.isNotBlank()) return generated
    val questions = analysis.missingQuestions.mapIndexed { index, question ->
        "${index + 1}. $question"
    }.joinToString("\n")
    return """
        您好，我看过您的简历和这份岗位要求，想把几段经历再确认一下，避免改写时把您的工作范围和结果写偏。

        $questions

        不确定的地方直接写“不确定”就可以，不用勉强补充。
    """.trimIndent()
}
