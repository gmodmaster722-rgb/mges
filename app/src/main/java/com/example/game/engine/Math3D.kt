package com.example.game.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3D(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vector3D(x * scale, y * scale, z * scale)
    operator fun div(scale: Float) = if (scale != 0f) Vector3D(x / scale, y / scale, z / scale) else Vector3D(0f, 0f, 0f)

    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSquared() = x * x + y * y + z * z

    fun normalize(): Vector3D {
        val len = length()
        return if (len > 0.0001f) this / len else Vector3D(0f, 0f, 0f)
    }

    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3D) = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun distance(other: Vector3D): Float = (this - other).length()
    fun distanceSquared(other: Vector3D): Float = (this - other).lengthSquared()
}

data class Vector2D(val x: Float = 0f, val y: Float = 0f)

data class Camera3D(
    val position: Vector3D = Vector3D(0f, 1.8f, 0f), // Height of 1.8m (eye level)
    val yaw: Float = 0f,  // Horizontal angle in radians
    val pitch: Float = 0f // Vertical angle in radians
)

object Math3D {
    /**
     * Projects a 3D point into 2D screen coordinates using perspective projection.
     * Returns null if the point is behind the camera plane.
     */
    fun project(
        point: Vector3D,
        camera: Camera3D,
        screenWidth: Float,
        screenHeight: Float,
        fovFactor: Float = 1.0f
    ): Vector2D? {
        val halfWidth = screenWidth / 2f
        val halfHeight = screenHeight / 2f

        // Translate point relative to camera
        val tX = point.x - camera.position.x
        val tY = point.y - camera.position.y
        val tZ = point.z - camera.position.z

        // Rotate around Y-axis (Yaw)
        val cosYaw = cos(-camera.yaw)
        val sinYaw = sin(-camera.yaw)
        val rx = tX * cosYaw - tZ * sinYaw
        val rzTmp = tX * sinYaw + tZ * cosYaw

        // Rotate around X-axis (Pitch)
        val cosPitch = cos(-camera.pitch)
        val sinPitch = sin(-camera.pitch)
        val ry = tY * cosPitch - rzTmp * sinPitch
        val rz = tY * sinPitch + rzTmp * cosPitch

        // Near-plane clipping
        if (rz < 0.1f) return null

        // Perspective Projection
        // Field of view determines focal length
        val fov = (screenWidth * 0.8f) * fovFactor
        val sX = halfWidth + (rx * fov) / rz
        val sY = halfHeight - (ry * fov) / rz // Subtract because screen Y is downward

        return Vector2D(sX, sY)
    }

    /**
     * Determines if a 3D face defined by 3 vertices is facing the camera (backface culling)
     */
    fun isFaceVisible(v0: Vector3D, v1: Vector3D, v2: Vector3D, cameraPos: Vector3D): Boolean {
        val u = v1 - v0
        val v = v2 - v0
        val normal = u.cross(v).normalize()
        val toCamera = cameraPos - v0
        return normal.dot(toCamera) > 0f
    }
}
