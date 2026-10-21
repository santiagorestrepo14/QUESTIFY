package com.irlachievements.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlachievements.app.R
import com.irlachievements.app.domain.model.*
import com.irlachievements.app.ui.theme.*

internal val LocalQuestifyLogo = staticCompositionLocalOf { LogoStyle.NEON_CORE }

internal fun LogoStyle.drawable(): Int = when (this) {
    LogoStyle.NEON_CORE -> R.drawable.ic_logo_neon_core
    LogoStyle.SKY_PULSE -> R.drawable.ic_logo_sky_pulse
    LogoStyle.VIOLET_RUSH -> R.drawable.ic_logo_violet_rush
    LogoStyle.SUNSET_CHARGE -> R.drawable.ic_logo_sunset_charge
    LogoStyle.EMERALD_NIGHT -> R.drawable.ic_logo_emerald_night
    LogoStyle.FROST_ELITE -> R.drawable.ic_logo_frost_elite
    LogoStyle.GOLD_RANK -> R.drawable.ic_logo_gold_rank
    LogoStyle.NOVA_CROWN -> R.drawable.ic_logo_nova_crown
    LogoStyle.GLITCH_RELIC -> R.drawable.ic_logo_glitch_relic
}

@Composable
internal fun QuestifyLogo(modifier: Modifier = Modifier, style: LogoStyle = LocalQuestifyLogo.current,
    description: String? = "QUESTIFY · ${style.title}") {
    Image(painterResource(style.drawable()), description, modifier)
}

@Composable
internal fun LogoCollectionScreen(state: AppUiState, onSelect: (LogoStyle) -> Unit,
    onRetry: () -> Unit, onBack: () -> Unit) {
    val collection = remember(state.states) { LogoCollection.evaluate(state.states) }
    val selected = remember(state.selectedLogoId, state.states) { LogoCollection.resolveSelection(state.selectedLogoId, state.states) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Ajustes", tint = Color.White) }
                Column {
                    Text("TU IDENTIDAD", color = Acid, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("Colección de logos", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                QuestifyLogo(Modifier.size(112.dp), selected)
                Text(selected.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("LOGO EQUIPADO", color = Acid, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(12.dp))
                Text("${collection.count { it.unlocked }} / ${collection.size} desbloqueados", color = Cyan, fontWeight = FontWeight.Bold)
                Text("Cambia tu emblema dentro de QUESTIFY y el icono en el escritorio de Android. El escritorio puede tardar unos segundos en actualizarse.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Solo personalización: no gasta XP ni reemplaza tu marco de nivel.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        state.logoChangeError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("Reintentar cambio en escritorio") }
            }
        }
        items(collection, key = { it.style.id }) { entry ->
            LogoCollectionCard(entry, entry.style == selected, onSelect)
        }
    }
}

@Composable
internal fun LogoCollectionCard(entry: LogoUnlockState, selected: Boolean, onSelect: (LogoStyle) -> Unit) {
    val secret = entry.style == LogoStyle.GLITCH_RELIC && !entry.unlocked
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
        .border(1.dp, if (selected) Acid else if (entry.style == LogoStyle.NOVA_CROWN) Gold.copy(alpha = .45f) else PanelRaised, RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Ink), contentAlignment = Alignment.Center) {
                if (secret) Icon(Icons.Default.Lock, "Logo secreto", tint = Violet, modifier = Modifier.size(30.dp))
                else QuestifyLogo(Modifier.size(68.dp), entry.style)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(entry.style.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(when {
                    selected -> "EQUIPADO"
                    entry.unlocked -> "DESBLOQUEADO"
                    entry.style == LogoStyle.GLITCH_RELIC -> "RELIQUIA SECRETA"
                    entry.style == LogoStyle.NOVA_CROWN -> "CORONA FINAL"
                    else -> "POR DESCUBRIR"
                }, color = if (secret) Violet else if (entry.style == LogoStyle.NOVA_CROWN) Gold else Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(entry.requirement, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!secret) {
            LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth(), color = if (entry.unlocked) Acid else Cyan, trackColor = PanelRaised)
            Spacer(Modifier.height(6.dp))
        }
        Text(entry.progressText, color = Muted, fontSize = 11.sp)
        FilledTonalButton(onClick = { onSelect(entry.style) }, enabled = entry.unlocked && !selected,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (selected) "Equipado" else if (entry.unlocked) "Equipar ${entry.style.title}" else "Bloqueado")
        }
    }
}
