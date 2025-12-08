package com.example.worldshardestgame_multiplayer

import android.util.Log
import com.example.worldshardestgame_multiplayer.models.User
import com.google.firebase.database.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

class AuthManager {
    private val database = FirebaseDatabase.getInstance("https://world-s-hardest-game-default-rtdb.europe-west1.firebasedatabase.app/")
    private val usersRef = database.getReference("users")

    companion object {
        private const val TAG = "AuthManager"

        // Singleton für aktuellen User
        private var currentUser: User? = null

        fun getCurrentUser(): User? = currentUser
        fun setCurrentUser(user: User?) {
            currentUser = user
        }

        fun isLoggedIn(): Boolean = currentUser != null
    }

    /**
     * Generiert ein zufälliges Salt
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /**
     * Hasht das Passwort mit Salt
     */
    private fun hashPassword(password: String, salt: String): String {
        val saltedPassword = password + salt
        val bytes = saltedPassword.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getEncoder().encodeToString(digest)
    }

    /**
     * Registriert einen neuen User
     */
    fun signUp(
        username: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        // Validierung
        if (username.length < 3) {
            onError("Username muss mindestens 3 Zeichen lang sein")
            return
        }

        if (password.length < 6) {
            onError("Passwort muss mindestens 6 Zeichen lang sein")
            return
        }

        // Prüfen ob Username bereits existiert
        usersRef.orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        onError("Username existiert bereits")
                        return
                    }

                    // User erstellen
                    val userId = usersRef.push().key
                    if (userId == null) {
                        onError("Fehler beim Erstellen der User-ID")
                        return
                    }

                    val salt = generateSalt()
                    val passwordHash = hashPassword(password, salt)

                    val user = User(
                        userId = userId,
                        username = username,
                        passwordHash = passwordHash,
                        salt = salt,
                        createdAt = System.currentTimeMillis(),
                        gamesPlayed = 0,
                        gamesWon = 0
                    )

                    usersRef.child(userId).setValue(user)
                        .addOnSuccessListener {
                            Log.d(TAG, "User created: $username")
                            currentUser = user
                            onSuccess(user)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error creating user", e)
                            onError("Fehler beim Erstellen des Users: ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error checking username", error.toException())
                    onError("Fehler beim Prüfen des Usernames: ${error.message}")
                }
            })
    }

    /**
     * Meldet einen User an
     */
    fun signIn(
        username: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        usersRef.orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onError("Username nicht gefunden")
                        return
                    }

                    val userSnapshot = snapshot.children.first()
                    val user = userSnapshot.getValue(User::class.java)

                    if (user == null) {
                        onError("Fehler beim Laden der User-Daten")
                        return
                    }

                    // Passwort prüfen
                    val passwordHash = hashPassword(password, user.salt)
                    if (passwordHash != user.passwordHash) {
                        onError("Falsches Passwort")
                        return
                    }

                    Log.d(TAG, "User logged in: $username")
                    currentUser = user
                    onSuccess(user)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error signing in", error.toException())
                    onError("Fehler beim Anmelden: ${error.message}")
                }
            })
    }

    /**
     * Meldet den aktuellen User ab
     */
    fun signOut() {
        currentUser = null
        Log.d(TAG, "User logged out")
    }

    /**
     * Sucht einen User nach Username
     */
    fun findUserByUsername(
        username: String,
        onSuccess: (User?) -> Unit,
        onError: (String) -> Unit
    ) {
        usersRef.orderByChild("username").equalTo(username)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onSuccess(null)
                        return
                    }

                    val userSnapshot = snapshot.children.first()
                    val user = userSnapshot.getValue(User::class.java)
                    onSuccess(user)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error finding user", error.toException())
                    onError("Fehler beim Suchen des Users: ${error.message}")
                }
            })
    }
}

