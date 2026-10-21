@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.irlachievements.app.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.irlachievements.app.R
import com.irlachievements.app.data.repository.MAX_PLAYER_NAME_LENGTH
import com.irlachievements.app.domain.achievements.*
import com.irlachievements.app.domain.model.*
import com.irlachievements.app.domain.challenges.ChallengeGameEngine
import com.irlachievements.app.domain.challenges.ChallengeDiscovery
import com.irlachievements.app.domain.challenges.ChallengeKind
import com.irlachievements.app.domain.challenges.TouchGameEngine
import com.irlachievements.app.domain.challenges.MicrophoneGameEngine
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.RunStatus
import com.irlachievements.app.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    ACHIEVEMENTS("Logros", Icons.Default.EmojiEvents),
    CHALLENGES("Retos", Icons.Default.SportsEsports),
    SETTINGS("Ajustes", Icons.Default.Settings),
    CREDITS("Créditos", Icons.Default.People),
    LOGOS("Logos", Icons.Default.AutoAwesome),
}

@Composable
fun IrlAchievementsApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.HOME.name
    var splashFinished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(650)
        splashFinished = true
    }
    val appLifecycleOwner = LocalLifecycleOwner.current
    val navigateTo: (Destination) -> Unit = { destination ->
        if (destination != Destination.CHALLENGES) {
            viewModel.cancelChallenge()
        }
        if (destination == Destination.HOME) {
            if (!navController.popBackStack(Destination.HOME.name, inclusive = false) && currentRoute != Destination.HOME.name) {
                navController.navigate(Destination.HOME.name) { launchSingleTop = true }
            }
        } else {
            navController.navigate(destination.name) {
                popUpTo(Destination.HOME.name) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    DisposableEffect(appLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.onForeground()
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackground()
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { appLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(
        enabled = state.onboardingComplete &&
            currentRoute != Destination.HOME.name && state.challengeRun.status == RunStatus.IDLE,
    ) { navigateTo(if (currentRoute == Destination.LOGOS.name) Destination.SETTINGS else Destination.HOME) }

    val activeLogo = remember(state.selectedLogoId, state.states) { LogoCollection.resolveSelection(state.selectedLogoId, state.states) }
    val rewardToShow = state.pendingLogoRewards.firstOrNull()?.takeIf {
        splashFinished && !state.loading && state.onboardingComplete &&
            state.challengeRun.status == RunStatus.IDLE && state.celebration == null
    }
    GameFeedback(state, rewardToShow?.id)
    CompositionLocalProvider(LocalQuestifyLogo provides activeLogo) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        when {
            state.loading || !splashFinished -> BrandSplashScreen()
            !state.onboardingComplete -> OnboardingScreen(viewModel::completeOnboarding)
            else -> Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                        Destination.entries.filter { it != Destination.CREDITS && it != Destination.LOGOS }.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.name || (currentRoute == Destination.LOGOS.name && item == Destination.SETTINGS),
                                onClick = { navigateTo(item) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink,
                                    indicatorColor = Acid,
                                    selectedTextColor = Acid,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                NavHost(navController = navController, startDestination = Destination.HOME.name, modifier = Modifier.padding(padding)) {
                    composable(Destination.HOME.name) {
                        HomeScreen(
                            state,
                            { navigateTo(Destination.ACHIEVEMENTS) },
                            { navigateTo(Destination.CHALLENGES) },
                            { viewModel.syncSteps() },
                            onCredits = { navigateTo(Destination.CREDITS) },
                            onLogos = { navigateTo(Destination.LOGOS) },
                        )
                    }
                    composable(Destination.ACHIEVEMENTS.name) {
                        AchievementsScreen(state, onHome = { navigateTo(Destination.HOME) })
                    }
                    composable(Destination.CHALLENGES.name) {
                        ChallengesScreen(
                            state,
                            viewModel::startChallenge,
                            viewModel::cancelChallenge,
                            onHome = { navigateTo(Destination.HOME) },
                            onTouch = viewModel::tapChallenge,
                        )
                    }
                    composable(Destination.SETTINGS.name) {
                        SettingsScreen(
                            state = state,
                            onPhysicalPermissionResult = viewModel::onPhysicalPermissionResult,
                            onSavePlayerName = viewModel::savePlayerName,
                            onSoundEnabled = viewModel::setSoundEnabled,
                            onLogos = { navigateTo(Destination.LOGOS) },
                            onHome = { navigateTo(Destination.HOME) },
                        )
                    }
                    composable(Destination.CREDITS.name) {
                        CreditsScreen(onHome = { navigateTo(Destination.HOME) })
                    }
                    composable(Destination.LOGOS.name) {
                        LogoCollectionScreen(state, viewModel::selectLogo, viewModel::retryLauncherLogo,
                            onBack = { navigateTo(Destination.SETTINGS) })
                    }
                }
            }
        }

        if (splashFinished && !state.loading && state.onboardingComplete) {
            AchievementCelebration(state.celebration, viewModel::clearCelebration)
        }
        rewardToShow?.let { logo ->
            LogoRewardDialog(logo,
                onEquip = { viewModel.equipLogoReward(logo) },
                onDismiss = { viewModel.dismissLogoReward(logo) },
                error = state.logoChangeError)
        }
    }
    }
}

private data class OnboardingPage(val eyebrow: String, val title: String, val body: String, val icon: ImageVector, val color: Color)

@Composable
private fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage("QUESTIFY · TU VIDA. TU PARTIDA.", "Tu vida sube de nivel.", "Convierte cosas que haces en el mundo real en achievements que viven solo en tu teléfono.", Icons.Default.AutoAwesome, Acid),
            OnboardingPage("ACHIEVEMENTS", "El progreso sucede fuera de la pantalla.", "Pasos, constancia y momentos reales se convierten en XP. Sin cuentas. Sin nube.", Icons.Default.EmojiEvents, Gold),
            OnboardingPage("SECRETS", "Hay cosas que no se pueden buscar.", "Los logros ocultos no revelan su nombre ni condición. Tendrás que encontrarlos viviendo.", Icons.Default.QuestionMark, Violet),
            OnboardingPage("CHALLENGES", "Cuando quieras jugar, pulsa iniciar.", "Los sensores solo se activarán durante un reto. Al salir, se detienen. Siempre bajo tu control.", Icons.Default.SportsEsports, Cyan),
        )
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(pages[pager.currentPage].color.copy(alpha = .18f), Ink), radius = 1_200f),
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("QUESTIFY", color = Acid, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                if (pager.currentPage < pages.lastIndex) TextButton(onClick = onComplete) { Text("Saltar", color = Muted) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.size(104.dp).clip(RoundedCornerShape(30.dp))
                            .background(page.color.copy(alpha = .14f)).border(1.dp, page.color.copy(alpha = .5f), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(page.icon, null, tint = page.color, modifier = Modifier.size(50.dp)) }
                    Spacer(Modifier.height(42.dp))
                    Text(page.eyebrow, color = page.color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(page.title, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.height(18.dp))
                    Text(page.body, fontSize = 17.sp, lineHeight = 25.sp, color = Muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    pages.indices.forEach { index ->
                        Box(Modifier.height(5.dp).width(if (pager.currentPage == index) 28.dp else 8.dp).clip(CircleShape).background(if (pager.currentPage == index) pages[index].color else PanelRaised))
                    }
                }
                Button(
                    onClick = {
                        if (pager.currentPage == pages.lastIndex) onComplete()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = pages[pager.currentPage].color, contentColor = Ink),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (pager.currentPage == pages.lastIndex) "Entrar" else "Siguiente", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(eyebrow: String, title: String, subtitle: String? = null, onHome: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(PanelRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    QuestifyLogo(Modifier.size(27.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("QUESTIFY", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
                    Text(eyebrow, color = Acid, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onHome != null) {
                FilledTonalIconButton(
                    onClick = onHome,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = PanelRaised, contentColor = Acid),
                ) { Icon(Icons.Default.Home, "Volver al inicio") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        subtitle?.let { Text(it, color = Muted, fontSize = 14.sp) }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, onAchievements: () -> Unit, onChallenges: () -> Unit, onSync: () -> Unit, onCredits: () -> Unit, onLogos: () -> Unit, modifier: Modifier = Modifier) {
    val xpLevel = remember(state.summary.totalXp) { XpProgression.from(state.summary.totalXp) }
    val rankAccent = rankAccent(xpLevel.level)
    var showXpDetails by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader(state.playerName.uppercase(), "Tu progreso", "Todo permanece en este dispositivo") }
        item { NextRewardCard(state, onLogos) }
        item {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(rankAccent.copy(alpha = .19f), Panel, Ink)))
                    .border(rankFrameWidth(xpLevel.level), rankAccent.copy(alpha = .62f), RoundedCornerShape(28.dp))
                    .clickable { showXpDetails = true }.padding(22.dp),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("NIVEL ${xpLevel.level} · ${xpLevel.title.uppercase()}", color = rankAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text("%,d XP".format(state.summary.totalXp), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        }
                        Box(Modifier.size(58.dp).clip(CircleShape).background(rankAccent), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${xpLevel.level}", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                Text("NIVEL", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    LinearProgressIndicator(
                        progress = { xpLevel.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = rankAccent,
                        trackColor = Color.White.copy(alpha = .1f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${xpLevel.xpToNextLevel} XP para nivel ${xpLevel.level + 1}", color = rankAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${state.summary.achievementsUnlocked}/${state.summary.totalAchievements} logros", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .055f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.MilitaryTech, null, tint = rankAccent, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(horizontal = 10.dp).weight(1f)) {
                            Text("${xpLevel.reward} activo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("La XP sube tu nivel, rango y marco; nunca se gasta.", color = Muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.Info, "Cómo funciona la XP", tint = rankAccent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("HOY", "%,d".format(state.stepsToday), "pasos", Icons.AutoMirrored.Filled.DirectionsWalk, Cyan, Modifier.weight(1f))
                StatTile("SECRETOS", state.summary.hiddenUnlocked.toString(), "encontrados", Icons.Default.Key, Violet, Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Sync, null, tint = if (state.stepSyncError == null) Cyan else MaterialTheme.colorScheme.error)
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text(
                        when (state.stepCapability) {
                            com.irlachievements.app.data.repository.RecordingCapability.FULL -> "Pasos y distancia activos"
                            com.irlachievements.app.data.repository.RecordingCapability.STEPS_ONLY -> "Pasos activos · distancia no disponible"
                            com.irlachievements.app.data.repository.RecordingCapability.PERMISSION_REQUIRED -> "Activa Actividad física para contar pasos"
                            com.irlachievements.app.data.repository.RecordingCapability.UNAVAILABLE -> "Recording API no disponible"
                            else -> "Preparando seguimiento local"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(
                        state.stepSyncError ?: "Hoy: ${"%.2f".format(state.distanceTodayMeters / 1_000.0)} km · sincronización local",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = onSync, enabled = !state.stepSyncInProgress) {
                    if (state.stepSyncInProgress) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Cyan)
                    else Icon(Icons.Default.Refresh, "Sincronizar", tint = Cyan)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            state.latestUnlock?.let { latest ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("ÚLTIMO DESBLOQUEO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    AchievementMiniCard(latest)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Explorar logros", "Mira el siguiente objetivo sin descubrir los secretos.", Icons.Default.EmojiEvents, Acid, onAchievements)
                QuickAction("Entrar a Challenges", "Retos activos, cortos y bajo tu control.", Icons.Default.SportsEsports, Cyan, onChallenges)
                OutlinedButton(onClick = onCredits, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.People, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Créditos")
                }
            }
        }
    }
    if (showXpDetails) {
        XpInfoDialog(xpLevel = xpLevel, accent = rankAccent, onDismiss = { showXpDetails = false })
    }
}

@Composable
private fun XpInfoDialog(xpLevel: XpLevel, accent: Color, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        icon = { Icon(Icons.Default.MilitaryTech, null, tint = accent, modifier = Modifier.size(34.dp)) },
        title = { Text("Tu XP sí tiene efecto", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cada logro suma XP permanente. No es una moneda: nunca se consume ni hace retroceder tu nivel.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                XpPurposeRow("Ahora", "Nivel ${xpLevel.level} · ${xpLevel.title}", accent)
                XpPurposeRow("Marco activo", xpLevel.reward, accent)
                if (xpLevel.nextRankTitle != null && xpLevel.nextRankLevel != null && xpLevel.xpToNextRank != null) {
                    XpPurposeRow("Próximo rango", "${xpLevel.nextRankTitle} · nivel ${xpLevel.nextRankLevel} · faltan ${xpLevel.xpToNextRank} XP", accent)
                } else {
                    XpPurposeRow("Rango máximo", "Has alcanzado Mito", accent)
                }
                Text("Todos los retos siguen disponibles desde el inicio: la XP recompensa tu progreso sin bloquear contenido.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido", color = accent, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun XpPurposeRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, color.copy(alpha = .18f), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.height(18.dp))
        Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(unit, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun QuickAction(title: String, body: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = color)
    }
}

private enum class AchievementFilter(val label: String) { ALL("Todos"), UNLOCKED("Desbloqueados"), LOCKED("Bloqueados"), HIDDEN("Ocultos") }

@Composable
private fun AchievementsScreen(state: AppUiState, onHome: () -> Unit, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(AchievementFilter.ALL) }
    val achievements = remember(state.states, filter) {
        AchievementCatalog.all.filter { it.category != AchievementCategory.CHALLENGE }.filter { definition ->
            val unlocked = state.states[definition.id]?.unlocked == true
            when (filter) {
                AchievementFilter.ALL -> true
                AchievementFilter.UNLOCKED -> unlocked
                AchievementFilter.LOCKED -> !unlocked
                AchievementFilter.HIDDEN -> definition.hidden
            }
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("COLLECTION", "Logros", "${state.summary.visibleUnlocked} visibles · ${state.summary.hiddenUnlocked} secretos", onHome) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AchievementFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Acid, selectedLabelColor = Ink, containerColor = Panel, labelColor = Muted),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == item, borderColor = PanelRaised, selectedBorderColor = Acid),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        items(achievements, key = { it.id }) { definition ->
            AchievementCard(definition, state.states[definition.id], Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun AchievementCard(definition: AchievementDefinition, state: AchievementState?, modifier: Modifier = Modifier) {
    val unlocked = state?.unlocked == true
    val secret = definition.hidden && !unlocked
    val color = rarityColor(definition.rarity)
    val progress = if (definition.target <= 0) 0f else ((state?.currentProgress ?: 0.0) / definition.target).coerceIn(0.0, 1.0).toFloat()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (unlocked) color.copy(alpha = .09f) else Panel)
            .border(1.dp, if (unlocked) color.copy(alpha = .45f) else PanelRaised, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(if (unlocked) color.copy(alpha = .18f) else PanelRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (secret) Icons.Default.QuestionMark else achievementIcon(definition), null, tint = if (unlocked) color else Muted)
            if (!secret && !unlocked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(17.dp).clip(CircleShape).background(Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Lock, null, tint = Muted, modifier = Modifier.size(11.dp))
                }
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (secret) "Logro oculto" else definition.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (secret) "SECRET" else "+${definition.rarity.xp} XP", color = if (secret) Violet else color, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text(if (secret) "Continúa explorando para descubrirlo." else definition.description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            if (!secret && !unlocked && definition.target > 1) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress }, color = color, trackColor = PanelRaised, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
                Spacer(Modifier.height(5.dp))
                Text("${formatProgress(state?.currentProgress ?: 0.0)} / ${formatProgress(definition.target)}", color = Muted, fontSize = 10.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                Text(if (unlocked) "DESBLOQUEADO · ${definition.rarity.name}" else definition.rarity.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            if (!unlocked && definition.requiredCapability == Capability.USAGE_ACCESS) {
                Text("Requiere permiso opcional: Acceso de uso", color = Gold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AchievementMiniCard(definition: AchievementDefinition) {
    val color = rarityColor(definition.rarity)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .1f)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Icon(achievementIcon(definition), null, tint = Ink) }
        Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
            Text(definition.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(definition.description, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("+${definition.rarity.xp}", color = color, fontWeight = FontWeight.Black)
    }
}

private enum class ChallengeSensorFilter(val label: String) {
    ALL("Todos"),
    GYROSCOPE("Giroscopio"),
    ACCELEROMETER("Acelerómetro"),
    ORIENTATION("Orientación"),
    COMPASS("Brújula"),
    LIGHT("Luz"),
    ENVIRONMENT("Ambiente"),
    MICROPHONE("Micrófono"),
}

private fun ChallengeSensorFilter.matches(definition: AchievementDefinition): Boolean = when (this) {
    ChallengeSensorFilter.MICROPHONE -> Capability.MICROPHONE in ChallengeGameEngine.requiredCapabilities(definition.id)
    ChallengeSensorFilter.ALL -> true
    ChallengeSensorFilter.GYROSCOPE -> definition.requiredCapability == Capability.GYROSCOPE
    ChallengeSensorFilter.ACCELEROMETER -> definition.requiredCapability == Capability.ACCELEROMETER
    ChallengeSensorFilter.ORIENTATION -> definition.requiredCapability in setOf(Capability.ROTATION_VECTOR, Capability.GRAVITY)
    ChallengeSensorFilter.COMPASS -> definition.requiredCapability == Capability.MAGNETOMETER
    ChallengeSensorFilter.LIGHT -> definition.requiredCapability == Capability.LIGHT
    ChallengeSensorFilter.ENVIRONMENT -> definition.requiredCapability in setOf(Capability.HUMIDITY, Capability.AMBIENT_TEMPERATURE, Capability.PRESSURE)
}

private fun challengeFilterIcon(filter: ChallengeSensorFilter): ImageVector = when (filter) {
    ChallengeSensorFilter.MICROPHONE -> Icons.Default.Mic
    ChallengeSensorFilter.ALL -> Icons.Default.Apps
    ChallengeSensorFilter.GYROSCOPE -> Icons.Default.ScreenRotation
    ChallengeSensorFilter.ACCELEROMETER -> Icons.Default.Vibration
    ChallengeSensorFilter.ORIENTATION -> Icons.Default.ScreenLockRotation
    ChallengeSensorFilter.COMPASS -> Icons.Default.Explore
    ChallengeSensorFilter.LIGHT -> Icons.Default.LightMode
    ChallengeSensorFilter.ENVIRONMENT -> Icons.Default.Thermostat
}

@Composable
internal fun ChallengesScreen(state: AppUiState, onStart: (String) -> Unit, onClose: () -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier, onTouch: (Int) -> Unit = {}) {
    var sensorFilter by rememberSaveable { mutableStateOf(ChallengeSensorFilter.ALL) }
    var kindFilter by rememberSaveable { mutableStateOf(ChallengeKind.ALL) }
    var compatibleOnly by rememberSaveable { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val micContext = LocalContext.current
    var micPermissionNote by remember { mutableStateOf<String?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermissionNote = if (granted) "Permiso concedido. Pulsa Iniciar reto cuando estés listo."
            else "Sin permiso no se activa el micrófono. Puedes jugar los demás retos o habilitarlo en los ajustes de Android."
    }
    if (state.challengeRun.status != RunStatus.IDLE) {
        ChallengePlayScreen(state.challengeRun, onStart, onClose, modifier, onTouch, state.soundEnabled)
        return
    }
    val allChallenges = remember { AchievementCatalog.all.filter { it.category == AchievementCategory.CHALLENGE } }
    val challenges = allChallenges.filter {
        sensorFilter.matches(it) &&
            ChallengeDiscovery.matches(kindFilter, it.id) &&
            (!compatibleOnly || ChallengeDiscovery.compatible(it.id, state.unavailableChallengeCapabilities))
    }.sortedBy { if (ChallengeDiscovery.compatible(it.id, state.unavailableChallengeCapabilities)) 0 else 1 }
    previewId?.let { id ->
        AchievementCatalog.byId[id]?.let { definition ->
            ChallengeGuideDialog(definition, state.unavailableChallengeCapabilities,
                onDismiss = { previewId = null },
                onStart = {
                    if (id in MicrophoneGameEngine.ids && ContextCompat.checkSelfPermission(micContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else { previewId = null; onStart(id) }
                }, permissionNote = if (id in MicrophoneGameEngine.ids) micPermissionNote else null)
        }
    }
    val completedInFilter = challenges.count { state.states[it.id]?.unlocked == true }
    LazyColumn(modifier.fillMaxSize().testTag("challenge_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("ELIGE CÓMO JUGAR", "Retos", "Consulta la guía antes de iniciar. Los sensores esperan a que tú empieces.", onHome) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("TIPO DE RETO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChallengeKind.entries.forEach { kind ->
                        val count = allChallenges.count { ChallengeDiscovery.matches(kind, it.id) }
                        FilterChip(selected = kindFilter == kind, onClick = { kindFilter = kind },
                            label = { Text("${kind.label} · $count") })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Solo compatibles por sensores", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = compatibleOnly, onCheckedChange = { compatibleOnly = it },
                        modifier = Modifier.semantics { contentDescription = "Solo retos compatibles" })
                }
                Text("Los retos de memoria también pueden requerir movimiento.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Cyan.copy(alpha = .08f)).border(1.dp, Cyan.copy(alpha = .25f), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Cyan)
                Text("Sin escucha permanente · pausa automática al salir", Modifier.padding(start = 12.dp), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            Text(
                "FILTRAR POR SENSOR",
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChallengeSensorFilter.entries.forEach { item ->
                    val count = allChallenges.count(item::matches)
                    FilterChip(
                        selected = sensorFilter == item,
                        onClick = { sensorFilter = item },
                        leadingIcon = { Icon(challengeFilterIcon(item), null, modifier = Modifier.size(16.dp)) },
                        label = { Text("${item.label} · $count") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan,
                            selectedLabelColor = Ink,
                            selectedLeadingIconColor = Ink,
                            containerColor = Panel,
                            labelColor = Muted,
                            iconColor = Muted,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = sensorFilter == item,
                            borderColor = PanelRaised,
                            selectedBorderColor = Cyan,
                        ),
                    )
                }
            }
            Text(
                "${challenges.size} retos en esta categoría · $completedInFilter completados",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (challenges.isEmpty()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(if (kindFilter == ChallengeKind.SENSOR_FREE && allChallenges.none { ChallengeDiscovery.matches(ChallengeKind.SENSOR_FREE, it.id) }) "Todavía no hay minijuegos sin sensores."
                        else "No hay retos con esta combinación de filtros.", color = Color.White)
                    Text("Puedes ver todos y consultar qué necesita cada uno.", color = Muted, fontSize = 13.sp)
                    TextButton(onClick = { kindFilter = ChallengeKind.ALL; sensorFilter = ChallengeSensorFilter.ALL; compatibleOnly = false }) {
                        Text("Restablecer filtros")
                    }
                }
            }
        }
        items(challenges, key = { it.id }) { challenge ->
            ChallengeCard(
                challenge,
                state.states[challenge.id],
                state.unavailableChallengeCapabilities,
                { previewId = it },
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun ChallengeCard(
    definition: AchievementDefinition,
    state: AchievementState?,
    unavailableCapabilities: Set<Capability>,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unlocked = state?.unlocked == true
    val hasEngine = definition.id in ChallengeGameEngine.playableIds
    val isMeta = definition.id == "gyroscope_master"
    val ready = definition.availability == ChallengeAvailability.READY && (hasEngine || isMeta)
    val requirements = ChallengeGameEngine.requiredCapabilities(definition.id)
    val hardwareCompatible = isMeta || requirements.none { it in unavailableCapabilities }
    val color = if (ready) Cyan else Muted
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, if (unlocked) Acid.copy(.4f) else PanelRaised, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(achievementIcon(definition), null, tint = if (unlocked) Acid else color)
                if (unlocked) Icon(Icons.Default.CheckCircle, null, tint = Acid, modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(Panel, CircleShape))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(definition.title, modifier = Modifier.weight(1f).padding(end = 8.dp), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("+${definition.rarity.xp} XP", color = rarityColor(definition.rarity), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(definition.description, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isMeta) "METALOGRO" else if (requirements.isEmpty()) "TÁCTIL · SIN SENSORES" else requirements.joinToString(" · ", transform = ChallengeDiscovery::sensorName),
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(ChallengeDiscovery.kind(definition.id).label, color = Cyan, fontSize = 11.sp)
        if (definition.id == "stereo_compass") Text("Requiere auriculares estéreo y Sonidos del juego activado.", color = Muted, fontSize = 12.sp)
        if (!hardwareCompatible) {
            Text("Falta: " + ChallengeDiscovery.missing(definition.id, unavailableCapabilities)
                .joinToString(", ", transform = ChallengeDiscovery::sensorName), color = Muted, fontSize = 12.sp)
        }
        FilledTonalButton(
            onClick = { onStart(definition.id) },
            enabled = ready && hardwareCompatible && !isMeta,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Cyan.copy(alpha = .16f),
                contentColor = Cyan,
                disabledContainerColor = if (ready) Cyan.copy(alpha = .08f) else PanelRaised,
                disabledContentColor = if (ready) Cyan.copy(alpha = .55f) else Muted,
            ),
        ) {
            Text(
                when {
                    isMeta -> "Se desbloquea al dominar los 29 retos físicos originales"
                    ready && !hardwareCompatible -> "No compatible con este dispositivo"
                    unlocked -> "Volver a jugar · XP ya obtenida"
                    ready -> "Ver guía e iniciar"
                    else -> "No disponible"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun ChallengePlayScreen(run: ChallengeRunState, onStart: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, onTouch: (Int) -> Unit = {}, soundEnabled: Boolean = true) {
    BackHandler { onClose() }
    val definition = run.challengeId?.let(AchievementCatalog.byId::get)
    val terminal = run.status in setOf(RunStatus.SUCCESS, RunStatus.FAILED, RunStatus.UNSUPPORTED)
    val displayedProgress by animateFloatAsState(run.progress.coerceIn(0f, 1f), label = "challengeProgress")
    val resultIconSize by animateFloatAsState(if (run.status == RunStatus.SUCCESS) 88f else 76f, label = "resultIconSize")
    val accent = when (run.status) {
        RunStatus.SUCCESS -> Acid
        RunStatus.FAILED, RunStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
        else -> Cyan
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("MODO ACTIVO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(definition?.title ?: "Challenge", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Surface(color = accent.copy(alpha = .12f), shape = CircleShape) {
                Icon(if (run.challengeId in TouchGameEngine.ids) Icons.Default.TouchApp else Icons.Default.Sensors, null, tint = accent, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        if (run.status == RunStatus.RUNNING &&
            run.challengeId !in com.irlachievements.app.domain.challenges.CollectionGameEngine.ids) {
            Text(run.instruction, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp)
            Spacer(Modifier.height(16.dp))
        }
        if (run.challengeId in MicrophoneGameEngine.ids && run.status == RunStatus.RUNNING) {
            MicrophoneGameBoard(run, soundEnabled)
            Spacer(Modifier.height(18.dp))
        } else if (run.challengeId in TouchGameEngine.ids && run.status == RunStatus.RUNNING) {
            TouchGameBoard(run, soundEnabled, onTouch)
            Spacer(Modifier.height(18.dp))
        } else if (run.challengeId == "tilt_labyrinth" && run.status == RunStatus.RUNNING) {
            LabyrinthBoard(run, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(18.dp))
        } else {
            Box(
                Modifier.size(190.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = .28f), Panel)))
                    .border(2.dp, accent.copy(alpha = .55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (run.status) {
                        RunStatus.SUCCESS -> Icons.Default.EmojiEvents
                        RunStatus.FAILED, RunStatus.UNSUPPORTED -> Icons.Default.Close
                        else -> definition?.let(::achievementIcon) ?: Icons.Default.ScreenRotation
                    },
                    null,
                    tint = accent,
                    modifier = Modifier.size(resultIconSize.dp),
                )
            }
            Spacer(Modifier.height(30.dp))
        }
        if (run.status != RunStatus.RUNNING) {
            Text(run.instruction, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp)
            Spacer(Modifier.height(10.dp))
        }
        Text(run.detail, color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { displayedProgress },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
            color = accent,
            trackColor = PanelRaised,
        )
        if (run.totalSteps > 1) {
            Text("${run.step.coerceAtMost(run.totalSteps)} / ${run.totalSteps}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (run.status == RunStatus.SUCCESS && definition != null) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Acid.copy(alpha = .09f))
                    .border(1.dp, Acid.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Bolt, null, tint = Acid)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("RECOMPENSA DEL LOGRO · ${definition.rarity.xp} XP", color = Acid, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Se concede una sola vez. Repetir cuenta para los retos de constancia.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (terminal) {
            Text(if (run.status == RunStatus.SUCCESS) "¡Reto superado!" else if (run.status == RunStatus.UNSUPPORTED)
                "Este intento no puede continuar" else "Puedes volver a intentarlo", color = accent, fontWeight = FontWeight.Bold)
            if (run.status == RunStatus.FAILED) {
                Text(ChallengeDiscovery.retryTip(run), color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            Text(if (run.challengeId in MicrophoneGameEngine.ids) "Micrófono detenido · audio no guardado"
                else if (run.challengeId in TouchGameEngine.ids) "Partida terminada · sin penalización de XP"
                else "Sensores detenidos · sin penalización de XP", color = Muted, fontSize = 12.sp)
            run.reactionMs?.let { Text("REACCIÓN · $it ms", color = Cyan, fontWeight = FontWeight.Bold) }
            run.angularError?.let { Text("ERROR · ${"%.1f".format(it)}°", color = Gold, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(18.dp))
            if (run.status == RunStatus.FAILED && run.challengeId != null) {
                Button(onClick = { onStart(run.challengeId) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink)) {
                    Text("Reintentar", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Volver a retos") }
        } else {
            TextButton(onClick = onClose) { Text("Cancelar intento", color = Muted) }
        }
    }
}

@Composable
private fun LabyrinthBoard(run: ChallengeRunState, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Cyan.copy(alpha = .35f), RoundedCornerShape(24.dp))) {
        fun mapX(value: Float) = (value + 1f) * size.width / 2f
        fun mapY(value: Float) = (value + 1f) * size.height / 2f
        ChallengeGameEngine.walls.forEach { wall ->
            drawRoundRect(
                color = Violet.copy(alpha = .8f),
                topLeft = Offset(mapX(wall.left), mapY(wall.top)),
                size = Size(mapX(wall.right) - mapX(wall.left), mapY(wall.bottom) - mapY(wall.top)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
        drawCircle(Acid.copy(alpha = .25f), radius = size.minDimension * .075f, center = Offset(mapX(.82f), mapY(-.82f)))
        drawCircle(Acid, radius = size.minDimension * .035f, center = Offset(mapX(.82f), mapY(-.82f)))
        drawCircle(Cyan, radius = size.minDimension * .0275f, center = Offset(mapX(run.ballX), mapY(run.ballY)))
        drawCircle(Color.White.copy(alpha = .65f), radius = size.minDimension * .008f, center = Offset(mapX(run.ballX) - 4f, mapY(run.ballY) - 4f))
    }
}

@Composable
private fun SettingsScreen(
    state: AppUiState,
    onPhysicalPermissionResult: (Boolean) -> Unit,
    onSavePlayerName: (String) -> Unit,
    onSoundEnabled: (Boolean) -> Unit,
    onLogos: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var playerNameDraft by rememberSaveable(state.playerName) { mutableStateOf(state.playerName) }
    val cleanPlayerName = playerNameDraft.trim().replace(Regex("\\s+"), " ")
    val xpLevel = remember(state.summary.totalXp) { XpProgression.from(state.summary.totalXp) }
    val frameAccent = rankAccent(xpLevel.level)
    val physicalGranted = remember(refresh) {
        Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
    val notificationsGranted = remember(refresh) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val usageGranted = remember(refresh) { hasUsageAccess(context) }
    val microphoneGranted = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        onPhysicalPermissionResult(granted)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("CONTROL CENTER", "Ajustes", "Perfil, marco y permisos", onHome) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sonidos del juego", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Efectos breves · respeta el modo silencio", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = state.soundEnabled, onCheckedChange = onSoundEnabled,
                    modifier = Modifier.semantics { contentDescription = "Sonidos del juego" })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PERFIL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel)
                        .border(rankFrameWidth(xpLevel.level), frameAccent.copy(alpha = .7f), RoundedCornerShape(24.dp)).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(68.dp).clip(RoundedCornerShape(21.dp)).background(frameAccent.copy(alpha = .12f))
                                .border(rankFrameWidth(xpLevel.level), frameAccent, RoundedCornerShape(21.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            QuestifyLogo(Modifier.size(52.dp))
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(state.playerName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Text("NIVEL ${xpLevel.level} · ${xpLevel.title.uppercase()}", color = frameAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(xpLevel.reward, color = Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = playerNameDraft,
                        onValueChange = { if (it.length <= MAX_PLAYER_NAME_LENGTH) playerNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nombre de jugador") },
                        supportingText = { Text("${playerNameDraft.length}/$MAX_PLAYER_NAME_LENGTH · solo se guarda en este dispositivo") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = frameAccent,
                            focusedLabelColor = frameAccent,
                            cursorColor = frameAccent,
                        ),
                    )
                    Button(
                        onClick = { onSavePlayerName(cleanPlayerName) },
                        enabled = cleanPlayerName.isNotBlank() && cleanPlayerName != state.playerName,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = frameAccent, contentColor = Ink),
                    ) {
                        Icon(if (cleanPlayerName == state.playerName) Icons.Default.Check else Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (cleanPlayerName == state.playerName) "Nombre guardado" else "Guardar nombre", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        xpLevel.nextRankTitle?.let { "El marco cambia cada 3 niveles. Próximo: $it en nivel ${xpLevel.nextRankLevel}." }
                            ?: "Has desbloqueado el marco máximo.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                    OutlinedButton(onClick = onLogos, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("Colección de logos", fontWeight = FontWeight.Bold)
                    }
                    Text("Equipa un logo para la app y el escritorio. Desbloquea estilos con niveles, retos y secretos.", color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("PERMISOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                PermissionCard(Icons.Default.Mic, "Micrófono para retos", "Solo durante retos de sonido. Procesamiento en memoria, sin grabaciones ni envíos. Toca para gestionar el permiso en Android.", microphoneGranted, Violet) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")))
                }
                PermissionCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Actividad física", "Permite importar pasos y reconocer caminar, correr o bicicleta.", physicalGranted, Acid) {
                    if (Build.VERSION.SDK_INT >= 29) activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                PermissionCard(Icons.Default.ScreenLockPortrait, "Acceso de uso", "Solo cuenta bloqueos y desbloqueos. No guarda qué apps utilizas.", usageGranted, Gold) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PermissionCard(Icons.Default.NotificationsNone, "Notificaciones", "Solo avisa cuando se desbloquea un logro pasivo.", notificationsGranted, Cyan) {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Acid.copy(alpha = .08f)).border(1.dp, Acid.copy(alpha = .2f), RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Acid)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Privado por diseño", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Tus datos permanecen en este dispositivo. Sin cuentas, anuncios ni analytics.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, body: String, granted: Boolean, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(body, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (granted) Acid else Muted)
        }
        if (!granted) {
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text("Configurar", color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AchievementCelebration(achievement: AchievementDefinition?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = fadeIn() + scaleIn(initialScale = .8f, animationSpec = spring()),
        exit = fadeOut() + scaleOut(targetScale = .9f),
    ) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .94f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            achievement?.let { item ->
                val color = rarityColor(item.rarity)
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(124.dp).clip(CircleShape).background(color.copy(alpha = .14f)).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(achievementIcon(item), null, tint = color, modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(30.dp))
                    Text("ACHIEVEMENT UNLOCKED", color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(item.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(item.description, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("+${item.rarity.xp} XP", color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Sumada a tu nivel y rango", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink)) { Text("Continuar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Color(0xFFC1C7D4)
    Rarity.UNCOMMON -> Acid
    Rarity.RARE -> Cyan
    Rarity.EPIC -> Violet
    Rarity.LEGENDARY -> Gold
}

private fun rankAccent(level: Int): Color = when {
    level >= 22 -> Gold
    level >= 19 -> Color(0xFFFFA85C)
    level >= 16 -> Violet
    level >= 13 -> Color(0xFFFF75C3)
    level >= 10 -> Violet
    level >= 7 -> Cyan
    level >= 4 -> Acid
    else -> Color(0xFFC1C7D4)
}

private fun rankFrameWidth(level: Int) = when {
    level >= 22 -> 3.dp
    level >= 13 -> 2.5.dp
    level >= 7 -> 2.dp
    else -> 1.5.dp
}

private fun formatProgress(value: Double): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
    value >= 1_000 -> "%.1fk".format(value / 1_000)
    value % 1.0 == 0.0 -> value.roundToInt().toString()
    else -> "%.1f".format(value)
}
