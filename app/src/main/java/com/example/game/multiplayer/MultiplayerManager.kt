package com.example.game.multiplayer

import com.example.game.engine.EntityType
import com.example.game.engine.PhysicsEntity
import com.example.game.engine.Projectile
import com.example.game.engine.ProjectileType
import com.example.game.engine.Vector2D
import com.example.game.engine.Vector3D
import com.example.game.engine.WeaponType
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MultiplayerOpponent(
    override val id: String,
    val name: String,
    val ping: Int,
    val colorHex: Long,
    override var position: Vector3D,
    override var velocity: Vector3D = Vector3D(0f, 0f, 0f),
    override val radius: Float = 0.6f,
    override val height: Float = 1.8f,
    override val mass: Float = 75f,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var health: Float = 100f,
    var isDead: Boolean = false,
    var respawnTimerMs: Long = 0L,
    var kills: Int = 0,
    var deaths: Int = 0,
    var activeWeapon: WeaponType = WeaponType.PLASMA,
    var lastFiredTimeMs: Long = 0L,
    var ammo: Int = WeaponType.PLASMA.ammoCapacity,
    var isReloading: Boolean = false,
    var reloadTimerMs: Long = 0L
) : PhysicsEntity {
    override val type = EntityType.OPPONENT
}

data class LobbyChatMessage(
    val sender: String,
    val message: String,
    val isSystem: Boolean = false,
    val colorHex: Long = 0xFFCCCCCC
)

data class KillFeedEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val killer: String,
    val killerColor: Long,
    val victim: String,
    val victimColor: Long,
    val weaponName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HealthPack(
    val position: Vector3D,
    var isAvailable: Boolean = true,
    var respawnTimerMs: Long = 0L,
    val radius: Float = 1.0f
)

class MultiplayerManager {
    private val rand = Random()
    
    val opponents = mutableListOf<MultiplayerOpponent>()
    val lobbyChat = mutableListOf<LobbyChatMessage>()
    val killFeed = mutableListOf<KillFeedEntry>()
    val healthPacks = mutableListOf(
        HealthPack(Vector3D(-12f, 0.4f, 0f)), // Left pack
        HealthPack(Vector3D(12f, 0.4f, 0f)),  // Right pack
        HealthPack(Vector3D(0f, 0.4f, -12f))  // Back pack
    )

    // Player name
    var localPlayerName = "Player_1"
    var localPlayerColor = 0xFFFF007F // Neon pink

    // Pool of funny/cool multiplayer usernames
    private val botNames = listOf(
        "AlphaCore", "NeonRunner", "LaserSpecter", "RogueBit", "VortexGamer",
        "PulseStriker", "QuantX", "MatrixBlade", "CyberPhantom", "GlitchFiend",
        "ApexCortex", "SynthStrike", "HyperDrive", "OrbitSniper", "ZeroGravity"
    )

    private val chatBanter = listOf(
        "Railgun is overpowered, devs nerf please!",
        "Plasma orbs bounce crazy in the corners.",
        "Who wants to 1v1 in the center circle?",
        "Watch out for the rockets, they push you off!",
        "GG, last match was insane.",
        "Anyone playing from EU? Ping is solid.",
        "Reloading, cover me!",
        "Just sniped someone mid-air, lol",
        "Can't touch this, my speed build is too good",
        "Grab the side health packs, they save lives."
    )

    fun initializeLobbyChat() {
        lobbyChat.clear()
        lobbyChat.add(LobbyChatMessage("System", "Welcome to Neon Strike 3D server lobby.", true, 0xFF00F3FF))
        // Add some random starting messages
        for (i in 0..3) {
            val name = botNames[rand.nextInt(botNames.size)]
            val msg = chatBanter[rand.nextInt(chatBanter.size)]
            val color = getRandomColor()
            lobbyChat.add(LobbyChatMessage(name, msg, false, color))
        }
    }

    fun generateLobbyBanter() {
        if (rand.nextFloat() < 0.15f) { // 15% chance per tick to get a chat
            val name = botNames[rand.nextInt(botNames.size)]
            val msg = chatBanter[rand.nextInt(chatBanter.size)]
            val color = getRandomColor()
            lobbyChat.add(LobbyChatMessage(name, msg, false, color))
            if (lobbyChat.size > 20) lobbyChat.removeAt(0)
        }
    }

    fun startMatch(playerUsername: String) {
        localPlayerName = playerUsername
        opponents.clear()
        killFeed.clear()
        healthPacks.forEach { 
            it.isAvailable = true
            it.respawnTimerMs = 0L
        }

        // Generate 3 opponents with varying weapons and skills
        val weapons = WeaponType.values()
        val selectedNames = botNames.shuffled().take(3)
        
        // Spawn positions at opposing corners
        val spawnPoints = listOf(
            Vector3D(-18f, 0.9f, -18f),
            Vector3D(18f, 0.9f, 18f),
            Vector3D(-18f, 0.9f, 18f)
        )

        for (i in 0 until 3) {
            val opWeapon = weapons[rand.nextInt(weapons.size)]
            opponents.add(
                MultiplayerOpponent(
                    id = "opp_$i",
                    name = selectedNames[i],
                    ping = rand.nextInt(60) + 15,
                    colorHex = getRandomColor(),
                    position = spawnPoints[i],
                    activeWeapon = opWeapon,
                    ammo = opWeapon.ammoCapacity
                )
            )
        }
    }

    /**
     * Updates opponent AI behavior, matchmaking chat, health pack respawns, and respawn loops
     */
    fun update(
        dtMs: Long,
        playerPos: Vector3D,
        isPlayerDead: Boolean,
        onSpawnProjectile: (Projectile) -> Unit,
        onRailgunShot: (String, Vector3D, Vector3D) -> Unit, // shooterId, origin, dir
        onKillFeed: (String, Long, String, Long, String) -> Unit, // killer, kColor, victim, vColor, weapon
        onOpponentHitPlayer: (Float, String) -> Unit // damage, opponentId
    ) {
        val dtSec = dtMs / 1000f

        // 1. Update Health Packs Respawn Timers
        healthPacks.forEach { pack ->
            if (!pack.isAvailable) {
                pack.respawnTimerMs -= dtMs
                if (pack.respawnTimerMs <= 0) {
                    pack.isAvailable = true
                }
            }
        }

        // 2. Clear old kill feeds (keep only last 4, expire after 4 seconds)
        val now = System.currentTimeMillis()
        killFeed.removeAll { now - it.timestamp > 4500 }

        // 3. Update Opponents AI and Mechanics
        opponents.forEach { opp ->
            if (opp.isDead) {
                opp.respawnTimerMs -= dtMs
                if (opp.respawnTimerMs <= 0) {
                    opp.isDead = false
                    opp.health = 100f
                    // Respawn at a random corner
                    val corners = listOf(
                        Vector3D(-18f, 0.9f, -18f),
                        Vector3D(18f, 0.9f, 18f),
                        Vector3D(-18f, 0.9f, 18f),
                        Vector3D(18f, 0.9f, -18f)
                    )
                    opp.position = corners[rand.nextInt(corners.size)]
                    opp.velocity = Vector3D(0f, 0f, 0f)
                    opp.ammo = opp.activeWeapon.ammoCapacity
                    opp.isReloading = false
                }
                return@forEach
            }

            // A. Reloading mechanic
            if (opp.isReloading) {
                opp.reloadTimerMs -= dtMs
                if (opp.reloadTimerMs <= 0) {
                    opp.ammo = opp.activeWeapon.ammoCapacity
                    opp.isReloading = false
                }
                opp.velocity = opp.velocity * 0.9f // Move slowly while reloading
            }

            // B. Pathfinding & AI Steering Target Selection
            // Choose the closest living entity as target (either player or another opponent)
            var targetPos = playerPos
            var targetIsPlayer = true
            var targetId = "player"
            var targetColor = localPlayerColor
            var targetName = localPlayerName
            var targetIsDead = isPlayerDead
            var minDist = opp.position.distance(playerPos)

            opponents.forEach { other ->
                if (other.id != opp.id && !other.isDead) {
                    val d = opp.position.distance(other.position)
                    if (d < minDist && !other.isDead) {
                        minDist = d
                        targetPos = other.position
                        targetIsPlayer = false
                        targetId = other.id
                        targetColor = other.colorHex
                        targetName = other.name
                        targetIsDead = false
                    }
                }
            }

            // Flee or seek health pack if low health
            val availablePacks = healthPacks.filter { it.isAvailable }
            if (opp.health < 40f && availablePacks.isNotEmpty()) {
                val closestPack = availablePacks.minByOrNull { it.position.distance(opp.position) }
                if (closestPack != null) {
                    targetPos = closestPack.position
                    targetIsPlayer = false
                    targetIsDead = false
                }
            }

            // C. Move towards steer target (AI pathfinding around pillars)
            if (!targetIsDead) {
                val dx = targetPos.x - opp.position.x
                val dz = targetPos.z - opp.position.z
                val dist = sqrt(dx * dx + dz * dz)

                if (dist > 1.5f && !opp.isReloading) {
                    var dirX = dx / dist
                    var dirZ = dz / dist

                    // Simple pillar steering avoidance
                    opponents.firstOrNull() // placeholder
                    val pillars = listOf(
                        Vector2D(-10f, -10f), Vector2D(10f, -10f),
                        Vector2D(-10f, 10f), Vector2D(10f, 10f)
                    )
                    pillars.forEach { pillar ->
                        val pdx = opp.position.x - pillar.x
                        val pdz = opp.position.z - pillar.y
                        val pDist = sqrt(pdx * pdx + pdz * pdz)
                        if (pDist < 3.5f) { // Alert radius
                            // Push steer vector perpendicular to the pillar normal
                            val nx = pdx / pDist
                            val nz = pdz / pDist
                            dirX += nz * 0.8f // Sidestep normal
                            dirZ -= nx * 0.8f
                        }
                    }

                    // Normalize steer vector
                    val len = sqrt(dirX * dirX + dirZ * dirZ)
                    if (len > 0f) {
                        dirX /= len
                        dirZ /= len
                    }

                    val moveSpeed = 4.2f // m/s
                    opp.velocity = Vector3D(dirX * moveSpeed, opp.velocity.y, dirZ * moveSpeed)
                    opp.yaw = atan2(-dirX, dirZ) // Look towards movement direction
                } else {
                    opp.velocity = Vector3D(opp.velocity.x * 0.8f, opp.velocity.y, opp.velocity.z * 0.8f)
                }
            }

            // D. Shooting mechanic
            if (!opp.isReloading && !targetIsDead && minDist < 20f) {
                val nowTime = System.currentTimeMillis()
                if (nowTime - opp.lastFiredTimeMs > opp.activeWeapon.fireRateMs) {
                    if (opp.ammo <= 0) {
                        opp.isReloading = true
                        opp.reloadTimerMs = 1500L // 1.5 seconds reload
                    } else {
                        opp.ammo--
                        opp.lastFiredTimeMs = nowTime

                        // Turn head towards target
                        val fireDir = (targetPos - opp.position).normalize()
                        opp.yaw = atan2(-fireDir.x, fireDir.z)
                        
                        // Fire weapon
                        when (opp.activeWeapon) {
                            WeaponType.PLASMA -> {
                                val bId = "bullet_${opp.id}_${nowTime}"
                                onSpawnProjectile(
                                    Projectile(
                                        id = bId,
                                        ownerId = opp.id,
                                        type = ProjectileType.PLASMA,
                                        position = opp.position + Vector3D(0f, 1.2f, 0f) + fireDir * 0.8f,
                                        velocity = fireDir * WeaponType.PLASMA.speed,
                                        damage = WeaponType.PLASMA.damage,
                                        gravityFactor = 0.1f
                                    )
                                )
                            }
                            WeaponType.ROCKET -> {
                                val bId = "rocket_${opp.id}_${nowTime}"
                                onSpawnProjectile(
                                    Projectile(
                                        id = bId,
                                        ownerId = opp.id,
                                        type = ProjectileType.ROCKET,
                                        position = opp.position + Vector3D(0f, 1.2f, 0f) + fireDir * 0.8f,
                                        velocity = fireDir * WeaponType.ROCKET.speed,
                                        damage = WeaponType.ROCKET.damage,
                                        gravityFactor = 0f
                                    )
                                )
                            }
                            WeaponType.RAILGUN -> {
                                // Direct laser shot raycast
                                val origin = opp.position + Vector3D(0f, 1.2f, 0f)
                                onRailgunShot(opp.id, origin, fireDir)

                                // Check instant raycast hit against player
                                if (targetIsPlayer && !isPlayerDead) {
                                    // Simulated hit chance based on distance and bot level (e.g. 45% hit rate at 15m)
                                    val hitChance = 0.45f / (minDist / 12f).coerceAtLeast(1.0f)
                                    if (rand.nextFloat() < hitChance) {
                                        onOpponentHitPlayer(WeaponType.RAILGUN.damage, opp.id)
                                    }
                                } else if (!targetIsPlayer) {
                                    // Hit other bot
                                    val victimId = targetId
                                    if (rand.nextFloat() < 0.5f) {
                                        applyDamageToOpponent(victimId, WeaponType.RAILGUN.damage, opp.id, onKillFeed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // E. Ground contact physics and walls bounds resolution
            if (opp.position.y > 0.9f) {
                opp.velocity = Vector3D(opp.velocity.x, opp.velocity.y - 9.8f * dtSec, opp.velocity.z)
            }
            opp.position += opp.velocity * dtSec

            if (opp.position.y <= 0.9f) {
                opp.position = Vector3D(opp.position.x, 0.9f, opp.position.z)
                opp.velocity = Vector3D(opp.velocity.x, 0f, opp.velocity.z)
            }

            // Bound checking
            val bound = 23.4f
            if (opp.position.x < -bound) opp.position = Vector3D(-bound, opp.position.y, opp.position.z)
            if (opp.position.x > bound) opp.position = Vector3D(bound, opp.position.y, opp.position.z)
            if (opp.position.z < -bound) opp.position = Vector3D(opp.position.x, opp.position.y, -bound)
            if (opp.position.z > bound) opp.position = Vector3D(opp.position.x, opp.position.y, bound)

            // F. Pickup Health Pack check
            healthPacks.forEach { pack ->
                if (pack.isAvailable && opp.position.distance(pack.position) < (opp.radius + pack.radius)) {
                    pack.isAvailable = false
                    pack.respawnTimerMs = 15000L // 15s respawn
                    opp.health = (opp.health + 45f).coerceAtMost(100f)
                }
            }
        }
    }

    /**
     * Inflicts damage on an opponent bot. Triggers death, respawn timer, and posts to Kill Feed
     */
    fun applyDamageToOpponent(
        opponentId: String,
        damage: Float,
        killerId: String,
        onKillFeed: (String, Long, String, Long, String) -> Unit
    ) {
        val opp = opponents.firstOrNull { it.id == opponentId } ?: return
        if (opp.isDead) return

        opp.health -= damage
        if (opp.health <= 0f) {
            opp.isDead = true
            opp.health = 0f
            opp.deaths++
            opp.respawnTimerMs = 5000L // 5 seconds respawn time

            val killerName: String
            val killerColor: Long
            val weaponUsed: String

            if (killerId == "player") {
                killerName = localPlayerName
                killerColor = localPlayerColor
                // We'll increment killer scores inside ViewModel, but can log here
                weaponUsed = "Railgun" // We can adapt this dynamically
            } else {
                val killerBot = opponents.firstOrNull { it.id == killerId }
                if (killerBot != null) {
                    killerBot.kills++
                    killerName = killerBot.name
                    killerColor = killerBot.colorHex
                    weaponUsed = killerBot.activeWeapon.weaponName
                } else {
                    killerName = "Environment"
                    killerColor = 0xFF888888
                    weaponUsed = "Explosion"
                }
            }

            val feed = KillFeedEntry(
                killer = killerName,
                killerColor = killerColor,
                victim = opp.name,
                victimColor = opp.colorHex,
                weaponName = weaponUsed
            )
            killFeed.add(feed)
            if (killFeed.size > 4) killFeed.removeAt(0)

            onKillFeed(killerName, killerColor, opp.name, opp.colorHex, weaponUsed)
        }
    }

    fun getRandomColor(): Long {
        val neonColors = listOf(
            0xFF00F3FF, // Cyan
            0xFFFF007F, // Pink
            0xFF39FF14, // Green
            0xFFCCFF00, // Yellow
            0xFFFF9F00, // Orange
            0xFFBD00FF  // Purple
        )
        return neonColors[rand.nextInt(neonColors.size)]
    }
}
