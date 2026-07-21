package com.example.game.ui.screens

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.engine.Camera3D
import com.example.game.engine.Math3D
import com.example.game.engine.Vector2D
import com.example.game.engine.Vector3D
import com.example.game.engine.WeaponType
import com.example.game.engine.ProjectileType
import com.example.game.multiplayer.KillFeedEntry
import com.example.game.multiplayer.MultiplayerOpponent
import com.example.game.ui.GameScreen
import com.example.game.ui.GameViewModel
import com.example.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamePlayScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val playerPos by viewModel.playerPos.collectAsStateWithLifecycle()
    val playerYaw by viewModel.playerYaw.collectAsStateWithLifecycle()
    val playerPitch by viewModel.playerPitch.collectAsStateWithLifecycle()
    val playerHealth by viewModel.playerHealth.collectAsStateWithLifecycle()
    val playerKills by viewModel.playerKills.collectAsStateWithLifecycle()
    val playerDeaths by viewModel.playerDeaths.collectAsStateWithLifecycle()
    val currentWeapon by viewModel.currentWeapon.collectAsStateWithLifecycle()
    val ammo by viewModel.ammo.collectAsStateWithLifecycle()
    val isReloading by viewModel.isReloading.collectAsStateWithLifecycle()
    val reloadProgress by viewModel.reloadProgress.collectAsStateWithLifecycle()
    val hitmarkerActive by viewModel.hitmarkerActive.collectAsStateWithLifecycle()
    val damageFlashActive by viewModel.damageFlashActive.collectAsStateWithLifecycle()
    val screenShakeIntensity by viewModel.screenShakeIntensity.collectAsStateWithLifecycle()
    val timeLeftMs by viewModel.timeLeftMs.collectAsStateWithLifecycle()

    val opponents by viewModel.opponentsState.collectAsStateWithLifecycle()
    val crates by viewModel.cratesState.collectAsStateWithLifecycle()
    val killFeed by viewModel.killFeedState.collectAsStateWithLifecycle()

    val physicsEngine = viewModel.physicsEngine
    val multiplayerManager = viewModel.multiplayerManager

    // Screen Shake Offset Calculation
    val shakeOffset = remember(screenShakeIntensity) {
        if (screenShakeIntensity > 0f) {
            val angle = Math.random() * 2 * Math.PI
            Offset(
                (cos(angle) * screenShakeIntensity * 3f).toFloat(),
                (sin(angle) * screenShakeIntensity * 3f).toFloat()
            )
        } else {
            Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDark)
            .pointerInput(Unit) {
                // Dragging anywhere on right half of the screen controls looking around
                detectDragGestures(
                    onDragStart = { /* Started */ },
                    onDragEnd = {
                        viewModel.lookJoystickX = 0f
                        viewModel.lookJoystickY = 0f
                    },
                    onDragCancel = {
                        viewModel.lookJoystickX = 0f
                        viewModel.lookJoystickY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // Check if drag starts on right side
                    if (change.position.x > size.width / 2f) {
                        viewModel.lookJoystickX = dragAmount.x * 0.12f
                        viewModel.lookJoystickY = dragAmount.y * 0.12f
                    }
                }
            }
    ) {
        if (currentScreen == GameScreen.MATCHMAKING) {
            MatchmakingOverlay(viewModel = viewModel)
            return@Box
        }

        if (currentScreen == GameScreen.MATCH_OVER) {
            MatchOverOverlay(
                kills = playerKills,
                deaths = playerDeaths,
                onRestart = { viewModel.resetMatch() },
                onNavigateMenu = { viewModel.navigateTo(GameScreen.MENU) }
            )
            return@Box
        }

        // 1. Core 3D perspective Canvas Renderer
        val camera = Camera3D(playerPos, playerYaw, playerPitch)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_3d_canvas")
        ) {
            val width = size.width
            val height = size.height

            // Apply camera shake transform
            drawContext.transform.translate(shakeOffset.x, shakeOffset.y)

            // A. Draw Sky Horizon line (for depth anchoring)
            val horizonY = height / 2f + tan(playerPitch.toDouble()).toFloat() * (height * 0.8f)
            drawLine(
                color = Color(0xFFFF007F).copy(alpha = 0.12f),
                start = Offset(0f, horizonY),
                end = Offset(width, horizonY),
                strokeWidth = 2f
            )

            // B. Draw Cosmic Ambient Stars
            val starRand = java.util.Random(1337)
            for (i in 0..60) {
                val sX = starRand.nextFloat() * width
                val sY = starRand.nextFloat() * (horizonY).coerceAtLeast(10f)
                drawCircle(
                    color = CyberWhite.copy(alpha = starRand.nextFloat() * 0.5f + 0.3f),
                    radius = starRand.nextFloat() * 2f + 1f,
                    center = Offset(sX, sY)
                )
            }

            // C. Draw Neon Ground Laser Grid
            val gridColor = Color(0xFF00F3FF).copy(alpha = 0.35f)
            val gridBounds = 24f
            val gridStep = 4f

            // Draw horizontal lines
            var z = -gridBounds
            while (z <= gridBounds) {
                // Line from (-24, 0, z) to (24, 0, z)
                val pStart = Math3D.project(Vector3D(-gridBounds, 0f, z), camera, width, height)
                val pEnd = Math3D.project(Vector3D(gridBounds, 0f, z), camera, width, height)

                if (pStart != null && pEnd != null) {
                    drawLine(gridColor, Offset(pStart.x, pStart.y), Offset(pEnd.x, pEnd.y), 1.5f)
                }
                z += gridStep
            }

            // Draw vertical lines
            var x = -gridBounds
            while (x <= gridBounds) {
                // Line from (x, 0, -24) to (x, 0, 24)
                val pStart = Math3D.project(Vector3D(x, 0f, -gridBounds), camera, width, height)
                val pEnd = Math3D.project(Vector3D(x, 0f, gridBounds), camera, width, height)

                if (pStart != null && pEnd != null) {
                    drawLine(gridColor, Offset(pStart.x, pStart.y), Offset(pEnd.x, pEnd.y), 1.5f)
                }
                x += gridStep
            }

            // Draw Arena Boundary top border box
            val wallColor = Color(0xFFFF007F).copy(alpha = 0.25f)
            val wallHeight = 5f
            val boundaryPoints = listOf(
                Vector3D(-gridBounds, 0f, -gridBounds),
                Vector3D(gridBounds, 0f, -gridBounds),
                Vector3D(gridBounds, 0f, gridBounds),
                Vector3D(-gridBounds, 0f, gridBounds)
            )
            val boundaryTopPoints = boundaryPoints.map { it.copy(y = wallHeight) }

            // Project top corners
            val projTop = boundaryTopPoints.map { Math3D.project(it, camera, width, height) }
            for (i in 0..3) {
                val start = projTop[i]
                val end = projTop[(i + 1) % 4]
                if (start != null && end != null) {
                    drawLine(wallColor, Offset(start.x, start.y), Offset(end.x, end.y), 2f)
                }

                // Vertical posts at corners
                val pBottom = Math3D.project(boundaryPoints[i], camera, width, height)
                if (pBottom != null && start != null) {
                    drawLine(wallColor, Offset(pBottom.x, pBottom.y), Offset(start.x, start.y), 1.5f)
                }
            }

            // D. Draw Bounding Pillars (Neon Columns)
            physicsEngine.pillars.forEach { pillar ->
                val steps = 6
                val pBottomProj = mutableListOf<Vector2D>()
                val pTopProj = mutableListOf<Vector2D>()

                for (i in 0 until steps) {
                    val angle = (i * 2 * Math.PI / steps).toFloat()
                    val pX = pillar.center.x + pillar.radius * cos(angle)
                    val pZ = pillar.center.y + pillar.radius * sin(angle)

                    val bProj = Math3D.project(Vector3D(pX, 0f, pZ), camera, width, height)
                    val tProj = Math3D.project(Vector3D(pX, pillar.height, pZ), camera, width, height)

                    if (bProj != null) pBottomProj.add(bProj)
                    if (tProj != null) pTopProj.add(tProj)
                }

                // Draw pillar lines
                val col = Color(pillar.colorHex)
                if (pBottomProj.size == steps && pTopProj.size == steps) {
                    for (i in 0 until steps) {
                        val bStart = pBottomProj[i]
                        val bEnd = pBottomProj[(i + 1) % steps]
                        val tStart = pTopProj[i]
                        val tEnd = pTopProj[(i + 1) % steps]

                        drawLine(col.copy(alpha = 0.4f), Offset(bStart.x, bStart.y), Offset(bEnd.x, bEnd.y), 2.5f)
                        drawLine(col, Offset(tStart.x, tStart.y), Offset(tEnd.x, tEnd.y), 2.5f)
                        drawLine(col.copy(alpha = 0.6f), Offset(bStart.x, bStart.y), Offset(tStart.x, tStart.y), 2f)
                    }
                }
            }

            // E. Draw Interactive Physical Crates (Shaded solid 3D cubes)
            crates.forEach { crate ->
                // A cube has 8 local vertices relative to its center position
                val halfSize = 0.6f
                val h = crate.height / 2f
                val vertices = listOf(
                    Vector3D(-halfSize, -h, -halfSize),
                    Vector3D(halfSize, -h, -halfSize),
                    Vector3D(halfSize, -h, halfSize),
                    Vector3D(-halfSize, -h, halfSize),
                    Vector3D(-halfSize, h, -halfSize),
                    Vector3D(halfSize, h, -halfSize),
                    Vector3D(halfSize, h, halfSize),
                    Vector3D(-halfSize, h, halfSize)
                ).map { local ->
                    // Apply rotation around vertical Y axis
                    val cosRot = cos(crate.rotation)
                    val sinRot = sin(crate.rotation)
                    val rx = local.x * cosRot - local.z * sinRot
                    val rz = local.x * sinRot + local.z * cosRot
                    crate.position + Vector3D(rx, local.y, rz)
                }

                // Project vertices
                val projVerts = vertices.map { Math3D.project(it, camera, width, height) }

                // Cube faces: Bottom(0,1,2,3), Top(4,5,6,7), Front(1,2,6,5), Back(0,3,7,4), Left(0,4,7,3), Right(1,5,6,2)
                val faces = listOf(
                    listOf(0, 1, 2, 3) to Color(0xFF0F1A30), // Bottom
                    listOf(4, 5, 6, 7) to Color(0xFF1B3252), // Top
                    listOf(1, 2, 6, 5) to Color(0xFF14243C), // Front
                    listOf(0, 3, 7, 4) to Color(0xFF122238), // Back
                    listOf(0, 4, 7, 3) to Color(0xFF182A45), // Left
                    listOf(1, 5, 6, 2) to Color(0xFF1E3558)  // Right
                )

                faces.forEach { (faceIndices, faceColor) ->
                    // Backface culling: verify normal direction against camera
                    val v0 = vertices[faceIndices[0]]
                    val v1 = vertices[faceIndices[1]]
                    val v2 = vertices[faceIndices[2]]
                    
                    if (Math3D.isFaceVisible(v0, v1, v2, camera.position)) {
                        val p0 = projVerts[faceIndices[0]]
                        val p1 = projVerts[faceIndices[1]]
                        val p2 = projVerts[faceIndices[2]]
                        val p3 = projVerts[faceIndices[3]]

                        if (p0 != null && p1 != null && p2 != null && p3 != null) {
                            val path = Path().apply {
                                moveTo(p0.x, p0.y)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                lineTo(p3.x, p3.y)
                                close()
                            }
                            // Solid shade face
                            drawPath(path, faceColor.copy(alpha = 0.85f))
                            // Wireframe outline
                            drawPath(path, Color(0xFFFF007F).copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                        }
                    }
                }
            }

            // F. Draw Projectiles
            physicsEngine.projectiles.forEach { proj ->
                val pProj = Math3D.project(proj.position, camera, width, height)
                if (pProj != null) {
                    // Distance depth scale factor
                    val dist = proj.position.distance(camera.position).coerceAtLeast(0.1f)
                    val projRadius = (proj.radius * (width * 0.8f)) / dist
                    val pColor = if (proj.type == ProjectileType.PLASMA) NeonCyan else NeonYellow

                    drawCircle(
                        color = pColor,
                        radius = projRadius.coerceIn(2f, 35f),
                        center = Offset(pProj.x, pProj.y)
                    )
                    // Core glow
                    drawCircle(
                        color = CyberWhite,
                        radius = (projRadius * 0.4f).coerceIn(1f, 15f),
                        center = Offset(pProj.x, pProj.y)
                    )
                }
            }

            // G. Draw Railgun beam lines
            physicsEngine.activeRailBeams.forEach { beam ->
                val pStart = Math3D.project(beam.start, camera, width, height)
                val pEnd = Math3D.project(beam.end, camera, width, height)
                if (pStart != null && pEnd != null) {
                    val alpha = 1f - (beam.ageMs.toFloat() / beam.lifespanMs)
                    drawLine(
                        color = Color(beam.colorHex).copy(alpha = alpha.coerceIn(0f, 1f)),
                        start = Offset(pStart.x, pStart.y),
                        end = Offset(pEnd.x, pEnd.y),
                        strokeWidth = (5f * alpha).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // H. Draw Multiplayer Opponent Bots
            opponents.forEach { opp ->
                if (opp.isDead) return@forEach

                // Represent bot as solid layered vector shapes (chest prism + floating shoulders + visor core)
                val centerTop = opp.position + Vector3D(0f, opp.height, 0f)
                val centerBase = opp.position

                val pBase = Math3D.project(centerBase, camera, width, height)
                val pTop = Math3D.project(centerTop, camera, width, height)

                if (pBase != null && pTop != null) {
                    val dist = opp.position.distance(camera.position).coerceAtLeast(0.1f)
                    val botHeightPx = abs(pTop.y - pBase.y)
                    val botWidthPx = botHeightPx * 0.55f
                    val botColor = Color(opp.colorHex)

                    // Draw vertical bounding cage for player depth rendering
                    val bodyPath = Path().apply {
                        moveTo(pTop.x, pTop.y)
                        lineTo(pTop.x - botWidthPx * 0.4f, pTop.y + botHeightPx * 0.25f)
                        lineTo(pTop.x - botWidthPx * 0.5f, pTop.y + botHeightPx * 0.7f)
                        lineTo(pBase.x - botWidthPx * 0.25f, pBase.y)
                        lineTo(pBase.x + botWidthPx * 0.25f, pBase.y)
                        lineTo(pTop.x + botWidthPx * 0.5f, pTop.y + botHeightPx * 0.7f)
                        lineTo(pTop.x + botWidthPx * 0.4f, pTop.y + botHeightPx * 0.25f)
                        close()
                    }

                    // Shaded vector armor plate
                    drawPath(bodyPath, Color(0xFF0F121C).copy(alpha = 0.8f))
                    drawPath(bodyPath, botColor, style = Stroke(width = 2.5f))

                    // Draw active glowing head visor
                    val headY = pTop.y + botHeightPx * 0.12f
                    drawLine(
                        color = NeonPink,
                        start = Offset(pTop.x - botWidthPx * 0.22f, headY),
                        end = Offset(pTop.x + botWidthPx * 0.22f, headY),
                        strokeWidth = (botHeightPx * 0.05f).coerceIn(2f, 15f),
                        cap = StrokeCap.Round
                    )

                    // I. Draw dynamic overhead bot information tag (Name, Health, Ping)
                    val tagProj = Math3D.project(opp.position + Vector3D(0f, opp.height + 0.35f, 0f), camera, width, height)
                    if (tagProj != null) {
                        val barW = 75.dp.toPx() / (dist * 0.08f).coerceAtLeast(1.0f)
                        val barH = 5.dp.toPx()

                        // Draw simple text & health bar bounds using native canvas text / graphics
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(tagProj.x, tagProj.y)

                        // Draw background bar
                        drawRect(
                            color = Color.Black.copy(alpha = 0.6f),
                            topLeft = Offset(-barW / 2f, -barH / 2f),
                            size = Size(barW, barH)
                        )
                        // Fill health bar
                        drawRect(
                            color = if (opp.health > 40f) NeonGreen else LaserRed,
                            topLeft = Offset(-barW / 2f, -barH / 2f),
                            size = Size((barW * (opp.health / 100f)).coerceAtLeast(0f), barH)
                        )

                        // Border outline
                        drawRect(
                            color = botColor.copy(alpha = 0.8f),
                            topLeft = Offset(-barW / 2f, -barH / 2f),
                            size = Size(barW, barH),
                            style = Stroke(width = 1.2f)
                        )

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }

            // J. Draw Health Packs
            multiplayerManager.healthPacks.forEach { pack ->
                if (!pack.isAvailable) return@forEach
                val pProj = Math3D.project(pack.position, camera, width, height)
                if (pProj != null) {
                    val dist = pack.position.distance(camera.position).coerceAtLeast(0.1f)
                    val r = (pack.radius * (width * 0.4f)) / dist
                    
                    // Draw a glowing cross and base cylinder
                    drawCircle(
                        color = NeonGreen.copy(alpha = 0.25f),
                        radius = r.coerceIn(5f, 50f),
                        center = Offset(pProj.x, pProj.y)
                    )
                    drawCircle(
                        color = NeonGreen,
                        radius = (r * 0.5f).coerceIn(2f, 25f),
                        center = Offset(pProj.x, pProj.y),
                        style = Stroke(width = 2f)
                    )

                    // Draw inner plus symbol
                    val len = (r * 0.35f).coerceIn(1f, 18f)
                    drawLine(NeonGreen, Offset(pProj.x - len, pProj.y), Offset(pProj.x + len, pProj.y), 3f)
                    drawLine(NeonGreen, Offset(pProj.x, pProj.y - len), Offset(pProj.x, pProj.y + len), 3f)
                }
            }

            // K. Draw Particles
            physicsEngine.particles.forEach { p ->
                val pProj = Math3D.project(p.position, camera, width, height)
                if (pProj != null) {
                    val dist = p.position.distance(camera.position).coerceAtLeast(0.1f)
                    val sizePx = (p.size * (width * 0.1f)) / dist
                    val alpha = 1f - (p.ageMs.toFloat() / p.lifespanMs)
                    drawCircle(
                        color = Color(p.colorHex).copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = sizePx.coerceIn(1f, 15f),
                        center = Offset(pProj.x, pProj.y)
                    )
                }
            }
        }

        // 2. Red Fullscreen Damage Flash Feedback
        AnimatedVisibility(
            visible = damageFlashActive,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(350))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LaserRed.copy(alpha = 0.3f))
            )
        }

        // 3. Central Tactical Aim HUD Crosshair
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val crosshairColor = if (hitmarkerActive) LaserRed else NeonCyan
            val size = if (hitmarkerActive) 16.dp else 12.dp

            // Central Crosshair
            Box(
                modifier = Modifier
                    .size(size)
                    .drawBehind {
                        val cWidth = 2.dp.toPx()
                        val cLen = 6.dp.toPx()
                        // Horizontal cross
                        drawLine(crosshairColor, Offset(-cLen, 0f), Offset(-2f, 0f), cWidth)
                        drawLine(crosshairColor, Offset(2f, 0f), Offset(cLen, 0f), cWidth)
                        // Vertical cross
                        drawLine(crosshairColor, Offset(0f, -cLen), Offset(0f, -2f), cWidth)
                        drawLine(crosshairColor, Offset(0f, 2f), Offset(0f, cLen), cWidth)
                    }
            )

            // Hitmarker floating indicator "X"
            if (hitmarkerActive) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .drawBehind {
                            val cWidth = 2.5f.dp.toPx()
                            val cLen = 10f.dp.toPx()
                            // Top Left - Bottom Right diagonal marks
                            drawLine(LaserRed, Offset(-cLen, -cLen), Offset(-4f, -4f), cWidth)
                            drawLine(LaserRed, Offset(cLen, cLen), Offset(4f, 4f), cWidth)
                            // Top Right - Bottom Left diagonal marks
                            drawLine(LaserRed, Offset(cLen, -cLen), Offset(4f, -4f), cWidth)
                            drawLine(LaserRed, Offset(-cLen, cLen), Offset(-4f, 4f), cWidth)
                        }
                )
            }
        }

        // 4. TOP ROW HUD: Health, Ammo, Timer, Scoreboard
        TopGameHUD(
            viewModel = viewModel,
            playerHealth = playerHealth,
            ammo = ammo,
            maxAmmo = currentWeapon.ammoCapacity,
            weaponName = currentWeapon.weaponName,
            kills = playerKills,
            deaths = playerDeaths,
            isReloading = isReloading,
            timeLeftMs = timeLeftMs,
            killFeed = killFeed,
            opponents = opponents
        )

        // 5. BOTTOM ROW HUD: Virtual Dual-Thumb Controllers
        BottomTouchControls(
            viewModel = viewModel,
            ammo = ammo,
            isReloading = isReloading,
            reloadProgress = reloadProgress,
            onFire = { viewModel.triggerPlayerFire() },
            onJump = { viewModel.triggerPlayerJump() },
            onSelectWeapon = { viewModel.selectWeapon(it) },
            activeWeapon = currentWeapon
        )
    }
}

@Composable
fun TopGameHUD(
    viewModel: GameViewModel,
    playerHealth: Float,
    ammo: Int,
    maxAmmo: Int,
    weaponName: String,
    kills: Int,
    deaths: Int,
    isReloading: Boolean,
    timeLeftMs: Long,
    killFeed: List<KillFeedEntry>,
    opponents: List<MultiplayerOpponent>
) {
    val sec = (timeLeftMs / 1000) % 60
    val min = (timeLeftMs / 1000) / 60
    val timerStr = String.format("%02d:%02d", min, sec)
    val displayH = playerHealth.coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // TOP LEFT: Health, Shield Bar and Weapon Status
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .border(0.5.dp, CyberGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = "Health", tint = NeonPink, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "HP: ${displayH.toInt()}/100",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberWhite,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Health Bar Outline
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CyberGray.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((displayH / 100f).coerceIn(0f, 1f))
                        .background(NeonPink)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = "Weapon Ammo", tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "$weaponName: ${if (isReloading) "RELOADING" else "$ammo/$maxAmmo"}",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // TOP CENTER: Digital Timer
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .border(1.dp, NeonYellow.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = timerStr,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonYellow,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Quick Mini scoreboard
            Text(
                "KILLS: $kills | DEATHS: $deaths",
                fontSize = 10.sp,
                color = CyberWhite,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // TOP RIGHT: Combat Live Kill Feed (Scrolling animated cards)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(160.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            killFeed.forEach { feed ->
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color(feed.killerColor).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        feed.killer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(feed.killerColor),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "eliminated",
                        tint = CyberWhite,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        feed.victim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(feed.victimColor),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BottomTouchControls(
    viewModel: GameViewModel,
    ammo: Int,
    isReloading: Boolean,
    reloadProgress: Float,
    onFire: () -> Unit,
    onJump: () -> Unit,
    onSelectWeapon: (WeaponType) -> Unit,
    activeWeapon: WeaponType
) {
    val density = LocalDensity.current
    val maxKnobRadius = remember(density) { with(density) { 45.dp.toPx() } }
    val offsetCenterPx = remember(density) { with(density) { 55.dp.toPx() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // BOTTOM LEFT: Custom Touch Joysticks for Movement
        var joystickPos by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(110.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), CircleShape)
                .pointerInteropFilter { motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            val touchX = motionEvent.x - offsetCenterPx
                            val touchY = motionEvent.y - offsetCenterPx
                            val dist = sqrt((touchX * touchX + touchY * touchY).toDouble()).toFloat()

                            if (dist < maxKnobRadius) {
                                joystickPos = Offset(touchX, touchY)
                            } else {
                                val ratio = maxKnobRadius / dist
                                joystickPos = Offset(touchX * ratio, touchY * ratio)
                            }

                            // Normalize values between -1f and 1f for movement direction
                            viewModel.moveJoystickX = joystickPos.x / maxKnobRadius
                            viewModel.moveJoystickY = -joystickPos.y / maxKnobRadius // Inverse screen Y
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            joystickPos = Offset.Zero
                            viewModel.moveJoystickX = 0f
                            viewModel.moveJoystickY = 0f
                        }
                    }
                    true
                },
            contentAlignment = Alignment.Center
        ) {
            // Knob
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { joystickPos.x.toDp() },
                        y = with(density) { joystickPos.y.toDp() }
                    )
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .border(2.dp, CyberWhite, CircleShape)
            )
        }

        // BOTTOM CENTER: Weapon Switcher Carousel
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-10).dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .border(1.dp, CyberGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeaponType.values().forEach { weapon ->
                val isSelected = activeWeapon == weapon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonPink.copy(alpha = 0.25f) else Color.Transparent)
                        .border(
                            0.5.dp,
                            if (isSelected) NeonPink else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectWeapon(weapon) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        weapon.weaponName.split(" ").first(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyberWhite else CyberGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // BOTTOM RIGHT: Action buttons: Shoot, Jump, Reload
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reload action
            IconButton(
                onClick = { viewModel.startReloading() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, NeonYellow.copy(alpha = 0.6f), CircleShape)
                    .testTag("reload_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = NeonYellow)
            }

            // Jump action
            IconButton(
                onClick = onJump,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.8f), CircleShape)
                    .testTag("jump_button")
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Jump", tint = NeonCyan, modifier = Modifier.size(24.dp))
            }

            // Huge Fire Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(NeonPink.copy(alpha = 0.9f))
                    .border(3.dp, CyberWhite, CircleShape)
                    .clickable { onFire() }
                    .testTag("fire_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Whatshot, contentDescription = "Fire", tint = CyberWhite, modifier = Modifier.size(28.dp))
                    if (isReloading) {
                        Text(
                            "${(reloadProgress * 100).toInt()}%",
                            fontSize = 9.sp,
                            color = CyberWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatchmakingOverlay(viewModel: GameViewModel) {
    val progress by viewModel.matchmakingProgress.collectAsStateWithLifecycle()
    val status by viewModel.matchmakingStatus.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDark.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(color = NeonCyan, strokeWidth = 5.dp, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "CONNECTING LOBBY...",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                status,
                fontSize = 12.sp,
                color = CyberWhite,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Matchmaking loading bar
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CyberGray.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(NeonPink)
                )
            }
        }
    }
}

@Composable
fun MatchOverOverlay(
    kills: Int,
    deaths: Int,
    onRestart: () -> Unit,
    onNavigateMenu: () -> Unit
) {
    val finalScore = kills * 100 - deaths * 35
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDark.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(CyberCard, RoundedCornerShape(16.dp))
                .border(1.5.dp, NeonYellow, RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "MATCH COMPLETED!",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NeonYellow,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("KILLS", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
                    Text(kills.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonCyan, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DEATHS", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
                    Text(deaths.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonPink, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCORE", fontSize = 10.sp, color = CyberGray, fontFamily = FontFamily.Monospace)
                    Text(finalScore.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            CyberButton(
                text = "PLAY AGAIN",
                icon = Icons.Default.Replay,
                glowColor = NeonCyan,
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateMenu,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberGray),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("RETURN TO MAIN MENU", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
