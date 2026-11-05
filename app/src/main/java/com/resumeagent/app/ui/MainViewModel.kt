package com.resumeagent.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resumeagent.app.ProcessingService
import com.resumeagent.app.ai.AiClient
import com.resumeagent.app.ai.ModelCatalog
import com.resumeagent.app.data.DocumentImporter
import com.resumeagent.app.data.ProjectStore
import com.resumeagent.app.data.SecureSettings
import com.resumeagent.app.domain.AiProviderId
import com.resumeagent.app.domain.AiProviderSettings
import com.resumeagent.app.domain.AiSettings
import com.resumeagent.app.domain.AiModelInfo
import com.resumeagent.app.domain.DraftSection
import com.resumeagent.app.domain.OptimizationMode
import com.resumeagent.app.domain.ProjectStage
import com.resumeagent.app.domain.ProjectInput
import com.resumeagent.app.domain.ProjectVersion
import com.resumeagent.app.domain.ResumeProject
import com.resumeagent.app.domain.RuleChecker
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
    val modelsByProvider: Map<AiProviderId, List<AiModelInfo>> = emptyMap(),
    val modelLoadingProvider: AiProviderId? = null,
    val initialized: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val secureSettings = SecureSettings(application)
    private val aiClient = AiClient(secureSettings)
    private val importer = DocumentImporter(application, aiClient, store)
    private val exporter = DocumentExporter(application, store)
    private val _state = MutableStateFlow(MainUiState())
    private val deletingProjectIds = mutableSetOf<String>()
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

    fun fetchModels(provider: AiProviderSettings, typedApiKey: String) {
        if (_state.value.modelLoadingProvider != null) return
        viewModelScope.launch {
            _state.update { it.copy(modelLoadingProvider = provider.id, error = null) }
            runCatching {
                val key = typedApiKey.trim().ifBlank {
                    secureSettings.readApiKey(provider.id).orEmpty()
                }
                ModelCatalog.fetch(provider, key)
            }.onSuccess { models ->
                _state.update {
                    it.copy(
                        modelsByProvider = it.modelsByProvider + (provider.id to models),
                        modelLoadingProvider = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        modelLoadingProvider = null,
                        error = error.message ?: "模型列表获取失败"
                    )
                }
            }
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            val projects = mutableListOf<ResumeProject>()
            for (project in store.list()) {
                projects += migrateLegacyDraft(project)
            }
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

    fun createProject(input: ProjectInput, resumeUri: Uri?, jobDescriptionUri: Uri?) {
        validateInput(
            input,
            requireResumeText = resumeUri == null,
            requireJobDescription = jobDescriptionUri == null
        )?.let {
            showError(it)
            return
        }
        if (resumeUri == null && input.resumeText.isBlank()) {
            showError("请选择简历文件或粘贴简历文字")
            return
        }
        val clean = input.normalized()
        runJob(if (resumeUri != null) "正在读取原简历…" else "正在创建任务…") {
            var project = ResumeProject(
                title = clean.title.trim().ifBlank {
                    clean.targetRole.trim().ifBlank { "通用简历优化" }
                },
                clientName = clean.clientName.trim(),
                dueDate = clean.dueDate.trim(),
                orderNote = clean.orderNote.trim(),
                company = clean.company.trim(),
                targetRole = clean.targetRole.trim(),
                industry = clean.industry.trim(),
                optimizationMode = clean.optimizationMode,
                seniorityLevel = clean.seniorityLevel,
                resumeText = clean.resumeText.trim(),
                jobDescription = clean.jobDescription.trim()
            )
            if (resumeUri != null) {
                val imported = importer.import(project.id, resumeUri, "resume")
                project = project.copy(
                    resumeFileName = imported.displayName,
                    localResumePath = imported.localPath,
                    resumeText = imported.text
                )
            }
            if (jobDescriptionUri != null && clean.optimizationMode == OptimizationMode.TARGETED) {
                val imported = importer.import(project.id, jobDescriptionUri, "jd")
                project = project.copy(
                    jobDescriptionFileName = imported.displayName,
                    localJobDescriptionPath = imported.localPath,
                    jobDescription = imported.text
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
            val imported = importer.import(project.id, uri, "resume")
            val updated = project.copy(
                resumeFileName = imported.displayName,
                localResumePath = imported.localPath,
                resumeText = imported.text,
                factLedger = emptyList(),
                ruleChecks = emptyList(),
                analysis = null,
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                secondReviewFindings = emptyList(),
                stage = ProjectStage.IMPORTED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun replaceJobDescription(uri: Uri) {
        val project = _state.value.selectedProject ?: return
        runJob("正在读取岗位 JD…") {
            val imported = importer.import(project.id, uri, "jd")
            val updated = project.copy(
                jobDescriptionFileName = imported.displayName,
                localJobDescriptionPath = imported.localPath,
                jobDescription = imported.text,
                analysis = null,
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                secondReviewFindings = emptyList(),
                stage = ProjectStage.IMPORTED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun updateProjectFields(input: ProjectInput) {
        val project = _state.value.selectedProject ?: return
        val clean = input.normalized()
        val contentChanged = project.resumeText != clean.resumeText ||
            project.jobDescription != clean.jobDescription ||
            project.optimizationMode != clean.optimizationMode
        val updated = project.copy(
            title = clean.title.trim().ifBlank { clean.targetRole.trim().ifBlank { project.title } },
            clientName = clean.clientName.trim(),
            dueDate = clean.dueDate.trim(),
            orderNote = clean.orderNote.trim(),
            targetRole = clean.targetRole.trim(),
            company = clean.company.trim(),
            industry = clean.industry.trim(),
            optimizationMode = clean.optimizationMode,
            seniorityLevel = clean.seniorityLevel,
            resumeText = clean.resumeText.trim(),
            jobDescription = clean.jobDescription.trim(),
            supplements = clean.supplements.trim(),
            analysis = if (contentChanged) null else project.analysis,
            draftSections = if (contentChanged) emptyList() else project.draftSections,
            verificationChecklist = if (contentChanged) emptyList() else project.verificationChecklist,
            traceability = if (contentChanged) emptyList() else project.traceability,
            factLedger = if (contentChanged) emptyList() else project.factLedger,
            ruleChecks = if (contentChanged) emptyList() else project.ruleChecks,
            secondReviewFindings = if (contentChanged) emptyList() else project.secondReviewFindings,
            stage = if (contentChanged) ProjectStage.IMPORTED else project.stage,
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    /**
     * 自动保存正在编辑的输入，但保留已经生成的诊断和改写稿。
     * 用户切到聊天 App 询问核实问题后回来，不会因为输入尚未手动保存而丢失当前进度。
     */
    fun autosaveInputs(input: ProjectInput) {
        val project = _state.value.selectedProject ?: return
        if (project.id in deletingProjectIds) return
        val clean = input.normalized()
        val updated = project.copy(
            title = clean.title.trim().ifBlank { clean.targetRole.trim().ifBlank { project.title } },
            clientName = clean.clientName.trim(),
            dueDate = clean.dueDate.trim(),
            orderNote = clean.orderNote.trim(),
            targetRole = clean.targetRole.trim(),
            company = clean.company.trim(),
            industry = clean.industry.trim(),
            optimizationMode = clean.optimizationMode,
            seniorityLevel = clean.seniorityLevel,
            resumeText = clean.resumeText.trim(),
            jobDescription = clean.jobDescription.trim(),
            supplements = clean.supplements.trim(),
            updatedAt = System.currentTimeMillis()
        )
        if (updated != project) persist(updated, message = null)
    }

    fun autosaveSupplements(value: String) {
        val project = _state.value.selectedProject ?: return
        if (project.id in deletingProjectIds) return
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
        validateProject(project)?.let {
            showError(it)
            return
        }
        if (!hasApiKey()) {
            showError("请先在右上角设置中填写 API Key")
            return
        }
        runJob("正在提取事实并诊断简历…") {
            val facts = aiClient.extractFacts(project)
            val withFacts = project.copy(factLedger = facts)
            store.save(withFacts)
            val analysis = try {
                aiClient.analyze(withFacts)
            } catch (error: Throwable) {
                loadProjectsAndSelect(withFacts)
                throw error
            }
            val updated = withFacts.copy(
                analysis = analysis,
                ruleChecks = RuleChecker.check(withFacts.resumeText),
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                secondReviewFindings = emptyList(),
                stage = ProjectStage.ANALYZED,
                updatedAt = System.currentTimeMillis()
            )
            store.save(updated)
            loadProjectsAndSelect(updated)
        }
    }

    fun saveAndAnalyze(input: ProjectInput) {
        val project = _state.value.selectedProject ?: return
        validateInput(input)?.let {
            showError(it)
            return
        }
        if (!hasApiKey()) {
            showError("请先在右上角设置中填写 API Key")
            return
        }
        val clean = input.normalized()
        runJob("正在提取事实并诊断简历…") {
            val prepared = project.copy(
                title = clean.title.trim().ifBlank {
                    clean.targetRole.trim().ifBlank { "通用简历优化" }
                },
                clientName = clean.clientName.trim(),
                dueDate = clean.dueDate.trim(),
                orderNote = clean.orderNote.trim(),
                targetRole = clean.targetRole.trim(),
                company = clean.company.trim(),
                industry = clean.industry.trim(),
                optimizationMode = clean.optimizationMode,
                seniorityLevel = clean.seniorityLevel,
                resumeText = clean.resumeText.trim(),
                jobDescription = clean.jobDescription.trim(),
                supplements = clean.supplements.trim(),
                updatedAt = System.currentTimeMillis()
            )
            store.save(prepared)
            val facts = aiClient.extractFacts(prepared)
            val withFacts = prepared.copy(factLedger = facts)
            store.save(withFacts)
            val analysis = try {
                aiClient.analyze(withFacts)
            } catch (error: Throwable) {
                loadProjectsAndSelect(withFacts)
                throw error
            }
            val updated = withFacts.copy(
                analysis = analysis,
                ruleChecks = RuleChecker.check(withFacts.resumeText),
                draftSections = emptyList(),
                verificationChecklist = emptyList(),
                traceability = emptyList(),
                secondReviewFindings = emptyList(),
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
            val updated = createDraftVersion(project, project.supplements, "")
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
            val updated = createDraftVersion(project, supplements, "")
            loadProjectsAndSelect(updated)
        }
    }

    fun reviseDraft(instruction: String) {
        val project = _state.value.selectedProject ?: return
        if (project.analysis == null) {
            showError("请先完成简历诊断")
            return
        }
        if (instruction.isBlank()) {
            showError("请填写客户本轮的返修要求")
            return
        }
        if (!hasApiKey()) {
            showError("请先在设置中填写 API Key")
            return
        }
        runJob("正在按客户返修要求生成新版本…") {
            val updated = createDraftVersion(project, project.supplements, instruction.trim())
            loadProjectsAndSelect(updated)
        }
    }

    fun activateVersion(versionId: String) {
        val project = _state.value.selectedProject ?: return
        val version = project.versions.firstOrNull { it.id == versionId } ?: return
        val updated = project.copy(
            activeVersionId = version.id,
            factLedger = version.factLedger,
            ruleChecks = version.ruleChecks,
            analysis = version.analysis,
            draftSections = version.draftSections,
            verificationChecklist = version.verificationChecklist,
            traceability = version.traceability,
            secondReviewFindings = version.secondReviewFindings,
            secondReviewProvider = version.secondReviewProvider,
            stage = ProjectStage.DRAFTED,
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, message = null)
    }

    fun markFinalVersion(versionId: String) {
        val project = _state.value.selectedProject ?: return
        val version = project.versions.firstOrNull { it.id == versionId } ?: return
        val updated = project.copy(
            activeVersionId = version.id,
            finalVersionId = version.id,
            factLedger = version.factLedger,
            ruleChecks = version.ruleChecks,
            analysis = version.analysis,
            draftSections = version.draftSections,
            verificationChecklist = version.verificationChecklist,
            traceability = version.traceability,
            secondReviewFindings = version.secondReviewFindings,
            secondReviewProvider = version.secondReviewProvider,
            stage = ProjectStage.DRAFTED,
            updatedAt = System.currentTimeMillis()
        )
        persist(updated, null)
    }

    fun clearFinalVersion() {
        val project = _state.value.selectedProject ?: return
        persist(project.copy(finalVersionId = null, updatedAt = System.currentTimeMillis()), null)
    }

    fun updateSection(sectionId: String, content: String) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            draftSections = project.draftSections.map {
                if (it.id == sectionId) it.copy(content = content) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        persist(syncActiveVersion(updated), message = null)
    }

    fun toggleSection(sectionId: String) {
        val project = _state.value.selectedProject ?: return
        val updated = project.copy(
            draftSections = project.draftSections.map {
                if (it.id == sectionId) it.copy(accepted = !it.accepted) else it
            },
            updatedAt = System.currentTimeMillis()
        )
        persist(syncActiveVersion(updated), message = null)
    }

    fun moveSection(sectionId: String, delta: Int) {
        val project = _state.value.selectedProject ?: return
        val sections = project.draftSections.toMutableList()
        val index = sections.indexOfFirst { it.id == sectionId }
        val target = index + delta
        if (index < 0 || target !in sections.indices) return
        val item = sections.removeAt(index)
        sections.add(target, item)
        persist(
            syncActiveVersion(
                project.copy(draftSections = sections, updatedAt = System.currentTimeMillis())
            ),
            null
        )
    }

    fun exportProject() {
        val project = _state.value.selectedProject ?: return
        if (project.finalVersionId != null && project.finalVersionId != project.activeVersionId) {
            showError("当前查看的不是最终交付版，请先在版本历史中恢复最终版")
            return
        }
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
        deleteProjectById(project.id)
    }

    fun deleteProjectById(projectId: String) {
        if (_state.value.busyMessage != null) {
            showError("当前正在处理任务，请完成后再删除")
            return
        }
        deletingProjectIds += projectId
        runJob("正在删除任务和原始文件…") {
            try {
                store.delete(projectId)
                val projects = store.list()
                _state.update {
                    it.copy(
                        projects = projects,
                        selectedProject = it.selectedProject?.takeUnless { project -> project.id == projectId },
                        lastExport = if (it.selectedProject?.id == projectId) null else it.lastExport,
                        busyMessage = null
                    )
                }
            } finally {
                deletingProjectIds -= projectId
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearExport() {
        _state.update { it.copy(lastExport = null) }
    }

    private suspend fun createDraftVersion(
        project: ResumeProject,
        supplements: String,
        instruction: String
    ): ResumeProject {
        val prepared = project.copy(
            supplements = supplements.trim(),
            updatedAt = System.currentTimeMillis()
        )
        val withFacts = if (
            prepared.supplements != project.supplements || prepared.factLedger.isEmpty()
        ) {
            prepared.copy(factLedger = aiClient.extractFacts(prepared))
        } else {
            prepared
        }
        store.save(withFacts)
        val audit = aiClient.rewrite(withFacts, instruction)
        val firstPass = appendVersion(
            withFacts.copy(
                draftSections = audit.sections,
                verificationChecklist = audit.verificationChecklist,
                traceability = audit.traceability,
                secondReviewFindings = emptyList(),
                secondReviewProvider = "",
                stage = ProjectStage.DRAFTED,
                updatedAt = System.currentTimeMillis()
            ),
            instruction
        )
        store.save(firstPass)
        val reviewFindings = try {
            reviewWithSecondModelIfEnabled(firstPass, audit.sections)
        } catch (error: Throwable) {
            loadProjectsAndSelect(firstPass)
            throw error
        }
        val updated = syncActiveVersion(
            firstPass.copy(
                verificationChecklist = audit.verificationChecklist + reviewFindings.map {
                    "第二模型审核：$it"
                },
                secondReviewFindings = reviewFindings,
                secondReviewProvider = reviewProviderLabel(),
                updatedAt = System.currentTimeMillis()
            )
        )
        store.save(updated)
        return updated
    }

    private fun appendVersion(project: ResumeProject, instruction: String): ResumeProject {
        val version = ProjectVersion(
            label = "V${project.versions.size + 1}",
            instruction = instruction,
            factLedger = project.factLedger,
            ruleChecks = project.ruleChecks,
            analysis = project.analysis,
            draftSections = project.draftSections,
            verificationChecklist = project.verificationChecklist,
            traceability = project.traceability,
            secondReviewFindings = project.secondReviewFindings,
            secondReviewProvider = project.secondReviewProvider
        )
        return project.copy(
            versions = project.versions + version,
            activeVersionId = version.id
        )
    }

    private suspend fun migrateLegacyDraft(project: ResumeProject): ResumeProject {
        if (project.versions.isNotEmpty() || project.draftSections.isEmpty()) return project
        val version = ProjectVersion(
            label = "V1",
            instruction = "旧版本任务自动迁移",
            factLedger = project.factLedger,
            ruleChecks = project.ruleChecks,
            analysis = project.analysis,
            draftSections = project.draftSections,
            verificationChecklist = project.verificationChecklist,
            traceability = project.traceability,
            secondReviewFindings = project.secondReviewFindings,
            secondReviewProvider = project.secondReviewProvider
        )
        val migrated = project.copy(versions = listOf(version), activeVersionId = version.id)
        store.save(migrated)
        return migrated
    }

    private fun syncActiveVersion(project: ResumeProject): ResumeProject {
        val activeId = project.activeVersionId ?: return project
        return project.copy(
            versions = project.versions.map { version ->
                if (version.id != activeId) {
                    version
                } else {
                    version.copy(
                        factLedger = project.factLedger,
                        ruleChecks = project.ruleChecks,
                        analysis = project.analysis,
                        draftSections = project.draftSections,
                        verificationChecklist = project.verificationChecklist,
                        traceability = project.traceability,
                        secondReviewFindings = project.secondReviewFindings,
                        secondReviewProvider = project.secondReviewProvider
                    )
                }
            }
        )
    }

    private suspend fun reviewWithSecondModelIfEnabled(
        project: ResumeProject,
        sections: List<DraftSection>
    ): List<String> {
        val settings = secureSettings.readAiSettings()
        if (!settings.dualReviewEnabled) return emptyList()
        require(secureSettings.hasApiKey(settings.activeReviewProvider)) {
            "双模型审核已开启，但审核 AI 还没有保存 API Key"
        }
        return aiClient.reviewDraft(project, sections)
    }

    private fun reviewProviderLabel(): String {
        val settings = secureSettings.readAiSettings()
        if (!settings.dualReviewEnabled) return ""
        return settings.providers.firstOrNull { it.id == settings.activeReviewProvider }?.label.orEmpty()
    }

    private fun ProjectInput.normalized(): ProjectInput = copy(
        targetRole = if (optimizationMode == OptimizationMode.GENERAL) "" else targetRole,
        company = if (optimizationMode == OptimizationMode.GENERAL) "" else company,
        jobDescription = if (optimizationMode == OptimizationMode.TARGETED) jobDescription else ""
    )

    private fun validateInput(
        input: ProjectInput,
        requireResumeText: Boolean = true,
        requireJobDescription: Boolean = true
    ): String? {
        if (requireResumeText && input.resumeText.isBlank()) return "简历内容不能为空"
        return when (input.optimizationMode) {
            OptimizationMode.TARGETED -> when {
                input.targetRole.isBlank() -> "特定岗位模式需要填写目标岗位"
                requireJobDescription && input.jobDescription.isBlank() ->
                    "特定岗位模式需要填写岗位 JD"
                else -> null
            }
            OptimizationMode.ROLE_ONLY ->
                if (input.targetRole.isBlank()) "只有岗位方向模式需要填写目标岗位" else null
            OptimizationMode.GENERAL -> null
        }
    }

    private fun validateProject(project: ResumeProject): String? = validateInput(
        ProjectInput(
            targetRole = project.targetRole,
            optimizationMode = project.optimizationMode,
            resumeText = project.resumeText,
            jobDescription = project.jobDescription
        )
    )

    private fun persist(project: ResumeProject, message: String?) {
        viewModelScope.launch {
            store.save(project)
            val projects = store.list()
            _state.update {
                it.copy(
                    projects = projects,
                    selectedProject = project,
                    lastExport = null,
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
                lastExport = null,
                busyMessage = null,
                error = null
            )
        }
    }

    private fun runJob(message: String, block: suspend () -> Unit) {
        if (_state.value.busyMessage != null) return
        val application = getApplication<Application>()
        runCatching { ProcessingService.start(application, message) }
        viewModelScope.launch {
            _state.update { it.copy(busyMessage = message, error = null) }
            try {
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
            } finally {
                ProcessingService.stop(application)
            }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
