package com.example.worldshardestgame_multiplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvSwitchMode: TextView

    private lateinit var authManager: AuthManager
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager()

        // Check if already logged in
        if (AuthManager.isLoggedIn()) {
            goToLobbyBrowser()
            return
        }

        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvSwitchMode = findViewById(R.id.tvSwitchMode)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            handleLogin()
        }

        btnRegister.setOnClickListener {
            handleRegister()
        }

        tvSwitchMode.setOnClickListener {
            toggleMode()
        }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode

        if (isLoginMode) {
            btnLogin.text = "Anmelden"
            btnRegister.text = "Registrieren"
            tvSwitchMode.text = "Noch kein Account? Jetzt registrieren"
        } else {
            btnLogin.text = "Account erstellen"
            btnRegister.text = "Zurück zum Login"
            tvSwitchMode.text = "Bereits einen Account? Jetzt anmelden"
        }
    }

    private fun handleLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Bitte alle Felder ausfüllen", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false
        btnRegister.isEnabled = false

        if (isLoginMode) {
            authManager.signIn(
                username = username,
                password = password,
                onSuccess = { user ->
                    Toast.makeText(this, "Willkommen zurück, ${user.username}!", Toast.LENGTH_SHORT).show()
                    goToLobbyBrowser()
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        btnRegister.isEnabled = true
                    }
                }
            )
        } else {
            authManager.signUp(
                username = username,
                password = password,
                onSuccess = { user ->
                    Toast.makeText(this, "Account erstellt! Willkommen, ${user.username}!", Toast.LENGTH_SHORT).show()
                    goToLobbyBrowser()
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        btnRegister.isEnabled = true
                    }
                }
            )
        }
    }

    private fun handleRegister() {
        if (isLoginMode) {
            toggleMode()
        } else {
            toggleMode()
        }
    }

    private fun goToLobbyBrowser() {
        val intent = Intent(this, LobbyBrowserActivity::class.java)
        startActivity(intent)
        finish()
    }
}

