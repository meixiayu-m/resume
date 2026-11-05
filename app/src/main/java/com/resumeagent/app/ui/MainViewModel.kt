package com.resumeagent.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resumeagent.app.ai.AiClient
import com.resumeagent.app.data.DocumentImporter
import com.resumeagent.app.data.ProjectStore
import com.resumeagent.app.data.SecureSettings
import com.resumeagent.app.domain.AiProviderId
import com.resumeagent.app.domain.AiSettings
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.export.DocumentExporter
import com.resumeagent.app.export.ExportBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val projects: List<ResumeProject> = emptyList(),
    val selectedProject: ResumeProject? = null,
    val busyMessage: String? = null,
    val error: String? = null,
    val lastExport: ExportBundle? = null,
    val initialized: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val secureSettings = SecureSettings(application)
    private val aiClient = AiClient(secureSettings)
    private val importer = DocumentImporter(application, aiClient, store)
    private val exporter = DocumentExporter(application, store)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        refreshProjects()
    }

    fun hasApiKey(): Boolean =
        secureSettings.hasApiKey(secureSettings.readAiSettings().activeTextProvider)

    fun hasApiKey(provider: AiProviderId): Boolean = secureSettings.hasApiKey(provider)

    fun readSettings(): AiSettings = secureSettings.readAiSettings()

    fun saveSettings(settings: AiSettings, apiKeys: Map<AiProviderId, String>) {
        apiKeys.forEach { (provider, key) ->
            if (key.isNotBlank()) secureSettings.saveApiKey(provider, key)
        }
        secureSettings.saveAiSettings(settings)
        clearError()
    }

    fun clearApiKey(provider: AiProviderId) {
        secureSettings.clearApiKey(provider)
    }

    fun refreshProjects() {
        viewModelScope.launch {
            val projects = store.list()
            _state.update { current ->
                val selectedId = current.selectedProject?.id ?: store.lastSelectedProjectId()
                val selected = projects.firstOrNull { it.id == selectedId }
                current.copy(projects = projects, selectedProject = selected, initialized = true)
            }
        }
    }

    fun selectProject(project: ResumeProject?) {
        store.setLastSelectedProjectId(project?.id)
        _state.update { it.copy(selectedProject = project, lastExport = null, error = null) }
    }

    fun createProject(
        title: String,
        company: String,
        targetRole: String,
        industry: String,
        resumeUri: Uri?,
        resumeText: String,
        jobDescription: String
    ) {
        if (targetRole.isBlank()) {
            showError("请填写目标岗位")
            return
        }
        if (jobDescription.isBlank()) {
            showError("请粘贴目标岗位 JD")
            return
        }
        if (resumeUri == null && resumeText.isBlank()) {
            showError("请选择简历文件或粘贴简历文字")
            return
        }
        runJob(if (resumeUri != null) "正在读取原简历…" else "正在创建任务…") {
            var project = ResumeProject(
                title = title.ifBlank { targetRole },
                company = company.trim(),
                targetRole = targetRole.trim(),
                industry = industry.trim(),
                resumeText = resumeText.trim(),
                jobDescription = jobDescription.trim()
            )
            if (resumeUri != null) {
                val imported = importer.import(project.id, resumeUri)
                project = project.copy(
                    resumeFileName = imported.displayName,
                    localResumePath = imported.localPath,
                    resumeText = imported.text
                )
            }
            require(project.resumeText.isNotBlank()) { "简历内容为空，请粘贴文字后重试" }
            store.save(project)
            loadProjectsAndSelect(project)
        }
    }

    fun replaceResume(uri: Uri) {
        val project = _state.value.selectedProject ?: return
        runJob("正在读取原简历…") {
            val imported = importer.import(project.id, uri)
            val updated = project.copy(
                resumeFileName = imported.displayName,
                localResumePath = imported.localPath,
                resumeText = imported.text,
                analysis = null,
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                stage = ProjectStage.IMPORTED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun updateProjectFields(
        targetRole: String,
        company: String,
        industry: String,
        resumeText: String,
        jobDescription: String,
        supplements: String
    ) {
        val project = _state.value.selectedProject ?: return
        val contentChanged = project.resumeText != resumeText || project.jobDescription != jobDescription
        val updated = project.copy(
            targetRole = targetRole.trim(),
            company = company.trim(),
            industry = industry.trim(),
            resumeText = resumeText.trim(),
            jobDescription = jobDescription.trim(),
            supplements = supplements.trim(),
            analysis = if (contentChanged) null else project.analysis,
            draftSections = if (contentChanged) emptyList() else project.draftSections,
            verificationChecklist = if (contentChanged) emptyList() else project.verificationChecklist,
            traceability = if (contentChanged) emptyList() else project.traceability,
            stage = if (contentChanged) ProjectStage.IMPORTED else project.stage,
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    /**
     * 自动保存正在编辑的输入，但保留已经生成的诊断和改写稿。
     * 用户切到聊天 App 询问核实问题后回来，不会因为输入尚未手动保存而丢失当前进度。
     */
    fun autosaveInputs(
        targetRole: String,
        company: String,
        industry: String,
        resumeText: String,
        jobDescription: String,
        supplements: String
    ) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            targetRole = targetRole.trim(),
            company = company.trim(),
            industry = industry.trim(),
            resumeText = resumeText.trim(),
            jobDescription = jobDescription.trim(),
            supplements = supplements.trim(),
            updatedAt = System.currentTimeMillis()
        )
        if (updated != project) persist(updated, message = null)
    }

    fun autosaveSupplements(value: String) {
        val project = _state.value.selectedProject ?: return
        if (project.supplements == value.trim()) return
        persist(
            project.copy(
                supplements = value.trim(),
                updatedAt = System.currentTimeMillis()
            ),
            message = null
        )
    }

    fun analyzeProject() {
        val project = _state.value.selectedProject ?: return
        if (!hasApiKey()) {
            showError("请先在右上角设置中填写 API Key")
            return
        }
        require(project.resumeText.isNotBlank()) { "简历内容为空" }
        require(project.jobDescription.isNotBlank()) { "岗位 JD 为空" }
        runJob("正在核对岗位要求与简历证据…") {
            val analysis = aiClient.analyze(project)
            val updated = project.copy(
                analysis = analysis,
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                stage = ProjectStage.ANALYZED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun saveAndAnalyze(
        targetRole: String,
        company: String,
        industry: String,
        resumeText: String,
        jobDescription: String,
        supplements: String
    ) {
        val project = _state.value.selectedProject ?: return
        if (targetRole.isBlank() || resumeText.isBlank() || jobDescription.isBlank()) {
            showError("目标岗位、简历内容和岗位 JD 不能为空")
            return
        }
        if (!hasApiKey()) {
            showError("请先在右上角设置中填写 API Key")
            return
        }
        runJob("正在核对岗位要求与简历证据…") {
            val prepared = project.copy(
                targetRole = targetRole.trim(),
                company = company.trim(),
                industry = industry.trim(),
                resumeText = resumeText.trim(),
                jobDescription = jobDescription.trim(),
                supplements = supplements.trim(),
                updatedAt = System.currentTimeMillis()
            )
            store.save(prepared)
            val analysis = aiClient.analyze(prepared)
            val updated = prepared.copy(
                analysis = analysis,
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                stage = ProjectStage.ANALYZED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun saveSupplements(value: String) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            supplements = value.trim(),
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    fun generateDraft() {
        val project = _state.value.selectedProject ?: return
        if (project.analysis == null) {
            showError("请先完成简历诊断")
            return
        }
        if (!hasApiKey()) {
            showError("请先在设置中填写 API Key")
            return
        }
        runJob("正在按岗位证据重写简历…") {
            val audit = aiClient.rewrite(project)
            val updated = project.copy(
                draftSections = audit.sections,
                verificationChecklist = audit.verificationChecklist,
                traceability = audit.traceability,
                stage = ProjectStage.DRAFTED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun saveAndGenerateDraft(supplements: String) {
        val project = _state.value.selectedProject ?: return
        if (project.analysis == null) {
            showError("请先完成简历诊断")
            return
        }
        if (!hasApiKey()) {
            showError("请先在设置中填写 API Key")
            return
        }
        runJob("正在按岗位证据重写简历…") {
            val prepared = project.copy(
                supplements = supplements.trim(),
                updatedAt = System.currentTimeMillis()
            )
            store.save(prepared)
            val audit = aiClient.rewrite(prepared)
            val updated = prepared.copy(
                draftSections = audit.sections,
                verificationChecklist = audit.verificationChecklist,
                traceability = audit.traceability,
                stage = ProjectStage.DRAFTED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun updateSection(sectionId: String, content: String) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            draftSections = project.draftSections.map {
                if (it.id == sectionId) it.copy(content = content) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    fun toggleSection(sectionId: String) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            draftSections = project.draftSections.map {
                if (it.id == sectionId) it.copy(accepted = !it.accepted) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    fun moveSection(sectionId: String, delta: Int) {
        val project = _state.value.selectedProject ?: return
        val sections = project.draftSections.toMutableList()
        val index = sections.indexOfFirst { it.id == sectionId }
        val target = index + delta
        if (index < 0 || target !in sections.indices) return
        val item = sections.removeAt(index)
        sections.add(target, item)
        persist(project.copy(draftSections = sections, updatedAt = System.currentTimeMillis()), null)
    }

    fun exportProject() {
        val project = _state.value.selectedProject ?: return
        runJob("正在生成 DOCX、PDF 和修改报告…") {
            val bundle = exporter.export(project)
            val updated = project.copy(stage = ProjectStage.READY, updatedAt = System.currentTimeMillis())
            store.save(updated)
            val projects = store.list()
            _state.update {
                it.copy(
                    projects = projects,
                    selectedProject = updated,
                    lastExport = bundle,
                    busyMessage = null
                )
            }
        }
    }

    fun deleteProject() {
        val project = _state.value.selectedProject ?: return
        runJob("正在删除任务和原始文件…") {
            store.delete(project.id)
            val projects = store.list()
            _state.update {
                it.copy(projects = projects, selectedProject = null, lastExport = null, busyMessage = null)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearExport() {
        _state.update { it.copy(lastExport = null) }
    }

    private fun persist(project: ResumeProject, message: String?) {
        viewModelScope.launch {
            store.save(project)
            val projects = store.list()
            _state.update {
                it.copy(
                    projects = projects,
                    selectedProject = project,
                    busyMessage = message,
                    error = null
                )
            }
        }
    }

    private suspend fun loadProjectsAndSelect(project: ResumeProject) {
        val projects = store.list()
        store.setLastSelectedProjectId(project.id)
        _state.update {
            it.copy(
                projects = projects,
                selectedProject = projects.firstOrNull { item -> item.id == project.id } ?: project,
                busyMessage = null,
                error = null
            )
        }
    }

    private fun runJob(message: String, block: suspend () -> Unit) {
        if (_state.value.busyMessage != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = message, error = null) }
            runCatching { block() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            busyMessage = null,
                            error = error.message ?: "操作失败，请重试"
                        )
                    }
                }
                .onSuccess {
                    _state.update { it.copy(busyMessage = null) }
                }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
