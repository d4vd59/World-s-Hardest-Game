package com.example.worldshardestgame_multiplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.worldshardestgame_multiplayer.models.Lobby
import com.google.firebase.database.*

class LobbyBrowserActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var btnCreateLobby: Button
    private lateinit var btnFriends: Button
    private lateinit var btnLogout: Button
    private lateinit var btnRefresh: Button
    private lateinit var rvLobbies: RecyclerView
    private lateinit var tvNoLobbies: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var firebaseManager: FirebaseManager
    private lateinit var authManager: AuthManager

    private val lobbies = mutableListOf<Lobby>()
    private lateinit var lobbyAdapter: LobbyAdapter

    private var lobbiesListener: ValueEventListener? = null
    private var currentInvitationDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby_browser)

        firebaseManager = FirebaseManager()
        authManager = AuthManager()

        // Check if logged in
        if (!AuthManager.isLoggedIn()) {
            goToLogin()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupListeners()
        loadLobbies()
        setupInvitationListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Listener aufräumen
        lobbiesListener?.let {
            firebaseManager.gamesRef.removeEventListener(it)
        }
        currentInvitationDialog?.dismiss()
    }

    private fun initializeViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        btnCreateLobby = findViewById(R.id.btnCreateLobby)
        btnFriends = findViewById(R.id.btnFriends)
        btnLogout = findViewById(R.id.btnLogout)
        btnRefresh = findViewById(R.id.btnRefresh)
        rvLobbies = findViewById(R.id.rvLobbies)
        tvNoLobbies = findViewById(R.id.tvNoLobbies)
        progressBar = findViewById(R.id.progressBar)

        val currentUser = AuthManager.getCurrentUser()
        tvWelcome.text = "Willkommen, ${currentUser?.username}! 👋"
    }

    private fun setupRecyclerView() {
        lobbyAdapter = LobbyAdapter(
            lobbies = lobbies,
            onJoinClick = { lobby -> joinLobby(lobby) },
            currentUserId = AuthManager.getCurrentUser()?.userId ?: ""
        )

        rvLobbies.layoutManager = LinearLayoutManager(this)
        rvLobbies.adapter = lobbyAdapter
    }

    private fun setupListeners() {
        btnCreateLobby.setOnClickListener {
            showCreateLobbyDialog()
        }

        btnFriends.setOnClickListener {
            val intent = Intent(this, FriendsActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            authManager.signOut()
            goToLogin()
        }

        btnRefresh.setOnClickListener {
            loadLobbies()
        }
    }

    private fun loadLobbies() {
        progressBar.visibility = View.VISIBLE

        // Cleanup leere Lobbys beim Laden
        cleanupEmptyLobbies()

        // Remove old listener
        lobbiesListener?.let {
            firebaseManager.gamesRef.removeEventListener(it)
        }

        // Add new listener
        lobbiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lobbies.clear()

                for (gameSnapshot in snapshot.children) {
                    try {
                        val gameId = gameSnapshot.key ?: continue
                        val lobbyName = gameSnapshot.child("lobbyName").getValue(String::class.java) ?: continue
                        val hostId = gameSnapshot.child("hostId").getValue(String::class.java) ?: continue
                        val status = gameSnapshot.child("status").getValue(String::class.java) ?: "waiting"
                        val currentLevel = gameSnapshot.child("currentLevel").getValue(Int::class.java) ?: 1
                        val isPublic = gameSnapshot.child("isPublic").getValue(Boolean::class.java) ?: true
                        val maxPlayers = gameSnapshot.child("maxPlayers").getValue(Int::class.java) ?: 4
                        val createdAt = gameSnapshot.child("createdAt").getValue(Long::class.java) ?: 0

                        // Count players
                        val playersSnapshot = gameSnapshot.child("players")
                        val currentPlayers = playersSnapshot.childrenCount.toInt()

                        // *** WICHTIG: Ignoriere leere Lobbys ***
                        if (currentPlayers == 0) {
                            // Lösche leere Lobby sofort
                            firebaseManager.gamesRef.child(gameId).removeValue()
                            firebaseManager.deleteInvitationsForGame(gameId)
                            Log.d("LobbyBrowser", "Deleted empty lobby: $gameId")
                            continue
                        }

                        // Get host username
                        val hostUsername = playersSnapshot.child(hostId).child("name").getValue(String::class.java) ?: "Unknown"

                        // Get invited users
                        val invitedUserIds = mutableListOf<String>()
                        gameSnapshot.child("invitedUsers").children.forEach { inviteSnapshot ->
                            inviteSnapshot.getValue(String::class.java)?.let { invitedUserIds.add(it) }
                        }

                        // Get player usernames
                        val playerUsernames = mutableMapOf<String, String>()
                        playersSnapshot.children.forEach { playerSnapshot ->
                            val playerId = playerSnapshot.key ?: return@forEach
                            val playerName = playerSnapshot.child("name").getValue(String::class.java) ?: return@forEach
                            playerUsernames[playerId] = playerName
                        }

                        val lobby = Lobby(
                            gameId = gameId,
                            lobbyName = lobbyName,
                            hostUserId = hostId,
                            hostUsername = hostUsername,
                            isPublic = isPublic,
                            maxPlayers = maxPlayers,
                            currentPlayers = currentPlayers,
                            status = status,
                            currentLevel = currentLevel,
                            createdAt = createdAt,
                            invitedUserIds = invitedUserIds,
                            playerUsernames = playerUsernames
                        )

                        // Filter: Show only waiting lobbies WITH PLAYERS
                        // For public lobbies: show all (with players)
                        // For private lobbies: only show if user is invited or is host (with players)
                        val currentUserId = AuthManager.getCurrentUser()?.userId
                        if (status == "waiting" && currentPlayers > 0) {
                            if (isPublic || currentUserId == hostId || invitedUserIds.contains(currentUserId)) {
                                lobbies.add(lobby)
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("LobbyBrowser", "Error parsing lobby", e)
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    lobbyAdapter.notifyDataSetChanged()

                    if (lobbies.isEmpty()) {
                        tvNoLobbies.visibility = View.VISIBLE
                        rvLobbies.visibility = View.GONE
                    } else {
                        tvNoLobbies.visibility = View.GONE
                        rvLobbies.visibility = View.VISIBLE
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LobbyBrowser", "Error loading lobbies", error.toException())
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LobbyBrowserActivity, "Fehler beim Laden: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        firebaseManager.gamesRef.addValueEventListener(lobbiesListener!!)
    }

    private fun showCreateLobbyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_lobby, null)
        val etLobbyName = dialogView.findViewById<EditText>(R.id.etLobbyName)
        val switchPublic = dialogView.findViewById<Switch>(R.id.switchPublic)
        val etInviteUsername = dialogView.findViewById<EditText>(R.id.etInviteUsername)
        val btnAddInvite = dialogView.findViewById<Button>(R.id.btnAddInvite)
        val tvInvitedUsers = dialogView.findViewById<TextView>(R.id.tvInvitedUsers)

        val invitedUsers = mutableMapOf<String, String>() // userId -> username

        switchPublic.setOnCheckedChangeListener { _, isChecked ->
            etInviteUsername.visibility = if (isChecked) View.GONE else View.VISIBLE
            btnAddInvite.visibility = if (isChecked) View.GONE else View.VISIBLE
            tvInvitedUsers.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        btnAddInvite.setOnClickListener {
            val username = etInviteUsername.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(this, "Bitte Username eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authManager.findUserByUsername(
                username = username,
                onSuccess = { user ->
                    runOnUiThread {
                        if (user == null) {
                            Toast.makeText(this, "User '$username' nicht gefunden", Toast.LENGTH_SHORT).show()
                        } else if (user.userId == AuthManager.getCurrentUser()?.userId) {
                            Toast.makeText(this, "Du kannst dich nicht selbst einladen", Toast.LENGTH_SHORT).show()
                        } else if (invitedUsers.containsKey(user.userId)) {
                            Toast.makeText(this, "User bereits eingeladen", Toast.LENGTH_SHORT).show()
                        } else {
                            invitedUsers[user.userId] = user.username
                            tvInvitedUsers.text = "Eingeladen: ${invitedUsers.values.joinToString(", ")}"
                            etInviteUsername.text.clear()
                            Toast.makeText(this, "✅ ${user.username} eingeladen", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Lobby erstellen")
            .setView(dialogView)
            .setPositiveButton("Erstellen") { _, _ ->
                val lobbyName = etLobbyName.text.toString().trim()
                val isPublic = switchPublic.isChecked

                if (lobbyName.isEmpty()) {
                    Toast.makeText(this, "Bitte Lobby-Namen eingeben", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createLobby(lobbyName, isPublic, invitedUsers)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun createLobby(lobbyName: String, isPublic: Boolean, invitedUsers: Map<String, String>) {
        val currentUser = AuthManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Nicht angemeldet", Toast.LENGTH_SHORT).show()
            return
        }

        btnCreateLobby.isEnabled = false

        firebaseManager.createGameWithAuth(
            hostUserId = currentUser.userId,
            hostName = currentUser.username,
            lobbyName = lobbyName,
            isPublic = isPublic,
            invitedUserIds = invitedUsers.keys.toList(),
            onGameCreated = { gameId, playerId ->
                // Sende Einladungen an alle eingeladenen User
                if (!isPublic && invitedUsers.isNotEmpty()) {
                    invitedUsers.forEach { (userId, username) ->
                        firebaseManager.sendInvitation(
                            gameId = gameId,
                            lobbyName = lobbyName,
                            fromUserId = currentUser.userId,
                            fromUsername = currentUser.username,
                            toUserId = userId,
                            toUsername = username,
                            onSuccess = {
                                Log.d("LobbyBrowser", "Invitation sent to: $username")
                            },
                            onError = { error ->
                                Log.e("LobbyBrowser", "Error sending invitation: $error")
                            }
                        )
                    }
                }

                runOnUiThread {
                    val inviteMsg = if (invitedUsers.isNotEmpty()) " Einladungen gesendet!" else ""
                    Toast.makeText(this, "Lobby erstellt!$inviteMsg ✅", Toast.LENGTH_SHORT).show()
                    goToMainActivity(gameId, playerId, currentUser.username)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                    btnCreateLobby.isEnabled = true
                }
            }
        )
    }

    private fun joinLobby(lobby: Lobby) {
        val currentUser = AuthManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Nicht angemeldet", Toast.LENGTH_SHORT).show()
            return
        }

        if (lobby.currentPlayers >= lobby.maxPlayers) {
            Toast.makeText(this, "Lobby ist voll", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if private and not invited
        if (!lobby.isPublic && !lobby.invitedUserIds.contains(currentUser.userId) && lobby.hostUserId != currentUser.userId) {
            Toast.makeText(this, "Diese Lobby ist privat", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseManager.joinGameWithAuth(
            gameId = lobby.gameId,
            userId = currentUser.userId,
            playerName = currentUser.username,
            onSuccess = { playerId ->
                runOnUiThread {
                    Toast.makeText(this, "Lobby beigetreten! ✅", Toast.LENGTH_SHORT).show()
                    goToMainActivity(lobby.gameId, playerId, currentUser.username)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToMainActivity(gameId: String, playerId: String, playerName: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("GAME_ID", gameId)
        intent.putExtra("PLAYER_ID", playerId)
        intent.putExtra("PLAYER_NAME", playerName)
        startActivity(intent)
        finish()
    }

    /**
     * Löscht automatisch alle leeren Lobbys aus der Datenbank
     */
    private fun cleanupEmptyLobbies() {
        firebaseManager.cleanupEmptyGames { success, count ->
            if (success && count > 0) {
                Log.d("LobbyBrowser", "Cleanup: $count leere Lobbys gelöscht")
            }
        }
    }

    /**
     * Richtet den Einladungs-Listener ein
     */
    private fun setupInvitationListener() {
        val currentUser = AuthManager.getCurrentUser() ?: return

        firebaseManager.listenToInvitations(currentUser.userId) { invitation ->
            runOnUiThread {
                // Prüfe erst ob die Lobby noch existiert
                firebaseManager.isLobbyActive(invitation.gameId) { isActive ->
                    runOnUiThread {
                        if (isActive) {
                            showInvitationDialog(invitation)
                        } else {
                            // Lobby existiert nicht mehr - Einladung automatisch ablehnen
                            firebaseManager.declineInvitation(invitation.invitationId, currentUser.userId) {}
                        }
                    }
                }
            }
        }
    }

    /**
     * Zeigt den Einladungs-Dialog
     */
    private fun showInvitationDialog(invitation: com.example.worldshardestgame_multiplayer.models.Invitation) {
        // Schließe vorherigen Dialog falls vorhanden
        currentInvitationDialog?.dismiss()


        currentInvitationDialog = AlertDialog.Builder(this)
            .setTitle("🎮 Lobby-Einladung")
            .setMessage("${invitation.fromUsername} hat dich zu '${invitation.lobbyName}' eingeladen!\n\nMöchtest du beitreten?")
            .setPositiveButton("✅ Annehmen") { dialog, _ ->
                dialog.dismiss()
                acceptInvitation(invitation)
            }
            .setNegativeButton("❌ Ablehnen") { dialog, _ ->
                dialog.dismiss()
                declineInvitation(invitation)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Akzeptiert eine Einladung
     */
    private fun acceptInvitation(invitation: com.example.worldshardestgame_multiplayer.models.Invitation) {
        val currentUser = AuthManager.getCurrentUser() ?: return

        btnCreateLobby.isEnabled = false
        progressBar.visibility = View.VISIBLE

        firebaseManager.acceptInvitation(
            invitation = invitation,
            userId = currentUser.userId,
            username = currentUser.username,
            onSuccess = { playerId ->
                runOnUiThread {
                    Toast.makeText(this, "✅ Einladung angenommen!", Toast.LENGTH_SHORT).show()
                    goToMainActivity(invitation.gameId, playerId, currentUser.username)
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnCreateLobby.isEnabled = true
                    Toast.makeText(this, "❌ $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    /**
     * Lehnt eine Einladung ab
     */
    private fun declineInvitation(invitation: com.example.worldshardestgame_multiplayer.models.Invitation) {
        val currentUser = AuthManager.getCurrentUser() ?: return

        firebaseManager.declineInvitation(
            invitationId = invitation.invitationId,
            userId = currentUser.userId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Einladung abgelehnt", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

}

// Adapter für Lobby-Liste
class LobbyAdapter(
    private val lobbies: List<Lobby>,
    private val onJoinClick: (Lobby) -> Unit,
    private val currentUserId: String
) : RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder>() {

    class LobbyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLobbyName: TextView = view.findViewById(R.id.tvLobbyName)
        val tvHostName: TextView = view.findViewById(R.id.tvHostName)
        val tvPlayerCount: TextView = view.findViewById(R.id.tvPlayerCount)
        val tvLobbyType: TextView = view.findViewById(R.id.tvLobbyType)
        val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        val btnJoin: Button = view.findViewById(R.id.btnJoin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LobbyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lobby, parent, false)
        return LobbyViewHolder(view)
    }

    override fun onBindViewHolder(holder: LobbyViewHolder, position: Int) {
        val lobby = lobbies[position]

        holder.tvLobbyName.text = lobby.lobbyName
        holder.tvHostName.text = "Host: ${lobby.hostUsername}"
        holder.tvPlayerCount.text = "Spieler: ${lobby.currentPlayers}/${lobby.maxPlayers}"
        holder.tvLobbyType.text = if (lobby.isPublic) "🌐 Öffentlich" else "🔒 Privat"
        holder.tvLevel.text = "Level ${lobby.currentLevel}"

        // Disable join button if lobby is full
        val isFull = lobby.currentPlayers >= lobby.maxPlayers
        holder.btnJoin.isEnabled = !isFull
        holder.btnJoin.text = if (isFull) "Voll" else "Beitreten"

        holder.btnJoin.setOnClickListener {
            onJoinClick(lobby)
        }
    }

    override fun getItemCount() = lobbies.size
}

