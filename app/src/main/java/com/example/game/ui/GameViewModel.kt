package com.example.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CareerStats
import com.example.data.GameDatabase
import com.example.data.GameStatsRepository
import com.example.game.engine.Camera3D
import com.example.game.engine.PhysicsCrate
import com.example.game.engine.PhysicsEngine
import com.example.game.engine.Projectile
import com.example.game.engine.ProjectileType
import com.example.game.engine.Vector3D
import com.example.game.engine.WeaponType
import com.example.game.multiplayer.KillFeedEntry
import com.example.game.multiplayer.LobbyChatMessage
import com.example.game.multiplayer.MultiplayerManager
import com.example.game.multiplayer.MultiplayerOpponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class GameScreen {
    MENU,
    LOBBY,
    MATCHMAKING,
    PLAYING,
    MATCH_OVER,
    STATS
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GameDatabase.getDatabase(application)
    private val repository = GameStatsRepository(db.gameStatsDao())

    // Career Stats & Match History from Room
    val careerStats: StateFlow<CareerStats?> = repository.careerStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val matchHistory = repository.matchHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI Navigation States
    private val _currentScreen = MutableStateFlow(GameScreen.MENU)
    val currentScreen: StateFlow<GameScreen> = _currentScreen.asStateFlow()

    // Lobby & Matchmaking States
    private val _userName = MutableStateFlow("Player_1")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _selectedRegion = MutableStateFlow("US_WEST")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _lobbyChat = MutableStateFlow<List<LobbyChatMessage>>(emptyList())
    val lobbyChat: StateFlow<List<LobbyChatMessage>> = _lobbyChat.asStateFlow()

    private val _matchmakingProgress = MutableStateFlow(0f)
    val matchmakingProgress: StateFlow<Float> = _matchmakingProgress.asStateFlow()

    private val _matchmakingStatus = MutableStateFlow("Searching for players...")
    val matchmakingStatus: StateFlow<String> = _matchmakingStatus.asStateFlow()

    // Real-Time Match States
    private val _playerPos = MutableStateFlow(Vector3D(0f, 0.9f, 0f)) // Center of arena at eye-level
    val playerPos: StateFlow<Vector3D> = _playerPos.asStateFlow()

    private val _playerVelocity = MutableStateFlow(Vector3D(0f, 0f, 0f))
    val playerVelocity: StateFlow<Vector3D> = _playerVelocity.asStateFlow()

    private val _playerYaw = MutableStateFlow(0f) // Looking forward along Z
    val playerYaw: StateFlow<Float> = _playerYaw.asStateFlow()

    private val _playerPitch = MutableStateFlow(0f) // Vertical view look
    val playerPitch: StateFlow<Float> = _playerPitch.asStateFlow()

    private val _playerHealth = MutableStateFlow(100f)
    val playerHealth: StateFlow<Float> = _playerHealth.asStateFlow()

    private val _playerKills = MutableStateFlow(0)
    val playerKills: StateFlow<Int> = _playerKills.asStateFlow()

    private val _playerDeaths = MutableStateFlow(0)
    val playerDeaths: StateFlow<Int> = _playerDeaths.asStateFlow()

    private val _currentWeapon = MutableStateFlow(WeaponType.PLASMA)
    val currentWeapon: StateFlow<WeaponType> = _currentWeapon.asStateFlow()

    private val _ammo = MutableStateFlow(WeaponType.PLASMA.ammoCapacity)
    val ammo: StateFlow<Int> = _ammo.asStateFlow()

    private val _isReloading = MutableStateFlow(false)
    val isReloading: StateFlow<Boolean> = _isReloading.asStateFlow()

    private val _reloadProgress = MutableStateFlow(0f)
    val reloadProgress: StateFlow<Float> = _reloadProgress.asStateFlow()

    private val _hitmarkerActive = MutableStateFlow(false)
    val hitmarkerActive: StateFlow<Boolean> = _hitmarkerActive.asStateFlow()

    private val _damageFlashActive = MutableStateFlow(false)
    val damageFlashActive: StateFlow<Boolean> = _damageFlashActive.asStateFlow()

    private val _muzzleFlashActive = MutableStateFlow(false)
    val muzzleFlashActive: StateFlow<Boolean> = _muzzleFlashActive.asStateFlow()

    private val _screenShakeIntensity = MutableStateFlow(0f)
    val screenShakeIntensity: StateFlow<Float> = _screenShakeIntensity.asStateFlow()

    private val _timeLeftMs = MutableStateFlow(120000L) // 2 minutes matches (120s)
    val timeLeftMs: StateFlow<Long> = _timeLeftMs.asStateFlow()

    // Physics Engine & Multiplayer Manager references
    val physicsEngine = PhysicsEngine()
    val multiplayerManager = MultiplayerManager()

    // Expose lists of drawing entities
    private val _opponentsState = MutableStateFlow<List<MultiplayerOpponent>>(emptyList())
    val opponentsState: StateFlow<List<MultiplayerOpponent>> = _opponentsState.asStateFlow()

    private val _cratesState = MutableStateFlow<List<PhysicsCrate>>(emptyList())
    val cratesState: StateFlow<List<PhysicsCrate>> = _cratesState.asStateFlow()

    private val _killFeedState = MutableStateFlow<List<KillFeedEntry>>(emptyList())
    val killFeedState: StateFlow<List<KillFeedEntry>> = _killFeedState.asStateFlow()

    // Joysticks states
    var moveJoystickX = 0f
    var moveJoystickY = 0f
    var lookJoystickX = 0f
    var lookJoystickY = 0f

    // Game loop control
    private var gameLoopJob: Job? = null
    private var lobbyBanterJob: Job? = null
    private var isMatchActive = false
    private var lastLoopTimeNs = 0L

    init {
        // Retrieve or create initial career stats
        viewModelScope.launch {
            val stats = repository.careerStats.firstOrNull()
            if (stats == null) {
                repository.saveCareerStats(CareerStats(username = "Player_1"))
            } else {
                _userName.value = stats.username
            }
        }
        
        multiplayerManager.initializeLobbyChat()
        _lobbyChat.value = multiplayerManager.lobbyChat.toList()

        // Start random lobby chatter when not in match
        startLobbyBanter()
    }

    fun setUserName(name: String) {
        val filtered = name.trim().take(12)
        if (filtered.isNotEmpty()) {
            _userName.value = filtered
            viewModelScope.launch {
                val stats = careerStats.value ?: CareerStats()
                repository.saveCareerStats(stats.copy(username = filtered))
            }
        }
    }

    fun setRegion(region: String) {
        _selectedRegion.value = region
    }

    fun navigateTo(screen: GameScreen) {
        _currentScreen.value = screen
        if (screen == GameScreen.LOBBY) {
            startLobbyBanter()
        } else {
            stopLobbyBanter()
        }
    }

    private fun startLobbyBanter() {
        stopLobbyBanter()
        lobbyBanterJob = viewModelScope.launch {
            while (true) {
                delay(2000L + (Math.random() * 3000).toLong())
                multiplayerManager.generateLobbyBanter()
                _lobbyChat.value = multiplayerManager.lobbyChat.toList()
            }
        }
    }

    private fun stopLobbyBanter() {
        lobbyBanterJob?.cancel()
        lobbyBanterJob = null
    }

    fun startMatchmaking() {
        navigateTo(GameScreen.MATCHMAKING)
        viewModelScope.launch {
            _matchmakingProgress.value = 0f
            _matchmakingStatus.value = "Searching for matchmaking servers..."
            delay(1200)

            _matchmakingProgress.value = 0.25f
            _matchmakingStatus.value = "Server located in region $selectedRegion!"
            delay(1000)

            _matchmakingProgress.value = 0.5f
            _matchmakingStatus.value = "Joining lobby. Synchronizing clocks..."
            delay(1200)

            _matchmakingProgress.value = 0.75f
            _matchmakingStatus.value = "Connecting opponents... (Pings: 32ms, 45ms, 120ms)"
            delay(1200)

            _matchmakingProgress.value = 1.0f
            _matchmakingStatus.value = "Match is starting!"
            delay(800)

            // Setup Match
            initiateMatch()
        }
    }

    private fun initiateMatch() {
        physicsEngine.resetArena()
        multiplayerManager.startMatch(userName.value)

        _playerPos.value = Vector3D(0f, 0.9f, 0f) // Center
        _playerVelocity.value = Vector3D(0f, 0f, 0f)
        _playerYaw.value = 0f
        _playerPitch.value = 0f
        _playerHealth.value = 100f
        _playerKills.value = 0
        _playerDeaths.value = 0
        _timeLeftMs.value = 120000L // 2 min (120,000 ms)
        _currentWeapon.value = WeaponType.PLASMA
        _ammo.value = WeaponType.PLASMA.ammoCapacity
        _isReloading.value = false
        _reloadProgress.value = 0f
        isMatchActive = true

        _opponentsState.value = multiplayerManager.opponents.toList()
        _cratesState.value = physicsEngine.crates.toList()
        _killFeedState.value = multiplayerManager.killFeed.toList()

        navigateTo(GameScreen.PLAYING)
        startPhysicsLoop()
    }

    private fun startPhysicsLoop() {
        gameLoopJob?.cancel()
        lastLoopTimeNs = System.nanoTime()
        gameLoopJob = viewModelScope.launch {
            while (isMatchActive) {
                val now = System.nanoTime()
                val elapsedNs = now - lastLoopTimeNs
                lastLoopTimeNs = now
                val elapsedSec = elapsedNs / 1_000_000_000f

                // Limit extreme elapsed values (e.g. background resume)
                val dt = elapsedSec.coerceIn(0.005f, 0.05f)

                runGameEngineTick(dt)

                delay(15) // Approx 60 FPS
            }
        }
    }

    private fun runGameEngineTick(dt: Float) {
        // 1. Countdown Time
        val currentMillis = _timeLeftMs.value
        if (currentMillis <= 0L) {
            finishMatch()
            return
        }
        _timeLeftMs.value = maxOf(0L, currentMillis - (dt * 1000).toLong())

        // 2. Local Player Camera Rotation (Look controls)
        if (lookJoystickX != 0f || lookJoystickY != 0f) {
            val sensitivity = 1.5f * dt
            _playerYaw.value = (_playerYaw.value + lookJoystickX * sensitivity) % (2f * Math.PI.toFloat())
            _playerPitch.value = (_playerPitch.value - lookJoystickY * sensitivity).coerceIn(
                -Math.PI.toFloat() / 2.3f, // Clamp looking too far down
                Math.PI.toFloat() / 2.3f  // Clamp looking too far up
            )
        }

        // 3. Local Player Movement (Move controls + sliding physics + jump)
        var velocity = _playerVelocity.value
        val isDead = _playerHealth.value <= 0f

        if (!isDead) {
            // Apply gravity
            val gravityAcceleration = -9.8f
            velocity = Vector3D(velocity.x, velocity.y + gravityAcceleration * dt, velocity.z)

            // Horizontal input velocities
            if (moveJoystickX != 0f || moveJoystickY != 0f) {
                val yaw = _playerYaw.value
                val forwardX = -sin(yaw)
                val forwardZ = cos(yaw)
                val rightX = cos(yaw)
                val rightZ = sin(yaw)

                val targetX = (forwardX * moveJoystickY + rightX * moveJoystickX) * 6.5f // Max speed 6.5 m/s
                val targetZ = (forwardZ * moveJoystickY + rightZ * moveJoystickX) * 6.5f

                // Interpolate velocities for fluid acceleration/inertia
                velocity = Vector3D(
                    lerp(velocity.x, targetX, 15f * dt),
                    velocity.y,
                    lerp(velocity.z, targetZ, 15f * dt)
                )
            } else {
                // Decay speed (ground friction/inertia)
                velocity = Vector3D(
                    lerp(velocity.x, 0f, 12f * dt),
                    velocity.y,
                    lerp(velocity.z, 0f, 12f * dt)
                )
            }
        } else {
            // Dead player slides to stop
            velocity = Vector3D(velocity.x * (1f - 5f * dt), velocity.y - 9.8f * dt, velocity.z * (1f - 5f * dt))
        }

        // Apply position update
        var pos = _playerPos.value + velocity * dt

        // Ground floor check
        val eyeHeight = 1.8f
        val playerFootY = pos.y - eyeHeight
        if (playerFootY <= 0f) {
            pos = Vector3D(pos.x, eyeHeight, pos.z)
            velocity = Vector3D(velocity.x, 0f, velocity.z)
        }

        // Handle respawn timer
        if (isDead) {
            val currentH = _playerHealth.value
            _playerHealth.value = (currentH + 10f * dt).coerceAtMost(0f) // Respawn progress
            if (_playerHealth.value >= 0f) {
                // Respawn
                pos = Vector3D(0f, eyeHeight, 0f)
                velocity = Vector3D(0f, 0f, 0f)
                _playerHealth.value = 100f
                _ammo.value = _currentWeapon.value.ammoCapacity
                _isReloading.value = false
            }
        }

        // Resolve Arena Boundary Wall Collisions for Local Player
        val bound = 23.4f
        val rRadius = 0.6f
        if (pos.x < -bound + rRadius) {
            pos = Vector3D(-bound + rRadius, pos.y, pos.z)
            velocity = Vector3D(0f, velocity.y, velocity.z)
        } else if (pos.x > bound - rRadius) {
            pos = Vector3D(bound - rRadius, pos.y, pos.z)
            velocity = Vector3D(0f, velocity.y, velocity.z)
        }

        if (pos.z < -bound + rRadius) {
            pos = Vector3D(pos.x, pos.y, -bound + rRadius)
            velocity = Vector3D(velocity.x, velocity.y, 0f)
        } else if (pos.z > bound - rRadius) {
            pos = Vector3D(pos.x, pos.y, bound - rRadius)
            velocity = Vector3D(velocity.x, velocity.y, 0f)
        }

        // Resolve Pillar Collisions for Local Player (Circle-to-Circle Sliding Physics)
        physicsEngine.pillars.forEach { pillar ->
            val dx = pos.x - pillar.center.x
            val dz = pos.z - pillar.center.y
            val dist = sqrt(dx * dx + dz * dz)
            val minDist = rRadius + pillar.radius
            if (dist < minDist && dist > 0.001f) {
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist
                pos = Vector3D(pos.x + nx * overlap, pos.y, pos.z + nz * overlap)
                // Zero out velocity component directed towards pillar
                val dot = velocity.x * nx + velocity.z * nz
                if (dot < 0f) {
                    velocity = Vector3D(velocity.x - dot * nx, velocity.y, velocity.z - dot * nz)
                }
            }
        }

        // Resolve Crate Collisions for Local Player
        physicsEngine.crates.forEach { crate ->
            val dx = pos.x - crate.position.x
            val dz = pos.z - crate.position.z
            val dist = sqrt(dx * dx + dz * dz)
            val minDist = rRadius + crate.radius
            // Vertical bounding overlap check (player is 1.8m height, crate is 1.2m height)
            val vertOverlap = (pos.y - eyeHeight) < (crate.position.y + 0.6f) && (pos.y > (crate.position.y - 0.6f))

            if (dist < minDist && vertOverlap && dist > 0.01f) {
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist
                
                // Push them apart (apply physical force on crate, player slides)
                pos = Vector3D(pos.x + nx * overlap * 0.3f, pos.y, pos.z + nz * overlap * 0.3f)
                crate.position = crate.position - Vector3D(nx * overlap * 0.7f, 0f, nz * overlap * 0.7f)

                // Push crate with player momentum
                val pushFactor = 4.0f
                crate.velocity = crate.velocity - Vector3D(nx * pushFactor, 0f, nz * pushFactor)
            }
        }

        _playerPos.value = pos
        _playerVelocity.value = velocity

        // 4. Update Projectiles, Particles, and Explosions in Physics Engine
        physicsEngine.update(
            dt = dt,
            playerPos = pos,
            onPlayerHit = { damage, shooterId ->
                if (_playerHealth.value > 0f) {
                    _playerHealth.value = maxOf(0f, _playerHealth.value - damage)
                    _damageFlashActive.value = true
                    _screenShakeIntensity.value = (_screenShakeIntensity.value + 6f).coerceAtMost(15f)
                    
                    if (_playerHealth.value <= 0f) {
                        _playerDeaths.value++
                        _playerHealth.value = -3000f // Starts 3s respawn countdown

                        // Post death to feed
                        val shooterName = multiplayerManager.opponents.firstOrNull { it.id == shooterId }?.name ?: "Environment"
                        val shooterColor = multiplayerManager.opponents.firstOrNull { it.id == shooterId }?.colorHex ?: 0xFF888888
                        val feed = KillFeedEntry(
                            killer = shooterName,
                            killerColor = shooterColor,
                            victim = userName.value,
                            victimColor = multiplayerManager.localPlayerColor,
                            weaponName = "Blaster"
                        )
                        multiplayerManager.killFeed.add(feed)
                    }
                }
            },
            onOpponentHit = { oppId, damage, isFatal ->
                multiplayerManager.applyDamageToOpponent(oppId, damage, "player") { killer, kColor, victim, vColor, weapon ->
                    if (killer == userName.value) {
                        _playerKills.value++
                    }
                }
                _hitmarkerActive.value = true
                viewModelScope.launch {
                    delay(120)
                    _hitmarkerActive.value = false
                }
            }
        )

        // 5. Update Multiplayer Opponents Behaviors
        multiplayerManager.update(
            dtMs = (dt * 1000).toLong(),
            playerPos = pos,
            isPlayerDead = _playerHealth.value <= 0f,
            onSpawnProjectile = { proj ->
                physicsEngine.projectiles.add(proj)
            },
            onRailgunShot = { shooterId, origin, dir ->
                physicsEngine.fireRailgun(
                    ownerId = shooterId,
                    origin = origin,
                    direction = dir,
                    onPlayerHit = { damage, sid ->
                        // Hit player
                        if (_playerHealth.value > 0f) {
                            _playerHealth.value = maxOf(0f, _playerHealth.value - damage)
                            _damageFlashActive.value = true
                            _screenShakeIntensity.value = (_screenShakeIntensity.value + 10f).coerceAtMost(20f)
                            if (_playerHealth.value <= 0f) {
                                _playerDeaths.value++
                                _playerHealth.value = -3000f // 3s respawn
                            }
                        }
                    },
                    onOpponentHit = { oppId, damage, isFatal ->
                        multiplayerManager.applyDamageToOpponent(oppId, damage, shooterId) { _, _, _, _, _ -> }
                    },
                    getOpponents = { multiplayerManager.opponents }
                )
            },
            onKillFeed = { killer, kColor, victim, vColor, weapon ->
                _killFeedState.value = multiplayerManager.killFeed.toList()
            },
            onOpponentHitPlayer = { damage, opponentId ->
                if (_playerHealth.value > 0f) {
                    _playerHealth.value = maxOf(0f, _playerHealth.value - damage)
                    _damageFlashActive.value = true
                    _screenShakeIntensity.value = (_screenShakeIntensity.value + 5f).coerceAtMost(15f)
                    if (_playerHealth.value <= 0f) {
                        _playerDeaths.value++
                        _playerHealth.value = -3000f // 3s respawn
                    }
                }
            }
        )

        // Sync list states with Compose flows
        _opponentsState.value = multiplayerManager.opponents.toList()
        _cratesState.value = physicsEngine.crates.toList()
        _killFeedState.value = multiplayerManager.killFeed.toList()

        // Decay screen shake, muzzle flash, damage flash
        if (_screenShakeIntensity.value > 0f) {
            _screenShakeIntensity.value = maxOf(0f, _screenShakeIntensity.value - 12f * dt)
        }
        if (_damageFlashActive.value) {
            _damageFlashActive.value = false // Simple instant flash trigger
        }
        if (_muzzleFlashActive.value) {
            _muzzleFlashActive.value = false
        }

        // Weapon reloading progression
        if (_isReloading.value) {
            val progress = _reloadProgress.value + (dt / 1.5f) // 1.5s reload
            if (progress >= 1f) {
                _ammo.value = _currentWeapon.value.ammoCapacity
                _isReloading.value = false
                _reloadProgress.value = 0f
            } else {
                _reloadProgress.value = progress
            }
        }
    }

    /**
     * Virtual fire button tapped or held
     */
    fun triggerPlayerFire() {
        if (_playerHealth.value <= 0f || _isReloading.value) return

        if (_ammo.value <= 0) {
            startReloading()
            return
        }

        val now = System.currentTimeMillis()
        val weapon = _currentWeapon.value

        _ammo.value--
        _muzzleFlashActive.value = true
        _screenShakeIntensity.value = (_screenShakeIntensity.value + 3.5f).coerceAtMost(10f)

        // Compute direction looking forward
        val yaw = _playerYaw.value
        val pitch = _playerPitch.value
        val dirX = -sin(yaw) * cos(pitch)
        val dirY = sin(pitch)
        val dirZ = cos(yaw) * cos(pitch)
        val fireDir = Vector3D(dirX, dirY, dirZ).normalize()

        val eyeHeight = 1.8f
        val origin = _playerPos.value + Vector3D(0f, -0.2f, 0f) // Slightly lower camera eye height for bullet origin

        when (weapon) {
            WeaponType.PLASMA -> {
                physicsEngine.projectiles.add(
                    Projectile(
                        id = "player_plasma_$now",
                        ownerId = "player",
                        type = ProjectileType.PLASMA,
                        position = origin + fireDir * 0.7f,
                        velocity = fireDir * WeaponType.PLASMA.speed,
                        damage = WeaponType.PLASMA.damage,
                        gravityFactor = 0.15f
                    )
                )
            }
            WeaponType.ROCKET -> {
                physicsEngine.projectiles.add(
                    Projectile(
                        id = "player_rocket_$now",
                        ownerId = "player",
                        type = ProjectileType.ROCKET,
                        position = origin + fireDir * 0.7f,
                        velocity = fireDir * WeaponType.ROCKET.speed,
                        damage = WeaponType.ROCKET.damage,
                        gravityFactor = 0f
                    )
                )
            }
            WeaponType.RAILGUN -> {
                physicsEngine.fireRailgun(
                    ownerId = "player",
                    origin = origin,
                    direction = fireDir,
                    onPlayerHit = { _, _ -> },
                    onOpponentHit = { oppId, damage, _ ->
                        multiplayerManager.applyDamageToOpponent(oppId, damage, "player") { killer, kColor, victim, vColor, weapon ->
                            if (killer == userName.value) {
                                _playerKills.value++
                            }
                        }
                        _hitmarkerActive.value = true
                        viewModelScope.launch {
                            delay(120)
                            _hitmarkerActive.value = false
                        }
                    },
                    getOpponents = { multiplayerManager.opponents }
                )
            }
        }
    }

    fun triggerPlayerJump() {
        val groundThreshold = 0.95f
        val isGrounded = (_playerPos.value.y - 1.8f) <= groundThreshold
        if (isGrounded && _playerHealth.value > 0f) {
            _playerVelocity.value = Vector3D(_playerVelocity.value.x, 6.2f, _playerVelocity.value.z) // Jump impulse
        }
    }

    fun selectWeapon(weapon: WeaponType) {
        if (_isReloading.value || _currentWeapon.value == weapon) return
        _currentWeapon.value = weapon
        _ammo.value = weapon.ammoCapacity
    }

    fun startReloading() {
        if (_isReloading.value || _ammo.value == _currentWeapon.value.ammoCapacity) return
        _isReloading.value = true
        _reloadProgress.value = 0f
    }

    private fun finishMatch() {
        isMatchActive = false
        gameLoopJob?.cancel()
        gameLoopJob = null

        // Determine final score and placement
        val finalKills = _playerKills.value
        val finalDeaths = _playerDeaths.value
        val finalScore = finalKills * 100 - finalDeaths * 35

        // Sort players by kills to find placement
        val sortedList = (multiplayerManager.opponents.map { it.name to it.kills } + (userName.value to finalKills))
            .sortedByDescending { it.second }
        
        val placement = sortedList.indexOfFirst { it.first == userName.value } + 1
        val totalPlayers = sortedList.size

        // Record Match in Room Database
        viewModelScope.launch {
            repository.recordMatch(
                kills = finalKills,
                deaths = finalDeaths,
                score = finalScore,
                placement = placement,
                totalPlayers = totalPlayers
            )

            // Update Career statistics
            val stats = careerStats.value ?: CareerStats(username = userName.value)
            val updatedStats = stats.copy(
                wins = stats.wins + if (placement == 1) 1 else 0,
                losses = stats.losses + if (placement > 1) 1 else 0,
                totalKills = stats.totalKills + finalKills,
                totalDeaths = stats.totalDeaths + finalDeaths,
                highscore = maxOf(stats.highscore, finalScore),
                bulletsFired = stats.bulletsFired + finalKills * 10, // approximate
                bulletsHit = stats.bulletsHit + finalKills * 3,
                accuracy = if (stats.bulletsFired + finalKills * 10 > 0) {
                    (stats.bulletsHit + finalKills * 3).toFloat() / (stats.bulletsFired + finalKills * 10) * 100f
                } else 0f
            )
            repository.saveCareerStats(updatedStats)
        }

        navigateTo(GameScreen.MATCH_OVER)
    }

    fun resetMatch() {
        initiateMatch()
    }

    fun clearCareerStats() {
        viewModelScope.launch {
            repository.clearAllHistory()
            repository.saveCareerStats(CareerStats(username = userName.value))
        }
    }

    override fun onCleared() {
        super.onCleared()
        isMatchActive = false
        gameLoopJob?.cancel()
        stopLobbyBanter()
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return start + fraction * (stop - start)
    }
}
