package com.irlachievements.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.irlachievements.app.AppContainer
import com.irlachievements.app.background.StepWorkScheduler
import com.irlachievements.app.background.UsageWorkScheduler
import com.irlachievements.app.domain.achievements.*
import com.irlachievements.app.domain.model.AchievementDefinition
import com.irlachievements.app.domain.model.AchievementState
import com.irlachievements.app.domain.model.PlayerSummary
import com.irlachievements.app.data.repository.RecordingCapability
import com.irlachievements.app.data.repository.ChallengeAttemptSession
import com.irlachievements.app.data.repository.DEFAULT_PLAYER_NAME
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.RunStatus
import com.irlachievements.app.domain.model.Capability
import com.irlachievements.app.domain.model.LogoCollection
import com.irlachievements.app.domain.model.LogoStyle
import com.irlachievements.app.data.repository.LauncherIconController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class AppUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val playerName: String = DEFAULT_PLAYER_NAME,
    val soundEnabled: Boolean = false,
    val selectedLogoId: String = LogoStyle.NEON_CORE.id,
    val logoChangeError: String? = null,
    val pendingLogoRewards: List<LogoStyle> = emptyList(),
    val states: Map<String, AchievementState> = emptyMap(),
    val summary: PlayerSummary = PlayerSummary(0, 0, AchievementCatalog.all.size, 0, 0, 0),
    val latestUnlock: AchievementDefinition? = null,
    val celebration: AchievementDefinition? = null,
    val stepsToday: Long = 0,
    val distanceTodayMeters: Double = 0.0,
    val stepCapability: RecordingCapability = RecordingCapability.UNKNOWN,
    val lastStepSyncAt: Long? = null,
    val stepSyncInProgress: Boolean = false,
    val stepSyncError: String? = null,
    val challengeRun: ChallengeRunState = ChallengeRunState(),
    val unavailableChallengeCapabilities: Set<Capability> = emptySet(),
    val activityTrackingActive: Boolean = false,
    val activityTrackingError: String? = null,
    val usageSyncInProgress: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val usageSyncError: String? = null,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var openRecorded = false
    private var currentChallengeAttempt: ChallengeAttemptSession? = null
    private var challengeStartPending = false
    private var challengeGeneration = 0
    private var activityRegisteredThisProcess = false
    private val launcherIcons = LauncherIconController(container.applicationContext)
    private var logoPreferenceLoaded = false
    private var achievementsLoaded = false
    private var appliedLauncherStyle: LogoStyle? = null
    private var seenLogoRewards: Set<String>? = null

    init {
        viewModelScope.launch {
            container.preferencesRepository.seenLogoRewards.collect {
                seenLogoRewards = it
                syncLogoRewards()
            }
        }
        viewModelScope.launch {
            container.preferencesRepository.selectedLogo.collect { id ->
                _uiState.value = _uiState.value.copy(selectedLogoId = id)
                logoPreferenceLoaded = true
                syncLauncherLogo()
            }
        }
        viewModelScope.launch {
            container.preferencesRepository.soundEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(soundEnabled = enabled)
            }
        }
        _uiState.value = _uiState.value.copy(
            unavailableChallengeCapabilities = container.challengeSensorRepository.unavailableCapabilities(),
        )
        viewModelScope.launch {
            container.preferencesRepository.onboardingComplete.collect { complete ->
                _uiState.value = _uiState.value.copy(onboardingComplete = complete, loading = false)
                if (complete && !openRecorded) {
                    openRecorded = true
                    recordOpen()
                    syncSteps(schedulePeriodic = true)
                    registerActivityTransitions()
                    syncUsage()
                }
            }
        }
        viewModelScope.launch {
            container.preferencesRepository.playerName.collect { name ->
                _uiState.value = _uiState.value.copy(playerName = name)
            }
        }
        viewModelScope.launch {
            container.achievementRepository.observeStates().collect { states ->
                val stateMap = states.associateBy { it.achievementId }
                val latest = states.filter { it.unlockedAt != null }.maxByOrNull { it.unlockedAt ?: 0L }
                    ?.let { AchievementCatalog.byId[it.achievementId] }
                val summary = container.achievementEngine.summary()
                _uiState.value = _uiState.value.copy(
                    states = stateMap,
                    summary = summary,
                    latestUnlock = latest,
                )
                achievementsLoaded = true
                syncLogoRewards()
                syncLauncherLogo()
            }
        }
        viewModelScope.launch {
            container.stepRepository.observeSnapshot().collect { snapshot ->
                _uiState.value = _uiState.value.copy(
                    stepsToday = snapshot.stepsToday,
                    distanceTodayMeters = snapshot.distanceTodayMeters,
                    stepCapability = snapshot.capability,
                    lastStepSyncAt = snapshot.lastSyncAt,
                )
            }
        }
        viewModelScope.launch {
            container.challengeSensorRepository.state.collect { run ->
                _uiState.value = _uiState.value.copy(challengeRun = run)
                if (run.status == RunStatus.UNSUPPORTED) {
                    currentChallengeAttempt?.let { attempt ->
                        currentChallengeAttempt = null
                        container.challengeAttemptRepository.cancel(attempt)
                    }
                    return@collect
                }
                if (run.status !in setOf(RunStatus.SUCCESS, RunStatus.FAILED)) return@collect
                val attempt = currentChallengeAttempt ?: return@collect
                if (attempt.challengeId != run.challengeId) return@collect
                currentChallengeAttempt = null
                val finishedAt = System.currentTimeMillis()
                val completed = run.status == RunStatus.SUCCESS
                container.challengeAttemptRepository.finish(attempt, finishedAt, run)
                if (completed) {
                    val dates = container.challengeAttemptRepository.distinctCompletionDates(attempt.challengeId)
                    val battery = container.batteryRepository.current()?.percent ?: 100
                    dispatch(
                        ChallengeCompleted(
                            challengeId = attempt.challengeId,
                            attemptNumber = attempt.attemptNumber,
                            previousFailures = attempt.previousFailures,
                            batteryPercent = battery,
                            distinctCompletionDates = dates,
                            reactionMs = run.reactionMs,
                            angularError = run.angularError,
                            timingDeviationPercent = run.timingDeviationPercent,
                            extraMovements = run.extraMovements,
                            occurredAt = LocalDateTime.now(),
                        ),
                    )
                } else {
                    val repeated = container.challengeAttemptRepository.repeatedFailureCount(attempt.challengeId, run.failureCode)
                    dispatch(
                        ChallengeFailed(
                            challengeId = attempt.challengeId,
                            totalFailures = attempt.previousFailures + 1,
                            failureCode = run.failureCode,
                            repeatedFailureCount = repeated,
                            completedSteps = run.step,
                            totalSteps = run.totalSteps,
                            occurredAt = LocalDateTime.now(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun recordOpen() {
        val now = LocalDateTime.now()
        val stats = container.preferencesRepository.recordAppOpen(now)
        val battery = container.batteryRepository.current()
        val unlockedBeforeOpen = container.achievementEngine.summary().achievementsUnlocked
        dispatch(
            AppOpened(
                openCountToday = stats.openCountToday,
                distinctUsageDays = stats.distinctUsageDays,
                daysSinceInstall = stats.daysSinceInstall,
                daysSinceLastOpen = stats.daysSinceLastOpen,
                batteryPercent = battery?.percent,
                isCharging = battery?.isCharging ?: false,
                powerSource = battery?.powerSource ?: PowerSource.NONE,
                achievementsUnlocked = unlockedBeforeOpen,
                sameClockMinuteDistinctDates = stats.sameClockMinuteDistinctDates,
                occurredAt = now,
            ),
        )
        battery?.let { dispatch(BatteryObserved(it.percent, now)) }
    }

    fun completeOnboarding() = viewModelScope.launch { container.preferencesRepository.completeOnboarding() }

    fun savePlayerName(name: String) = viewModelScope.launch {
        container.preferencesRepository.savePlayerName(name)
    }

    fun selectLogo(style: LogoStyle) = viewModelScope.launch {
        if (LogoCollection.evaluate(_uiState.value.states).any { it.style == style && it.unlocked }) {
            runCatching { container.preferencesRepository.saveSelectedLogo(style.id) }
                .onFailure { _uiState.value = _uiState.value.copy(logoChangeError = "No se pudo guardar el logo. Inténtalo de nuevo.") }
        }
    }

    fun retryLauncherLogo() { appliedLauncherStyle = null; syncLauncherLogo() }

    fun dismissLogoReward(style: LogoStyle) = viewModelScope.launch {
        runCatching { container.preferencesRepository.acknowledgeLogoReward(style.id) }
            .onFailure { _uiState.value = _uiState.value.copy(logoChangeError = "No se pudo guardar la confirmación de la recompensa.") }
    }

    fun equipLogoReward(style: LogoStyle) = viewModelScope.launch {
        if (LogoCollection.evaluate(_uiState.value.states).none { it.style == style && it.unlocked }) return@launch
        runCatching {
            container.preferencesRepository.saveSelectedLogo(style.id)
            container.preferencesRepository.acknowledgeLogoReward(style.id)
        }.onFailure { _uiState.value = _uiState.value.copy(logoChangeError = "No se pudo guardar el logo. Puedes reintentarlo en Ajustes.") }
    }

    private fun syncLogoRewards() {
        val seen = seenLogoRewards ?: return
        if (!achievementsLoaded) return
        _uiState.value = _uiState.value.copy(pendingLogoRewards = LogoCollection.pendingRewards(_uiState.value.states, seen))
    }

    private fun syncLauncherLogo() {
        if (!logoPreferenceLoaded || !achievementsLoaded) return
        val style = LogoCollection.resolveSelection(_uiState.value.selectedLogoId, _uiState.value.states)
        if (style == appliedLauncherStyle) return
        launcherIcons.apply(style).onSuccess {
            appliedLauncherStyle = style
            _uiState.value = _uiState.value.copy(logoChangeError = null)
        }.onFailure {
            _uiState.value = _uiState.value.copy(logoChangeError = "El logo está elegido, pero Android no pudo actualizar el icono del escritorio. Puedes reintentarlo.")
        }
    }

    fun onPhysicalPermissionResult(granted: Boolean) {
        if (granted) {
            syncSteps(schedulePeriodic = true)
            registerActivityTransitions()
        }
        else _uiState.value = _uiState.value.copy(stepCapability = RecordingCapability.PERMISSION_REQUIRED)
    }

    fun onForeground() {
        if (!_uiState.value.onboardingComplete) return
        syncSteps(schedulePeriodic = true)
        registerActivityTransitions()
        syncUsage()
    }

    fun syncUsage() {
        if (_uiState.value.usageSyncInProgress) return
        val hasAccess = container.usageRepository.hasUsageAccess()
        if (!hasAccess) {
            _uiState.value = _uiState.value.copy(usageAccessGranted = false, usageSyncError = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(usageSyncInProgress = true, usageAccessGranted = true, usageSyncError = null)
            container.usageRepository.reconcile().fold(
                onSuccess = {
                    UsageWorkScheduler.schedule(container.applicationContext)
                    _uiState.value = _uiState.value.copy(usageSyncInProgress = false, usageAccessGranted = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        usageSyncInProgress = false,
                        usageAccessGranted = container.usageRepository.hasUsageAccess(),
                        usageSyncError = error.message ?: "No se pudo reconciliar el uso del dispositivo",
                    )
                },
            )
        }
    }

    private fun registerActivityTransitions() {
        if (activityRegisteredThisProcess) return
        viewModelScope.launch {
            container.activityRepository.registerTransitions().fold(
                onSuccess = {
                    activityRegisteredThisProcess = true
                    _uiState.value = _uiState.value.copy(activityTrackingActive = true, activityTrackingError = null)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        activityTrackingActive = false,
                        activityTrackingError = if (error is SecurityException) null else error.message ?: "No se pudo activar el reconocimiento de actividad",
                    )
                },
            )
        }
    }

    fun onBackground() {
        // Stop hardware even if the repository's first RUNNING emission has not reached UI yet.
        cancelChallenge()
    }

    fun startChallenge(challengeId: String) {
        if (challengeStartPending || _uiState.value.challengeRun.status == RunStatus.RUNNING) return
        challengeStartPending = true
        val generation = ++challengeGeneration
        viewModelScope.launch {
            try {
                if (!container.challengeSensorRepository.isPlayable(challengeId)) {
                    container.challengeSensorRepository.start(challengeId)
                    return@launch
                }
                val attempt = container.challengeAttemptRepository.start(challengeId, System.currentTimeMillis())
                if (generation != challengeGeneration) {
                    container.challengeAttemptRepository.cancel(attempt)
                    return@launch
                }
                currentChallengeAttempt = attempt
                if (!container.challengeSensorRepository.start(challengeId)) {
                    currentChallengeAttempt = null
                    container.challengeAttemptRepository.cancel(attempt)
                }
            } finally {
                if (generation == challengeGeneration) challengeStartPending = false
            }
        }
    }

    fun tapChallenge(tile: Int) {
        container.challengeSensorRepository.tap(tile)
    }

    fun cancelChallenge() {
        challengeGeneration++
        challengeStartPending = false
        val attempt = currentChallengeAttempt
        currentChallengeAttempt = null
        container.challengeSensorRepository.stop()
        _uiState.value = _uiState.value.copy(challengeRun = ChallengeRunState())
        if (attempt != null) viewModelScope.launch { container.challengeAttemptRepository.cancel(attempt) }
    }

    override fun onCleared() {
        container.challengeSensorRepository.stop()
        super.onCleared()
    }

    fun syncSteps(schedulePeriodic: Boolean = false) {
        if (_uiState.value.stepSyncInProgress) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stepSyncInProgress = true, stepSyncError = null)
            container.stepSyncCoordinator.sync().fold(
                onSuccess = { snapshot ->
                    if (schedulePeriodic && snapshot.capability in setOf(RecordingCapability.FULL, RecordingCapability.STEPS_ONLY)) {
                        StepWorkScheduler.schedule(container.applicationContext)
                    }
                    _uiState.value = _uiState.value.copy(stepSyncInProgress = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        stepSyncInProgress = false,
                        stepCapability = if (error is SecurityException) RecordingCapability.PERMISSION_REQUIRED else _uiState.value.stepCapability,
                        stepSyncError = if (error is SecurityException) null else error.message ?: "No se pudo sincronizar",
                    )
                },
            )
        }
    }


    fun clearCelebration() {
        _uiState.value = _uiState.value.copy(celebration = null)
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        container.preferencesRepository.saveSoundEnabled(enabled)
    }

    private suspend fun dispatch(event: AchievementEvent) {
        val result = container.achievementEngine.process(event)
        result.newlyUnlocked.lastOrNull()?.let { unlocked ->
            _uiState.value = _uiState.value.copy(celebration = unlocked)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
