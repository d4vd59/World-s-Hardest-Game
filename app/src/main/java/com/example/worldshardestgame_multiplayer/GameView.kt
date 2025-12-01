package com.example.worldshardestgame_multiplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import kotlin.div
import kotlin.invoke
import kotlin.math.abs
import kotlin.text.toFloat

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callbacks
    var onLevelCompleted: (() -> Unit)? = null
    var onPlayerDied: (() -> Unit)? = null
    var onTimeUpdate: ((Long) -> Unit)? = null

    // In GameView.kt - neue Variable für gedrückte Tasten hinzufügen
    private val pressedKeys = mutableSetOf<Int>()
    private val playerSpeed = 5f // Geschwindigkeit reduziert von 10f auf 5f

    init {
        initLevel()
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

    private fun updatePlayerMovement() {
        if (!isGameRunning) return

        if (KeyEvent.KEYCODE_W in pressedKeys) {
            playerY = (playerY - playerSpeed).coerceIn(playerSize / 2, height - playerSize / 2)
        }
        if (KeyEvent.KEYCODE_S in pressedKeys) {
            playerY = (playerY + playerSpeed).coerceIn(playerSize / 2, height - playerSize / 2)
        }
        if (KeyEvent.KEYCODE_A in pressedKeys) {
            playerX = (playerX - playerSpeed).coerceIn(playerSize / 2, width - playerSize / 2)
        }
        if (KeyEvent.KEYCODE_D in pressedKeys) {
            playerX = (playerX + playerSpeed).coerceIn(playerSize / 2, width - playerSize / 2)
        }
    }

    // Spieler
    private var playerX = 100f
    private var playerY = 100f
    private val playerSize = 40f

    // Level-Elemente
    private val startZone = RectF(50f, 50f, 150f, 150f)
    private val endZone = RectF(700f, 900f, 800f, 1000f)
    private val obstacles = mutableListOf<Enemy>()
    private val coins = mutableListOf<Coin>()

    // Spiel-Status
    private var isGameRunning = false
    private var levelStartTime = 0L
    private var collectedCoins = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

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

    init {
        initLevel()
    }

    private fun initLevel() {
        playerX = startZone.centerX()
        playerY = startZone.centerY()
        collectedCoins = 0

        // Beispiel-Level mit beweglichen Hindernissen
        obstacles.clear()
        obstacles.add(Enemy(400f, 300f, 2f, 0f, 30f))
        obstacles.add(Enemy(400f, 500f, -2f, 0f, 30f))
        obstacles.add(Enemy(500f, 400f, 0f, 3f, 30f))

        // Münzen platzieren
        coins.clear()
        coins.add(Coin(400f, 200f, 20f))
        coins.add(Coin(600f, 500f, 20f))
        coins.add(Coin(400f, 800f, 20f))
    }

    fun startLevel() {
        isGameRunning = true
        levelStartTime = System.currentTimeMillis()
        initLevel()
        invalidate()
    }

    fun stopLevel() {
        isGameRunning = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Bewegung ZUERST aktualisieren
        if (isGameRunning) {
            updatePlayerMovement()
        }

        // Hintergrund
        canvas.drawColor(Color.WHITE)

        // Zonen zeichnen
        canvas.drawRect(startZone, startZonePaint)
        canvas.drawRect(endZone, endZonePaint)

        // Münzen zeichnen
        coins.forEach { coin ->
            if (!coin.collected) {
                canvas.drawCircle(coin.x, coin.y, coin.radius, coinPaint)
            }
        }

        // Hindernisse zeichnen und bewegen
        obstacles.forEach { enemy ->
            if (isGameRunning) {
                enemy.update(width.toFloat(), height.toFloat())
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

        // Nächsten Frame anfordern
        if (isGameRunning) {
            postInvalidateDelayed(16) // ~60 FPS
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGameRunning) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY

                // Bewegung mit Begrenzung
                playerX = (playerX + dx).coerceIn(playerSize / 2, width - playerSize / 2)
                playerY = (playerY + dy).coerceIn(playerSize / 2, height - playerSize / 2)

                touchStartX = event.x
                touchStartY = event.y

                invalidate()
            }
        }
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
        // Level geschafft wenn alle Münzen eingesammelt und Endzone erreicht
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
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // Hilfsklassen
    data class Enemy(
        var x: Float,
        var y: Float,
        var speedX: Float,
        var speedY: Float,
        val radius: Float
    ) {
        fun update(maxWidth: Float, maxHeight: Float) {
            x += speedX
            y += speedY

            // Wände abprallen
            if (x - radius < 0 || x + radius > maxWidth) {
                speedX = -speedX
            }
            if (y - radius < 0 || y + radius > maxHeight) {
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
