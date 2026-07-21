package com.example.game.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.ui.GameScreen
import com.example.game.ui.GameViewModel
import com.example.game.multiplayer.LobbyChatMessage
import com.example.ui.theme.*

@Composable
fun LobbyScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val username by viewModel.userName.collectAsStateWithLifecycle()
    val careerStats by viewModel.careerStats.collectAsStateWithLifecycle()
    val matchHistory by viewModel.matchHistory.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val chatMessages by viewModel.lobbyChat.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "lobby_grid")
    val gridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid_offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDark)
            .drawBehind {
                // Drawing dynamic grid backdrop
                val gridColor = Color(0xFFFF007F).copy(alpha = 0.05f)
                val strokeWidth = 1.5f

                // Draw vertical grid lines
                var x = gridOffset % 80f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                    x += 80f
                }

                // Draw horizontal grid lines
                var y = gridOffset % 80f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
                    y += 80f
                }
            }
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        when (currentScreen) {
            GameScreen.MENU -> {
                MenuContent(
                    username = username,
                    onStartMatchmaking = { viewModel.startMatchmaking() },
                    onNavigateStats = { viewModel.navigateTo(GameScreen.STATS) },
                    onNavigateLobby = { viewModel.navigateTo(GameScreen.LOBBY) },
                    onUsernameChanged = { viewModel.setUserName(it) }
                )
            }
            GameScreen.LOBBY -> {
                LobbyContent(
                    username = username,
                    selectedRegion = selectedRegion,
                    chatMessages = chatMessages,
                    onBack = { viewModel.navigateTo(GameScreen.MENU) },
                    onSelectRegion = { viewModel.setRegion(it) },
                    onStartMatchmaking = { viewModel.startMatchmaking() }
                )
            }
            GameScreen.STATS -> {
                StatsContent(
                    username = username,
                    wins = careerStats?.wins ?: 0,
                    losses = careerStats?.losses ?: 0,
                    kills = careerStats?.totalKills ?: 0,
                    deaths = careerStats?.totalDeaths ?: 0,
                    highscore = careerStats?.highscore ?: 0,
                    accuracy = careerStats?.accuracy ?: 0f,
                    history = matchHistory,
                    onBack = { viewModel.navigateTo(GameScreen.MENU) },
                    onResetStats = { viewModel.clearCareerStats() }
                )
            }
            else -> {}
        }
    }
}

@Composable
fun MenuContent(
    username: String,
    onStartMatchmaking: () -> Unit,
    onNavigateStats: () -> Unit,
    onNavigateLobby: () -> Unit,
    onUsernameChanged: (String) -> Unit
) {
    var editingName by remember { mutableStateOf(username) }
    var isEditing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Neon Header Title
        Text(
            text = "NEON STRIKE 3D",
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeonCyan,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .drawBehind {
                    // Draw a subtle cyan neon blur behind text
                }
        )

        Text(
            text = "MULTIPLAYER PHYSICS ARENA SHOOTER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NeonPink,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Username Editor Panel
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(CyberCard, RoundedCornerShape(12.dp))
                .border(1.dp, NeonPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        textStyle = LocalTextStyle.current.copy(color = CyberWhite, fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("username_textfield")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            isEditing = false
                            onUsernameChanged(editingName)
                        },
                        modifier = Modifier.testTag("save_username_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Name", tint = NeonGreen)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("AGENT CODNAME", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
                        Text(username, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CyberWhite, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.testTag("edit_username_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = NeonCyan)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Main Menu Action Buttons
        CyberButton(
            text = "QUICK MATCH",
            icon = Icons.Default.PlayArrow,
            glowColor = NeonCyan,
            onClick = onStartMatchmaking,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .testTag("quick_match_button")
        )

        Spacer(modifier = Modifier.height(16.dp))

        CyberButton(
            text = "GLOBAL LOBBY",
            icon = Icons.Default.Forum,
            glowColor = NeonPink,
            onClick = onNavigateLobby,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .testTag("global_lobby_button")
        )

        Spacer(modifier = Modifier.height(16.dp))

        CyberButton(
            text = "CAREER STATS",
            icon = Icons.Default.Leaderboard,
            glowColor = NeonYellow,
            onClick = onNavigateStats,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .testTag("career_stats_button")
        )
    }
}

@Composable
fun LobbyContent(
    username: String,
    selectedRegion: String,
    chatMessages: List<LobbyChatMessage>,
    onBack: () -> Unit,
    onSelectRegion: (String) -> Unit,
    onStartMatchmaking: () -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to bottom when new chat arrives
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Lobby Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CyberWhite)
            }
            Text(
                "GLOBAL SERVER LOBBY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NeonPink,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.width(48.dp)) // Equalizer spacing
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Region Selector Panel
        Text("SELECT matchmaking SERVER:", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val regions = listOf("US_WEST", "EU_EAST", "ASIA_SOUTH")
            regions.forEach { reg ->
                val isSelected = selectedRegion == reg
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonPink.copy(alpha = 0.2f) else CyberCard)
                        .border(
                            1.dp,
                            if (isSelected) NeonPink else CyberGray.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectRegion(reg) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        reg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyberWhite else CyberGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lobby Chat Room (Simulated live server interactions)
        Text("LIVE SERVER LOBBY CHAT:", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(CyberCard, RoundedCornerShape(12.dp))
                .border(1.dp, CyberGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { chat ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chat.sender,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(chat.colorHex),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (chat.isSystem) "[ANNOUNCEMENT]" else "[PING: ${32 + (chat.sender.hashCode() % 40)}ms]",
                                fontSize = 9.sp,
                                color = CyberGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = chat.message,
                            fontSize = 13.sp,
                            color = CyberWhite,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Find Match Button
        CyberButton(
            text = "DEPLOY MATCHMAKING",
            icon = Icons.Default.Launch,
            glowColor = NeonCyan,
            onClick = onStartMatchmaking,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("deploy_matchmaking_button")
        )
    }
}

@Composable
fun StatsContent(
    username: String,
    wins: Int,
    losses: Int,
    kills: Int,
    deaths: Int,
    highscore: Int,
    accuracy: Float,
    history: List<com.example.data.MatchHistory>,
    onBack: () -> Unit,
    onResetStats: () -> Unit
) {
    val kdRatio = if (deaths > 0) kills.toFloat() / deaths else kills.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stats Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CyberWhite)
            }
            Text(
                "AGENT PROFILE & CAREER",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onResetStats, modifier = Modifier.testTag("reset_stats_button")) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Career", tint = LaserRed)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid of Stats Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("WINS", wins.toString(), NeonGreen, Modifier.weight(1f))
                StatCard("LOSSES", losses.toString(), LaserRed, Modifier.weight(1f))
                StatCard("HIGH SCORE", highscore.toString(), NeonCyan, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("KILLS", kills.toString(), NeonPink, Modifier.weight(1f))
                StatCard("DEATHS", deaths.toString(), CyberGray, Modifier.weight(1f))
                StatCard("K/D RATIO", String.format("%.2f", kdRatio), NeonYellow, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Match History Logs
        Text("HISTORIC MATCH LOGS:", fontSize = 11.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CyberCard, RoundedCornerShape(12.dp))
                    .border(1.dp, CyberGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = CyberGray, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "NO MATCHES COMPLETED YET.\nLAUNCH 'QUICK MATCH' TO BEGIN.",
                        fontSize = 11.sp,
                        color = CyberGray,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
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
                items(history) { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberCard, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (match.outcome == "Victory") NeonGreen.copy(alpha = 0.4f) else LaserRed.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                match.outcome.uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (match.outcome == "Victory") NeonGreen else LaserRed,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Placed: ${match.placement}/${match.totalPlayers}",
                                fontSize = 11.sp,
                                color = CyberWhite,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Kills: ${match.kills} / Deaths: ${match.deaths}",
                                fontSize = 12.sp,
                                color = CyberWhite,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Pts: ${match.score}",
                                fontSize = 11.sp,
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CyberCard, RoundedCornerShape(10.dp))
            .border(1.dp, CyberGray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CyberButton(
    text: String,
    icon: ImageVector,
    glowColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        CyberCard,
                        CyberCard.copy(alpha = 0.8f)
                    )
                )
            )
            .border(1.5.dp, glowColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = glowColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CyberWhite,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}
