package com.irlachievements.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.irlachievements.app.ui.theme.Acid
import com.irlachievements.app.ui.theme.Panel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irlachievements.app.R
import com.irlachievements.app.ui.theme.Ink

@Composable
internal fun BrandSplashScreen() {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val opacity by animateFloatAsState(if (entered) 1f else 0f, tween(400), label = "brandOpacity")
    val scale by animateFloatAsState(if (entered) 1f else .88f, tween(400), label = "brandScale")
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(Modifier.alpha(opacity).scale(scale), horizontalAlignment = Alignment.CenterHorizontally) {
            QuestifyLogo(Modifier.size(96.dp), description = null)
            Spacer(Modifier.height(20.dp))
            Text("QUESTIFY", color = Color.White, fontSize = 30.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun CreditsScreen(onHome: () -> Unit) {
    val members = stringArrayResource(R.array.team_members)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al inicio", tint = Color.White) }
            Text("Créditos", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(28.dp))
        members.forEach { name ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Panel)
                    .border(1.dp, Acid.copy(alpha = .18f), RoundedCornerShape(20.dp)).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, lineHeight = 30.sp)
            }
        }
    }
}
