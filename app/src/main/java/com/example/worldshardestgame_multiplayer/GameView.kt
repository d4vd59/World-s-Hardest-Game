// kotlin
package com.example.worldshardestgame_multiplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callbacks
    var onLevelCompleted: (() -> Unit)? = null
    var onPlayerDied: (() -> Unit)? = null
    var onTimeUpdate: ((Long) -> Unit)? = null

    private val pressedKeys = mutableSetOf<Int>()
    private val playerSpeed = 8f
    init {
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.add(keyCode)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return true
    }

    // Spieler
    private var playerX = 100f
    private var playerY = 100f
    private val playerSize = 60f

    // Level-Elemente
    private val startZone = RectF()
    private val endZone = RectF()
    private val obstacles = mutableListOf<Enemy>()
    private val coins = mutableListOf<Coin>()

    // Level-Box / Wände
    private val levelBounds = RectF()
    private val wallThickness = 20f
    private val wallPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
    }

    // Reusable runtime RectF to avoid allocations during onDraw
    private val runtimeInnerBounds = RectF()

    // Aktuelles Level (wird von Activity gesetzt)
    var currentLevel: Int = 1

    // Multiplikator um Hindernisse schneller/langsamer zu machen
    private var obstacleSpeedMultiplier: Float = 1.2f

    // Spiel-Status
    private var isGameRunning = false
    private var levelStartTime = 0L
    private var collectedCoins = 0

    // Möglichkeit, später die Geschwindigkeit anzupassen
    @Suppress("unused")
    fun setObstacleSpeedMultiplier(mult: Float) {
        obstacleSpeedMultiplier = mult.coerceAtLeast(0.1f)
    }

    // Paint-Objekte
    private val playerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val startZonePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val endZonePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }
    private val obstaclePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val coinPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    // Ermöglicht Activity das Level zu setzen bevor startLevel() aufgerufen wird
    @Suppress("unused")
    fun setLevel(level: Int) {
        currentLevel = level
    }

    private fun initLevel() {
        // Level-Box (Ränder) basierend auf View-Größe positionieren
        val margin = 50f
        // Platz am unteren Rand freihalten für UI (Info-Panel)
        val bottomInset = 220f
        levelBounds.set(margin, margin, width - margin, height - margin - bottomInset)

        // Innerer Bereich in dem sich Spieler und Hindernisse bewegen
        val innerLeft = levelBounds.left + wallThickness
        val innerTop = levelBounds.top + wallThickness
        val innerRight = levelBounds.right - wallThickness
        val innerBottom = levelBounds.bottom - wallThickness
        val innerBounds = RectF(innerLeft, innerTop, innerRight, innerBottom)

        // Start und Ziel innerhalb der inneren Box
        startZone.set(innerBounds.left + 20f, innerBounds.top + 20f, innerBounds.left + 140f, innerBounds.top + 140f)
        endZone.set(innerBounds.right - 140f, innerBounds.bottom - 140f, innerBounds.right - 20f, innerBounds.bottom - 20f)

        playerX = startZone.centerX()
        playerY = startZone.centerY()
        collectedCoins = 0

        obstacles.clear()
        coins.clear()

        // Spezielles Layout für Level 1: "Straßenüberquerung" mit 10 horizontalen Lanes
        if (currentLevel == 1) {
            val lanes = 10
            // Hindernisse sollen ungefähr so groß wie der Spieler sein
            val desiredLaneHeight = playerSize + 12f // Platz für Hindernis + etwas Puffer

            // Wir wollen die Lanes zwischen Start- und Endzone platzieren, damit keine Überlappung entsteht
            val lanesAreaTop = startZone.bottom + 20f
            val lanesAreaBottom = endZone.top - 20f
            var availableHeight = lanesAreaBottom - lanesAreaTop
            if (availableHeight <= 0f) {
                // Falls kein Platz (sehr kleine View) nutzen wir innerBounds zentral
                availableHeight = innerBounds.height()
            }

            // Berechne laneHeight dynamisch: höchstens desiredLaneHeight, sonst teilt sich der Platz auf
            val laneHeight = if (availableHeight / lanes >= desiredLaneHeight) desiredLaneHeight else (availableHeight / lanes)
            val laneTotalHeight = lanes * laneHeight

            // vertikale Startposition so wählen, dass die Lanes zentriert im verfügbaren Bereich liegen
            val startY = lanesAreaTop + (availableHeight - laneTotalHeight) / 2f

            val baseSpeed = 4f

            for (i in 0 until lanes) {
                val laneTop = startY + i * laneHeight
                val laneCenterY = laneTop + laneHeight / 2f

                // Richtung alternieren: gerade Lanes nach rechts, ungerade nach links
                val dir = if (i % 2 == 0) 1 else -1
                val speed = (baseSpeed + (i % 3) * 0.6f) * obstacleSpeedMultiplier

                // Hindernisse ca. so groß wie der Spieler
                val radius = playerSize / 2f

                // Setze Start-X so, dass sie nicht sofort an der Wand kleben
                val xStart = if (dir == 1) (innerBounds.left + radius + 24f) else (innerBounds.right - radius - 24f)

                // genau ein Hindernis pro Lane
                obstacles.add(Enemy(xStart, laneCenterY, dir * speed, 0f, radius))
            }

            // Optional: ein paar Münzen oberhalb des ersten Lanes und am Ziel
            coins.add(Coin(innerBounds.centerX(), innerBounds.top + 20f, 12f))
            coins.add(Coin(innerBounds.centerX(), innerBounds.bottom - 40f, 12f))
        } else {
            // ...bestehende/ältere Level-Logik (wie vorher) - abwechslungsreiche Hindernisse
            // Kreisförmig bewegende Gegner (verschiedene Geschwindigkeiten)
            obstacles.add(Enemy(innerBounds.centerX() - 80f, innerBounds.centerY() - 120f, 3f * obstacleSpeedMultiplier, 0f, 28f))
            obstacles.add(Enemy(innerBounds.centerX() + 60f, innerBounds.centerY() - 60f, -2.5f * obstacleSpeedMultiplier, 0.8f * obstacleSpeedMultiplier, 26f))
            obstacles.add(Enemy(innerBounds.centerX() + 120f, innerBounds.centerY() + 90f, 0f * obstacleSpeedMultiplier, -3.2f * obstacleSpeedMultiplier, 30f))
            obstacles.add(Enemy(innerBounds.left + 120f, innerBounds.centerY() + 40f, 2.2f * obstacleSpeedMultiplier, 1.5f * obstacleSpeedMultiplier, 24f))
            obstacles.add(Enemy(innerBounds.right - 120f, innerBounds.top + 120f, -2.8f * obstacleSpeedMultiplier, 2.0f * obstacleSpeedMultiplier, 22f))
            obstacles.add(Enemy(innerBounds.centerX(), innerBounds.top + 200f, 1f * obstacleSpeedMultiplier, 0.6f * obstacleSpeedMultiplier, 36f))
            obstacles.add(Enemy(innerBounds.centerX() - 200f, innerBounds.bottom - 150f, 0.7f * obstacleSpeedMultiplier, -1.2f * obstacleSpeedMultiplier, 34f))

            // Münzen innerhalb der inneren Box
            coins.add(Coin(innerBounds.centerX(), innerBounds.top + 80f, 18f))
            coins.add(Coin(innerBounds.right - 80f, innerBounds.centerY(), 18f))
            coins.add(Coin(innerBounds.centerX() - 140f, innerBounds.bottom - 120f, 18f))
            coins.add(Coin(innerBounds.centerX() + 140f, innerBounds.bottom - 200f, 18f))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && startZone.isEmpty) {
            initLevel()
        }
    }

    fun startLevel() {
        isGameRunning = true
        levelStartTime = System.currentTimeMillis()
        initLevel()
        requestFocus()
        invalidate()
    }

    private var dpadX = 0f
    private var dpadY = 0f

    fun handleDpadInput(dx: Int, dy: Int, action: Int) {
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dpadX = dx.toFloat()
                dpadY = dy.toFloat()
            }
            MotionEvent.ACTION_UP -> {
                dpadX = 0f
                dpadY = 0f
            }
        }
    }

    private fun updatePlayerMovement() {
        if (!isGameRunning) return

        // Inner Bounds berechnen (während Laufzeit)
        val innerLeft = levelBounds.left + wallThickness
        val innerTop = levelBounds.top + wallThickness
        val innerRight = levelBounds.right - wallThickness
        val innerBottom = levelBounds.bottom - wallThickness

        if (dpadX != 0f || dpadY != 0f) {
            playerX = (playerX + dpadX * playerSpeed).coerceIn(innerLeft + playerSize / 2, innerRight - playerSize / 2)
            playerY = (playerY + dpadY * playerSpeed).coerceIn(innerTop + playerSize / 2, innerBottom - playerSize / 2)
        }
    }

    fun stopLevel() {
        isGameRunning = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isGameRunning) {
            updatePlayerMovement()
        }

        // Hintergrund
        canvas.drawColor(Color.WHITE)

        // Wände zeichnen (als Rand um levelBounds)
        val innerLeft = levelBounds.left + wallThickness
        val innerTop = levelBounds.top + wallThickness
        val innerRight = levelBounds.right - wallThickness
        val innerBottom = levelBounds.bottom - wallThickness

        // linke Wand
        canvas.drawRect(levelBounds.left, levelBounds.top, innerLeft, levelBounds.bottom, wallPaint)
        // obere Wand
        canvas.drawRect(innerLeft, levelBounds.top, levelBounds.right - wallThickness, innerTop, wallPaint)
        // rechte Wand
        canvas.drawRect(innerRight, levelBounds.top, levelBounds.right, levelBounds.bottom, wallPaint)
        // untere Wand
        canvas.drawRect(innerLeft, innerBottom, levelBounds.right - wallThickness, levelBounds.bottom, wallPaint)

        // Zonen zeichnen (innerhalb der Box)
        canvas.drawRect(startZone, startZonePaint)
        canvas.drawRect(endZone, endZonePaint)

        // Münzen zeichnen
        coins.forEach { coin ->
            if (!coin.collected) {
                canvas.drawCircle(coin.x, coin.y, coin.radius, coinPaint)
            }
        }

        // Hindernisse zeichnen und bewegen (nutze inner Bounds)
        runtimeInnerBounds.set(innerLeft, innerTop, innerRight, innerBottom)
        obstacles.forEach { enemy ->
            if (isGameRunning) {
                enemy.update(runtimeInnerBounds)
            }
            canvas.drawCircle(enemy.x, enemy.y, enemy.radius, obstaclePaint)
        }

        // Spieler zeichnen
        canvas.drawRect(
            playerX - playerSize / 2,
            playerY - playerSize / 2,
            playerX + playerSize / 2,
            playerY + playerSize / 2,
            playerPaint
        )

        // Kollisionserkennung
        if (isGameRunning) {
            checkCollisions()
            checkLevelCompletion()
            onTimeUpdate?.invoke(System.currentTimeMillis() - levelStartTime)
        }

        if (isGameRunning) {
            postInvalidateDelayed(16)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    // implementiere performClick damit Accessibility/Lint zufrieden sind
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun checkCollisions() {
        // Kollision mit Hindernissen
        obstacles.forEach { enemy ->
            val distance = calculateDistance(playerX, playerY, enemy.x, enemy.y)
            if (distance < (playerSize / 2 + enemy.radius)) {
                isGameRunning = false
                onPlayerDied?.invoke()
            }
        }

        // Münzen einsammeln
        coins.forEach { coin ->
            if (!coin.collected) {
                val distance = calculateDistance(playerX, playerY, coin.x, coin.y)
                if (distance < (playerSize / 2 + coin.radius)) {
                    coin.collected = true
                    collectedCoins++
                }
            }
        }
    }

    private fun checkLevelCompletion() {
        val allCoinsCollected = coins.all { it.collected }
        val inEndZone = endZone.contains(playerX, playerY)
        if (allCoinsCollected && inEndZone) {
            isGameRunning = false
            onLevelCompleted?.invoke()
        }
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    // Hilfsklassen
    data class Enemy(
        var x: Float,
        var y: Float,
        var speedX: Float,
        var speedY: Float,
        val radius: Float
    ) {
        fun update(bounds: RectF) {
            x += speedX
            y += speedY

            // Abprallen an inneren Wänden (bounds)
            if (x - radius < bounds.left) {
                x = bounds.left + radius
                speedX = -speedX
            } else if (x + radius > bounds.right) {
                x = bounds.right - radius
                speedX = -speedX
            }
            if (y - radius < bounds.top) {
                y = bounds.top + radius
                speedY = -speedY
            } else if (y + radius > bounds.bottom) {
                y = bounds.bottom - radius
                speedY = -speedY
            }
        }
    }

    data class Coin(
        val x: Float,
        val y: Float,
        val radius: Float,
        var collected: Boolean = false
    )
}