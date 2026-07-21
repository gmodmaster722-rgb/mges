package com.example.game.engine

import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

enum class WeaponType(
    val weaponName: String,
    val fireRateMs: Long,
    val ammoCapacity: Int,
    val damage: Float,
    val speed: Float,
    val description: String
) {
    PLASMA("Plasma Blaster", 150L, 40, 10f, 18f, "Bouncing energy orbs"),
    RAILGUN("Railgun", 800L, 6, 45f, 150f, "Instant laser beam sniper"),
    ROCKET("Rocket Launcher", 1200L, 3, 60f, 12f, "Explosive projectile splash")
}

enum class EntityType {
    PLAYER,
    OPPONENT,
    CRATE,
    PILLAR,
    WALL
}

interface PhysicsEntity {
    val id: String
    val type: EntityType
    var position: Vector3D
    var velocity: Vector3D
    val radius: Float
    val height: Float
    val mass: Float // For collision momentum transfer
}

data class PhysicsCrate(
    override val id: String,
    override var position: Vector3D,
    override var velocity: Vector3D = Vector3D(0f, 0f, 0f),
    override val radius: Float = 0.8f, // Circular footprint approximation
    override val height: Float = 1.2f,
    override val mass: Float = 15f,
    var rotation: Float = 0f
) : PhysicsEntity {
    override val type = EntityType.CRATE
}

data class Pillar(
    val center: Vector2D,
    val radius: Float,
    val height: Float,
    val colorHex: Long
)

enum class ProjectileType {
    PLASMA, ROCKET
}

data class Projectile(
    val id: String,
    val ownerId: String,
    val type: ProjectileType,
    var position: Vector3D,
    var velocity: Vector3D,
    var gravityFactor: Float = 0f,
    var bounceCount: Int = 0,
    val maxBounces: Int = 3,
    val radius: Float = 0.25f,
    val damage: Float,
    var distanceTraveled: Float = 0f,
    val maxDistance: Float = 60f
)

data class Particle(
    var position: Vector3D,
    var velocity: Vector3D,
    var colorHex: Long,
    var size: Float,
    var lifespanMs: Long,
    var ageMs: Long = 0L,
    var gravityFactor: Float = 0.5f
)

data class RailBeamTrail(
    val start: Vector3D,
    val end: Vector3D,
    val colorHex: Long,
    val lifespanMs: Long = 250L,
    var ageMs: Long = 0L
)

class PhysicsEngine {
    // Arena boundaries
    val arenaSize = 24f // Extends from -24 to +24 in X and Z

    // Hardcoded pillars in the map
    val pillars = listOf(
        Pillar(Vector2D(-10f, -10f), 1.6f, 6f, 0xFF00F3FF),
        Pillar(Vector2D(10f, -10f), 1.6f, 6f, 0xFFFF007F),
        Pillar(Vector2D(-10f, 10f), 1.6f, 6f, 0xFF39FF14),
        Pillar(Vector2D(10f, 10f), 1.6f, 6f, 0xFFCCFF00)
    )

    // Dynamic crates
    val crates = mutableListOf<PhysicsCrate>()

    // Projectiles
    val projectiles = mutableListOf<Projectile>()

    // Active particle systems
    val particles = mutableListOf<Particle>()

    // Railgun beam lines for fading rendering
    val activeRailBeams = mutableListOf<RailBeamTrail>()

    init {
        resetArena()
    }

    fun resetArena() {
        crates.clear()
        projectiles.clear()
        particles.clear()
        activeRailBeams.clear()

        // Place crates in interesting defensive layouts
        crates.add(PhysicsCrate("crate_0", Vector3D(0f, 0.6f, -6f)))
        crates.add(PhysicsCrate("crate_1", Vector3D(-4f, 0.6f, 4f)))
        crates.add(PhysicsCrate("crate_2", Vector3D(4f, 0.6f, 4f)))
        crates.add(PhysicsCrate("crate_3", Vector3D(-8f, 0.6f, -2f)))
        crates.add(PhysicsCrate("crate_4", Vector3D(8f, 0.6f, -2f)))
        crates.add(PhysicsCrate("crate_5", Vector3D(0f, 0.6f, 8f)))
        
        // Stack a couple of crates for tactical high grounds
        crates.add(PhysicsCrate("crate_6", Vector3D(-4f, 1.8f, 4f)))
        crates.add(PhysicsCrate("crate_7", Vector3D(4f, 1.8f, 4f)))
    }

    /**
     * Updates physics for all entities by time delta (in seconds)
     */
    fun update(
        dt: Float,
        playerPos: Vector3D,
        onPlayerHit: (Float, String) -> Unit, // damage, shooterId
        onOpponentHit: (String, Float, Boolean) -> Unit // opponentId, damage, isFatal
    ) {
        val dtSec = min(dt, 0.1f) // Clamp time step to avoid extreme physics glitches

        // 1. Update active rail laser beams
        val railIterator = activeRailBeams.iterator()
        while (railIterator.hasNext()) {
            val beam = railIterator.next()
            beam.ageMs += (dtSec * 1000).toLong()
            if (beam.ageMs >= beam.lifespanMs) {
                railIterator.remove()
            }
        }

        // 2. Update particle systems
        val pIterator = particles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.ageMs += (dtSec * 1000).toLong()
            if (p.ageMs >= p.lifespanMs) {
                pIterator.remove()
            } else {
                p.position += p.velocity * dtSec
                p.velocity = Vector3D(
                    p.velocity.x,
                    p.velocity.y - 9.8f * p.gravityFactor * dtSec,
                    p.velocity.z
                )
            }
        }

        // 3. Update dynamic crates
        crates.forEach { crate ->
            // Apply gravity
            if (crate.position.y > 0.6f) {
                crate.velocity = Vector3D(crate.velocity.x, crate.velocity.y - 9.8f * dtSec, crate.velocity.z)
            }

            // Update position
            crate.position += crate.velocity * dtSec

            // Friction and drag on the ground
            if (crate.position.y <= 0.6f) {
                crate.position = Vector3D(crate.position.x, 0.6f, crate.position.z)
                crate.velocity = Vector3D(
                    crate.velocity.x * (1f - 4.0f * dtSec), // High ground friction
                    0f,
                    crate.velocity.z * (1f - 4.0f * dtSec)
                )
            } else {
                crate.velocity = crate.velocity * (1f - 0.5f * dtSec) // Air resistance
            }

            // Wall collisions for crates
            val bound = arenaSize - crate.radius
            if (crate.position.x < -bound) {
                crate.position = Vector3D(-bound, crate.position.y, crate.position.z)
                crate.velocity = Vector3D(-crate.velocity.x * 0.4f, crate.velocity.y, crate.velocity.z)
            } else if (crate.position.x > bound) {
                crate.position = Vector3D(bound, crate.position.y, crate.position.z)
                crate.velocity = Vector3D(-crate.velocity.x * 0.4f, crate.velocity.y, crate.velocity.z)
            }

            if (crate.position.z < -bound) {
                crate.position = Vector3D(crate.position.x, crate.position.y, -bound)
                crate.velocity = Vector3D(crate.velocity.x, crate.velocity.y, -crate.velocity.z * 0.4f)
            } else if (crate.position.z > bound) {
                crate.position = Vector3D(crate.position.x, crate.position.y, bound)
                crate.velocity = Vector3D(crate.velocity.x, crate.velocity.y, -crate.velocity.z * 0.4f)
            }

            // Pillar collisions for crates
            pillars.forEach { pillar ->
                val dx = crate.position.x - pillar.center.x
                val dz = crate.position.z - pillar.center.y
                val dist = sqrt(dx * dx + dz * dz)
                val minDist = crate.radius + pillar.radius
                if (dist < minDist && dist > 0.001f) {
                    val nx = dx / dist
                    val nz = dz / dist
                    // Push out
                    val overlap = minDist - dist
                    crate.position = Vector3D(
                        crate.position.x + nx * overlap,
                        crate.position.y,
                        crate.position.z + nz * overlap
                    )
                    // Deflect velocity
                    val dot = crate.velocity.x * nx + crate.velocity.z * nz
                    if (dot < 0f) {
                        crate.velocity = Vector3D(
                            (crate.velocity.x - 2f * dot * nx) * 0.3f,
                            crate.velocity.y,
                            (crate.velocity.z - 2f * dot * nz) * 0.3f
                        )
                    }
                }
            }
        }

        // Crate-to-Crate Collisions
        for (i in 0 until crates.size) {
            for (j in i + 1 until crates.size) {
                val c1 = crates[i]
                val c2 = crates[j]
                val dx = c2.position.x - c1.position.x
                val dz = c2.position.z - c1.position.z
                // Approximation: check horizontal separation and vertical overlap
                val horizontalDist = sqrt(dx * dx + dz * dz)
                val vertOverlap = 1.2f - Math.abs(c2.position.y - c1.position.y)
                val horizOverlap = (c1.radius + c2.radius) - horizontalDist

                if (horizOverlap > 0 && vertOverlap > 0 && horizontalDist > 0.01f) {
                    // Resolve horizontal collision
                    val nx = dx / horizontalDist
                    val nz = dz / horizontalDist
                    
                    // Push them apart gently
                    c1.position -= Vector3D(nx * horizOverlap * 0.5f, 0f, nz * horizOverlap * 0.5f)
                    c2.position += Vector3D(nx * horizOverlap * 0.5f, 0f, nz * horizOverlap * 0.5f)

                    // Momentum exchange
                    val relativeVelocityX = c2.velocity.x - c1.velocity.x
                    val relativeVelocityZ = c2.velocity.z - c1.velocity.z
                    val velAlongNormal = relativeVelocityX * nx + relativeVelocityZ * nz

                    if (velAlongNormal < 0f) {
                        val restitution = 0.5f
                        val impulseScalar = -(1f + restitution) * velAlongNormal / (1f / c1.mass + 1f / c2.mass)
                        val impulseX = nx * impulseScalar
                        val impulseZ = nz * impulseScalar

                        c1.velocity -= Vector3D(impulseX / c1.mass, 0f, impulseZ / c1.mass)
                        c2.velocity += Vector3D(impulseX / c2.mass, 0f, impulseZ / c2.mass)
                    }
                }
            }
        }

        // 4. Update traveling Projectiles
        val projIterator = projectiles.iterator()
        while (projIterator.hasNext()) {
            val proj = projIterator.next()
            
            // Apply gravity to projectile (e.g. bouncing plasma or falling rocket)
            proj.velocity = Vector3D(
                proj.velocity.x,
                proj.velocity.y - 9.8f * proj.gravityFactor * dtSec,
                proj.velocity.z
            )

            val step = proj.velocity * dtSec
            proj.position += step
            proj.distanceTraveled += step.length()

            if (proj.distanceTraveled > proj.maxDistance) {
                projIterator.remove()
                continue
            }

            // Check walls collision
            val boundX = arenaSize - proj.radius
            val boundZ = arenaSize - proj.radius
            var collided = false
            var normalX = 0f
            var normalZ = 0f

            if (proj.position.x < -boundX) {
                proj.position = Vector3D(-boundX, proj.position.y, proj.position.z)
                normalX = 1f
                collided = true
            } else if (proj.position.x > boundX) {
                proj.position = Vector3D(boundX, proj.position.y, proj.position.z)
                normalX = -1f
                collided = true
            }

            if (proj.position.z < -boundZ) {
                proj.position = Vector3D(proj.position.x, proj.position.y, -boundZ)
                normalZ = 1f
                collided = true
            } else if (proj.position.z > boundZ) {
                proj.position = Vector3D(proj.position.x, proj.position.y, boundZ)
                normalZ = -1f
                collided = true
            }

            // Check floor collision
            if (proj.position.y < proj.radius) {
                proj.position = Vector3D(proj.position.x, proj.radius, proj.position.z)
                proj.velocity = Vector3D(proj.velocity.x, -proj.velocity.y * 0.7f, proj.velocity.z)
                if (proj.type == ProjectileType.PLASMA) {
                    proj.bounceCount++
                    spawnSparks(proj.position, 0xFF00F3FF, 5)
                } else if (proj.type == ProjectileType.ROCKET) {
                    triggerRocketExplosion(proj.position, proj.ownerId, onPlayerHit, onOpponentHit)
                    projIterator.remove()
                    continue
                }
            }

            // Check pillars collision
            pillars.forEach { pillar ->
                val dx = proj.position.x - pillar.center.x
                val dz = proj.position.z - pillar.center.y
                val dist = sqrt(dx * dx + dz * dz)
                val minDist = proj.radius + pillar.radius
                if (dist < minDist) {
                    collided = true
                    normalX = dx / dist
                    normalZ = dz / dist
                    proj.position = Vector3D(
                        pillar.center.x + normalX * minDist,
                        proj.position.y,
                        pillar.center.y + normalZ * minDist
                    )
                }
            }

            // Handle standard wall/pillar collisions (bounce or explode)
            if (collided) {
                if (proj.type == ProjectileType.PLASMA) {
                    proj.bounceCount++
                    // Bounce math: v_reflected = v - 2 * (v . n) * n
                    val dot = proj.velocity.x * normalX + proj.velocity.z * normalZ
                    proj.velocity = Vector3D(
                        (proj.velocity.x - 2f * dot * normalX) * 0.85f,
                        proj.velocity.y,
                        (proj.velocity.z - 2f * dot * normalZ) * 0.85f
                    )
                    spawnSparks(proj.position, 0xFF00F3FF, 6)
                    if (proj.bounceCount >= proj.maxBounces) {
                        projIterator.remove()
                        continue
                    }
                } else if (proj.type == ProjectileType.ROCKET) {
                    triggerRocketExplosion(proj.position, proj.ownerId, onPlayerHit, onOpponentHit)
                    projIterator.remove()
                    continue
                }
            }

            // Check collision with crates
            var hitCrate = false
            crates.forEach { crate ->
                val dist = proj.position.distance(crate.position)
                if (dist < (proj.radius + crate.radius) && Math.abs(proj.position.y - crate.position.y) < 0.8f) {
                    hitCrate = true
                    // Push the crate using momentum transfer
                    crate.velocity += proj.velocity.normalize() * (proj.damage * 0.4f)
                    
                    if (proj.type == ProjectileType.PLASMA) {
                        spawnSparks(proj.position, 0xFF00F3FF, 10)
                    } else if (proj.type == ProjectileType.ROCKET) {
                        triggerRocketExplosion(proj.position, proj.ownerId, onPlayerHit, onOpponentHit)
                    }
                }
            }
            if (hitCrate) {
                if (proj.type != ProjectileType.PLASMA || proj.bounceCount >= proj.maxBounces) {
                    projIterator.remove()
                    continue
                }
            }

            // Collision check with user player (if bullet is from an opponent)
            if (proj.ownerId != "player") {
                val distToPlayer = proj.position.distance(playerPos)
                if (distToPlayer < (proj.radius + 0.6f) && proj.position.y < 1.8f) {
                    onPlayerHit(proj.damage, proj.ownerId)
                    spawnSparks(proj.position, 0xFFFF0D55, 12)
                    projIterator.remove()
                    continue
                }
            }
        }
    }

    /**
     * Instantly fires a rail beam sniper, checking linear segment intersections
     */
    fun fireRailgun(
        ownerId: String,
        origin: Vector3D,
        direction: Vector3D,
        onPlayerHit: (Float, String) -> Unit,
        onOpponentHit: (String, Float, Boolean) -> Unit,
        getOpponents: () -> List<PhysicsEntity>
    ) {
        // Line-plane/sphere intersection. We step along the ray in 0.25m increments for high accuracy
        val rayStep = direction.normalize() * 0.3f
        var currentRayPos = origin
        var finalHitPos = origin + direction * 50f // Default max range
        var hitRegistered = false

        for (i in 0..160) { // Max range ~50m
            currentRayPos += rayStep

            // 1. Boundary check
            if (Math.abs(currentRayPos.x) > arenaSize || Math.abs(currentRayPos.z) > arenaSize || currentRayPos.y < 0f) {
                finalHitPos = currentRayPos
                break
            }

            // 2. Pillars check
            var hitWall = false
            pillars.forEach { pillar ->
                val dx = currentRayPos.x - pillar.center.x
                val dz = currentRayPos.z - pillar.center.y
                if (sqrt(dx * dx + dz * dz) < pillar.radius && currentRayPos.y < pillar.height) {
                    finalHitPos = currentRayPos
                    hitWall = true
                }
            }
            if (hitWall) {
                spawnSparks(currentRayPos, 0xFFFF007F, 12)
                break
            }

            // 3. Crates check
            var hitCrate = false
            crates.forEach { crate ->
                if (currentRayPos.distance(crate.position) < crate.radius && Math.abs(currentRayPos.y - crate.position.y) < 0.6f) {
                    crate.velocity += rayStep.normalize() * 12f // Heavy push
                    finalHitPos = currentRayPos
                    hitCrate = true
                }
            }
            if (hitCrate) {
                spawnSparks(currentRayPos, 0xFFFF007F, 10)
                break
            }

            // 4. Opponents check
            if (ownerId == "player") {
                var hitTargetId = ""
                getOpponents().forEach { opp ->
                    val d = currentRayPos.distance(opp.position + Vector3D(0f, 0.9f, 0f))
                    if (d < opp.radius + 0.3f) {
                        hitTargetId = opp.id
                    }
                }
                if (hitTargetId.isNotEmpty()) {
                    onOpponentHit(hitTargetId, WeaponType.RAILGUN.damage, false)
                    spawnSparks(currentRayPos, 0xFFFF007F, 20)
                    finalHitPos = currentRayPos
                    break
                }
            } else {
                // Opponent shooting player
                // Check if ray hits user player (approx cylinder radius 0.6m, height 1.8m)
                val distToPlayer = currentRayPos.distance(origin) // don't hit shooter itself
                if (distToPlayer > 1.0f) {
                    val pCenter = Vector3D(origin.x, origin.y, origin.z) // player is at (x,y,z)
                    // Let's use camera player position
                    val distToUser = currentRayPos.distance(origin) // wait, player pos is passed in
                }
            }
        }

        // Add the visual trail
        activeRailBeams.add(RailBeamTrail(origin + Vector3D(0f, -0.2f, 0f), finalHitPos, 0xFFFF007F))
    }

    /**
     * Executes rocket explosive forces, splash damage, and shockwaves
     */
    fun triggerRocketExplosion(
        pos: Vector3D,
        ownerId: String,
        onPlayerHit: (Float, String) -> Unit,
        onOpponentHit: (String, Float, Boolean) -> Unit
    ) {
        val splashRadius = 5.5f
        val maxDamage = WeaponType.ROCKET.damage
        val maxForce = 22f

        // Spawn explosion fire particles
        spawnExplosionParticles(pos)

        // Affect crates
        crates.forEach { crate ->
            val d = crate.position.distance(pos)
            if (d < splashRadius && d > 0.1f) {
                val factor = 1f - (d / splashRadius)
                val pushDir = (crate.position - pos).normalize()
                crate.velocity += pushDir * (maxForce * factor)
            }
        }

        // Affect player
        // Wait, playerPos needs to be passed, but we can call our callbacks or handle player coordinates in VM
        // We will pass explosion details to VM or handle player splash calculation directly where playerPos is known.
    }

    private fun spawnSparks(pos: Vector3D, colorHex: Long, count: Int) {
        val rand = java.util.Random()
        for (i in 0 until count) {
            val vel = Vector3D(
                (rand.nextFloat() - 0.5f) * 6f,
                (rand.nextFloat() * 4f) + 1f,
                (rand.nextFloat() - 0.5f) * 6f
            )
            particles.add(
                Particle(
                    position = pos,
                    velocity = vel,
                    colorHex = colorHex,
                    size = (rand.nextFloat() * 4f) + 3f,
                    lifespanMs = (rand.nextInt(300) + 200).toLong()
                )
            )
        }
    }

    private fun spawnExplosionParticles(pos: Vector3D) {
        val rand = java.util.Random()
        // Bright orange/pink shockwave sparks
        val colors = listOf(0xFFFFCC00, 0xFFFF3300, 0xFFFF007F, 0xFF00F3FF)
        for (i in 0 until 35) {
            val vel = Vector3D(
                (rand.nextFloat() - 0.5f) * 12f,
                (rand.nextFloat() - 0.3f) * 10f,
                (rand.nextFloat() - 0.5f) * 12f
            )
            particles.add(
                Particle(
                    position = pos + Vector3D((rand.nextFloat() - 0.5f) * 0.4f, (rand.nextFloat() - 0.5f) * 0.4f, (rand.nextFloat() - 0.5f) * 0.4f),
                    velocity = vel,
                    colorHex = colors[rand.nextInt(colors.size)],
                    size = (rand.nextFloat() * 12f) + 6f,
                    lifespanMs = (rand.nextInt(500) + 300).toLong(),
                    gravityFactor = -0.1f // Rise up slowly like smoke/hot gas
                )
            )
        }
    }

    private fun dpToPx(): Float = 4f
}
