package com.example.worldshardestgame_multiplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.worldshardestgame_multiplayer.models.Friend
import com.example.worldshardestgame_multiplayer.models.FriendRequest
import com.example.worldshardestgame_multiplayer.models.User

class FriendsActivity : AppCompatActivity() {

    private lateinit var firebaseManager: FirebaseManager
    private lateinit var authManager: AuthManager

    private lateinit var lvFriends: ListView
    private lateinit var lvFriendRequests: ListView
    private lateinit var btnAddFriend: Button
    private lateinit var btnBack: Button
    private lateinit var tvNoFriends: TextView
    private lateinit var tvNoRequests: TextView

    private var currentUserId: String = ""
    private var currentUsername: String = ""
    private var currentGameId: String? = null
    private var currentLobbyName: String? = null

    private val friendsList = mutableListOf<Friend>()
    private val requestsList = mutableListOf<FriendRequest>()

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var requestsAdapter: FriendRequestsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        firebaseManager = FirebaseManager()
        authManager = AuthManager()

        // Hole gameId und lobbyName aus Intent (falls vorhanden)
        currentGameId = intent.getStringExtra("GAME_ID")
        currentLobbyName = intent.getStringExtra("LOBBY_NAME")

        lvFriends = findViewById(R.id.lvFriends)
        lvFriendRequests = findViewById(R.id.lvFriendRequests)
        btnAddFriend = findViewById(R.id.btnAddFriend)
        btnBack = findViewById(R.id.btnBack)
        tvNoFriends = findViewById(R.id.tvNoFriends)
        tvNoRequests = findViewById(R.id.tvNoRequests)

        // Hole aktuelle User-Info
        val user = AuthManager.getCurrentUser()
        if (user != null) {
            currentUserId = user.userId
            currentUsername = user.username

            setupAdapters()
            loadFriends()
            loadFriendRequests()
        } else {
            Toast.makeText(this, "Nicht eingeloggt", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Bereinige Status-Listener
        firebaseManager.clearFriendStatusListeners()
    }

    private fun setupAdapters() {
        friendsAdapter = FriendsAdapter(friendsList)
        lvFriends.adapter = friendsAdapter

        requestsAdapter = FriendRequestsAdapter(requestsList)
        lvFriendRequests.adapter = requestsAdapter
    }

    private fun loadFriends() {
        firebaseManager.getFriends(currentUserId) { friends ->
            runOnUiThread {
                friendsList.clear()
                friendsList.addAll(friends)
                friendsAdapter.notifyDataSetChanged()

                tvNoFriends.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                lvFriends.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadFriendRequests() {
        firebaseManager.getPendingFriendRequests(currentUserId) { requests ->
            runOnUiThread {
                requestsList.clear()
                requestsList.addAll(requests)
                requestsAdapter.notifyDataSetChanged()

                tvNoRequests.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
                lvFriendRequests.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showAddFriendDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_friend, null)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchUsername)
        val btnSearch = dialogView.findViewById<Button>(R.id.btnSearch)
        val lvResults = dialogView.findViewById<ListView>(R.id.lvSearchResults)
        val tvNoResults = dialogView.findViewById<TextView>(R.id.tvNoResults)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Freund hinzufügen")
            .setView(dialogView)
            .setNegativeButton("Abbrechen", null)
            .create()

        val searchResults = mutableListOf<User>()
        val searchAdapter = SearchResultsAdapter(searchResults)
        lvResults.adapter = searchAdapter

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "Bitte Benutzernamen eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseManager.searchUsers(query, currentUserId) { users ->
                runOnUiThread {
                    searchResults.clear()
                    searchResults.addAll(users)
                    searchAdapter.notifyDataSetChanged()

                    tvNoResults.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                    lvResults.visibility = if (users.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        lvResults.setOnItemClickListener { _, _, position, _ ->
            val user = searchResults[position]
            sendFriendRequest(user)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun sendFriendRequest(toUser: User) {
        firebaseManager.sendFriendRequest(
            fromUserId = currentUserId,
            fromUsername = currentUsername,
            toUserId = toUser.userId,
            toUsername = toUser.username,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Anfrage an ${toUser.username} gesendet", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ==================== ADAPTERS ====================

    inner class FriendsAdapter(private val friends: List<Friend>) : BaseAdapter() {
        override fun getCount() = friends.size
        override fun getItem(position: Int) = friends[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_friend, parent, false)
            val friend = friends[position]

            val tvUsername = view.findViewById<TextView>(R.id.tvFriendUsername)
            val tvStatus = view.findViewById<TextView>(R.id.tvFriendStatus)
            val btnInvite = view.findViewById<Button>(R.id.btnInviteFriend)
            val btnRemove = view.findViewById<Button>(R.id.btnRemoveFriend)

            tvUsername.text = friend.username
            tvStatus.text = ""

            btnInvite.setOnClickListener {
                if (currentGameId != null && currentLobbyName != null) {
                    inviteFriendToLobby(friend)
                } else {
                    Toast.makeText(this@FriendsActivity, "Keine aktive Lobby vorhanden", Toast.LENGTH_SHORT).show()
                }
            }

            btnRemove.setOnClickListener {
                showRemoveFriendDialog(friend)
            }

            return view
        }
    }

    inner class FriendRequestsAdapter(private val requests: List<FriendRequest>) : BaseAdapter() {
        override fun getCount() = requests.size
        override fun getItem(position: Int) = requests[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_friend_request, parent, false)
            val request = requests[position]

            val tvUsername = view.findViewById<TextView>(R.id.tvRequestUsername)
            val btnAccept = view.findViewById<Button>(R.id.btnAcceptRequest)
            val btnReject = view.findViewById<Button>(R.id.btnRejectRequest)

            tvUsername.text = "Anfrage von ${request.fromUsername}"

            btnAccept.setOnClickListener {
                acceptFriendRequest(request)
            }

            btnReject.setOnClickListener {
                rejectFriendRequest(request)
            }

            return view
        }
    }

    inner class SearchResultsAdapter(private val users: List<User>) : BaseAdapter() {
        override fun getCount() = users.size
        override fun getItem(position: Int) = users[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_user_search, parent, false)
            val user = users[position]

            val tvUsername = view.findViewById<TextView>(R.id.tvSearchUsername)
            val tvStats = view.findViewById<TextView>(R.id.tvSearchStats)

            tvUsername.text = user.username
            tvStats.text = "Spiele: ${user.gamesPlayed} | Siege: ${user.gamesWon}"

            return view
        }
    }

    // ==================== ACTIONS ====================

    private fun acceptFriendRequest(request: FriendRequest) {
        firebaseManager.acceptFriendRequest(
            requestId = request.requestId,
            fromUserId = request.fromUserId,
            fromUsername = request.fromUsername,
            toUserId = currentUserId,
            toUsername = currentUsername,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "${request.fromUsername} zu Freunden hinzugefügt", Toast.LENGTH_SHORT).show()
                    loadFriends()
                    loadFriendRequests()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun rejectFriendRequest(request: FriendRequest) {
        firebaseManager.rejectFriendRequest(
            requestId = request.requestId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Anfrage abgelehnt", Toast.LENGTH_SHORT).show()
                    loadFriendRequests()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showRemoveFriendDialog(friend: Friend) {
        AlertDialog.Builder(this)
            .setTitle("Freund entfernen")
            .setMessage("Möchtest du ${friend.username} wirklich aus deiner Freundesliste entfernen?")
            .setPositiveButton("Entfernen") { _, _ ->
                removeFriend(friend)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun removeFriend(friend: Friend) {
        firebaseManager.removeFriend(
            userId = currentUserId,
            friendId = friend.userId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "${friend.username} entfernt", Toast.LENGTH_SHORT).show()
                    loadFriends()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun inviteFriendToLobby(friend: Friend) {
        if (currentGameId == null || currentLobbyName == null) {
            Toast.makeText(this, "Keine aktive Lobby vorhanden", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseManager.inviteFriendToLobby(
            gameId = currentGameId!!,
            lobbyName = currentLobbyName!!,
            fromUserId = currentUserId,
            fromUsername = currentUsername,
            toUserId = friend.userId,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Einladung an ${friend.username} gesendet", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Fehler: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

