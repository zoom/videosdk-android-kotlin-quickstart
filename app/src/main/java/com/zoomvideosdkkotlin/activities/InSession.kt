package com.zoomvideosdkkotlin.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.CompoundButton
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.zoomvideosdkkotlin.R
import com.zoomvideosdkkotlin.viewmodel.OnHoldParticipant
import com.zoomvideosdkkotlin.viewmodel.ZoomSessionViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import us.zoom.sdk.ZoomVideoSDKErrors
import us.zoom.sdk.ZoomVideoSDKVideoView


class InSession : AppCompatActivity() {
    private val zoomSessionViewModel by viewModels<ZoomSessionViewModel>()
    private lateinit var config: Config
    private var page: Int = 1
    private var recordAudioGranted: Boolean = false
    private var cameraGranted: Boolean = false
    private val permissions: Array<String> = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )
    private lateinit var waitingRoomOverlay: FrameLayout
    private lateinit var hostOptionsBtn: MaterialButton
    private lateinit var hostParticipantsPanel: FrameLayout
    private lateinit var hostAdmitPanel: FrameLayout
    private lateinit var participantsListContainer: LinearLayout
    private lateinit var onHoldListContainer: LinearLayout
    private lateinit var enableHoldInSessionSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private var suppressHoldSwitchListener: Boolean = false
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            permissionsMap.forEach { (permission, isGranted) ->
                when (permission) {
                    "android.permission.RECORD_AUDIO" -> recordAudioGranted = isGranted
                    "android.permission.CAMERA" -> cameraGranted = isGranted
                }
            }
        }

    private fun createUserRow(
        userName: String,
        actionText: String? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }
        val nameView = TextView(this).apply {
            text = userName
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(nameView)
        if (actionText != null && onClick != null) {
            val actionButton = MaterialButton(this).apply {
                text = actionText
                setOnClickListener { onClick() }
            }
            row.addView(actionButton)
        }
        return row
    }

    private fun renderParticipantsList() {
        participantsListContainer.removeAllViews()
        val users = zoomSessionViewModel.getAllSessionUsers()
        if (users.isEmpty()) {
            participantsListContainer.addView(createUserRow("No participants"))
            return
        }
        users.forEach { user ->
            participantsListContainer.addView(createUserRow(user.userName))
        }
    }

    private fun renderOnHoldList(onHoldParticipants: List<OnHoldParticipant>) {
        onHoldListContainer.removeAllViews()
        if (onHoldParticipants.isEmpty()) {
            onHoldListContainer.addView(createUserRow("No users waiting"))
            return
        }
        onHoldParticipants.forEach { user ->
            onHoldListContainer.addView(
                createUserRow(user.userName, "Admit") {
                    zoomSessionViewModel.admitOnHoldUser(user.userId)
                }
            )
        }
    }

    private fun manageUserView(
        userVideoOn: Boolean,
        userViewId: Int,
        userAvatarId: Int) {
            val view: ZoomVideoSDKVideoView = findViewById(userViewId)
            val avatar: ImageView = findViewById(userAvatarId)

            if (userVideoOn) {
                view.visibility = View.VISIBLE
                avatar.visibility = View.GONE
            } else {
                view.visibility = View.GONE
                avatar.visibility = View.VISIBLE
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_session)
        config = intent.getSerializableExtra("config") as Config
        val context: Context = this
        waitingRoomOverlay = findViewById(R.id.waitingRoomOverlay)
        hostOptionsBtn = findViewById(R.id.hostOptionsBtn)
        hostParticipantsPanel = findViewById(R.id.hostParticipantsPanel)
        hostAdmitPanel = findViewById(R.id.hostAdmitPanel)
        participantsListContainer = findViewById(R.id.participantsListContainer)
        onHoldListContainer = findViewById(R.id.onHoldListContainer)
        enableHoldInSessionSwitch = findViewById(R.id.enableHoldInSessionSwitch)

        val allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) requestMultiplePermissionsLauncher.launch(permissions)

        val audiobtn = findViewById<MaterialButton>(R.id.audiobtn).apply {
            setOnClickListener {
                if (!recordAudioGranted) requestMultiplePermissionsLauncher.launch(permissions)
                else zoomSessionViewModel.toggleMicrophone()
            }
        }
        val videobtn = findViewById<MaterialButton>(R.id.videobtn).apply {
            setOnClickListener {
                if (!cameraGranted) requestMultiplePermissionsLauncher.launch(permissions)
                else zoomSessionViewModel.toggleCamera()
            }
        }
        val forwardbtn = findViewById<MaterialButton>(R.id.forwardbtn).apply {
            setOnClickListener { zoomSessionViewModel.updateUsersInView(page + 1) }
        }
        val backbtn = findViewById<MaterialButton>(R.id.backbtn).apply {
            setOnClickListener { zoomSessionViewModel.updateUsersInView(page - 1) }
        }
        val closeSession = findViewById<LinearLayout>(R.id.closeSession)

        findViewById<MaterialButton>(R.id.closebtn).apply {
            setOnClickListener {
                closeSession.visibility = if (closeSession.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        findViewById<MaterialButton>(R.id.leavebtn).apply {
            setOnClickListener { zoomSessionViewModel.closeSession(false) }
        }
        findViewById<MaterialButton>(R.id.endbtn).apply {
            setOnClickListener { zoomSessionViewModel.closeSession(true) }
        }
        hostOptionsBtn.setOnClickListener {
            val menu = PopupMenu(this, hostOptionsBtn)
            menu.menu.add("Participants")
            menu.menu.add("Admit Participants")
            menu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title.toString()) {
                    "Participants" -> {
                        renderParticipantsList()
                        hostParticipantsPanel.visibility = View.VISIBLE
                    }
                    "Admit Participants" -> {
                        zoomSessionViewModel.refreshOnHoldUsersFromHelper()
                        renderOnHoldList(zoomSessionViewModel.getState().onHoldParticipants)
                        hostAdmitPanel.visibility = View.VISIBLE
                    }
                }
                true
            }
            menu.show()
        }
        findViewById<MaterialButton>(R.id.closeParticipantsPanelBtn).setOnClickListener {
            hostParticipantsPanel.visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.closeAdmitPanelBtn).setOnClickListener {
            hostAdmitPanel.visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.admitAllBtn).setOnClickListener {
            zoomSessionViewModel.admitAllOnHoldUsers()
        }
        val holdSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (suppressHoldSwitchListener) return@OnCheckedChangeListener
            val result = zoomSessionViewModel.setHoldEnabled(isChecked)
            if (result != ZoomVideoSDKErrors.Errors_Success) {
                suppressHoldSwitchListener = true
                enableHoldInSessionSwitch.isChecked = !isChecked
                suppressHoldSwitchListener = false
            }
        }
        enableHoldInSessionSwitch.setOnCheckedChangeListener(holdSwitchListener)
        findViewById<TextView>(R.id.title).text = config.sessionName

        zoomSessionViewModel.setSelfViews(findViewById(R.id.bigselfview), findViewById(R.id.draggableselfview))
        zoomSessionViewModel.setViews(listOf(
            findViewById(R.id.participant1),
            findViewById(R.id.participant2),
            findViewById(R.id.participant3),
            findViewById(R.id.participant4))
        )
        zoomSessionViewModel.initZoomSDK()
        zoomSessionViewModel.joinSession(config)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                //Icons and Button
                launch {
                    zoomSessionViewModel.zoomSessionUIState.distinctUntilChanged { old, new ->
                        old.muted == new.muted && old.isVideoOn == new.isVideoOn && old.maxPages == new.maxPages && old.pageNumber == new.pageNumber
                    }.collect {
                        audiobtn.icon = if (it.muted)
                            AppCompatResources.getDrawable(this@InSession, R.drawable.mic_off_24px)
                        else AppCompatResources.getDrawable(this@InSession, R.drawable.mic_24px)

                        videobtn.icon = if (it.isVideoOn)
                            AppCompatResources.getDrawable(this@InSession, R.drawable.videocam_24px)
                        else AppCompatResources.getDrawable(this@InSession, R.drawable.videocam_off_24px)
                    }
                }

                //Self View
                launch {
                    zoomSessionViewModel.zoomSessionUIState.distinctUntilChanged { old, new ->
                        old.isVideoOn == new.isVideoOn && old.currentUsersInViewCount == new.currentUsersInViewCount
                    }.collect {
                        findViewById<FrameLayout>(R.id.bigselfview_container).visibility = if (it.currentUsersInViewCount == 0) View.VISIBLE else View.GONE
                        findViewById<FrameLayout>(R.id.draggableselfview_container).visibility = if (it.currentUsersInViewCount != 0) View.VISIBLE else View.GONE

                        manageUserView(
                            userVideoOn = it.isVideoOn,
                            userViewId = if (it.currentUsersInViewCount == 0) R.id.bigselfview else R.id.draggableselfview,
                            userAvatarId = if (it.currentUsersInViewCount == 0) R.id.bigselfview_avatar else R.id.draggableselfview_avatar)
                    }
                }

                //Gallery View
                launch {
                    zoomSessionViewModel.zoomSessionUIState.distinctUntilChanged { old, new ->
                        old.currentUsersInViewCount == new.currentUsersInViewCount && old.participantVideoOn == new.participantVideoOn
                    }.collect {
                        val viewWidth = if (it.currentUsersInViewCount > 1)
                            applicationContext.resources.displayMetrics.widthPixels / 2
                            else applicationContext.resources.displayMetrics.widthPixels
                        val currentUsersInView = zoomSessionViewModel.getCurrentUsersInView()

                        if (it.currentUsersInViewCount > 0) {
                            findViewById<FrameLayout>(R.id.participant1_container).apply {
                                layoutParams.width = viewWidth - 20
                                layoutParams.height = viewWidth - 20
                                visibility = View.VISIBLE
                            }
                            findViewById<TextView>(R.id.participant_nametag1).apply {
                                text = currentUsersInView[0].userName
                            }
                            manageUserView(
                                userVideoOn = it.participantVideoOn[0],
                                userViewId = R.id.participant1,
                                userAvatarId = R.id.participant_avatar1)
                        } else findViewById<FrameLayout>(R.id.participant1_container).visibility = View.GONE

                        if (it.currentUsersInViewCount > 1) {
                            findViewById<FrameLayout>(R.id.participant2_container).apply {
                                layoutParams.width = viewWidth - 20
                                layoutParams.height = viewWidth - 20
                                visibility = View.VISIBLE
                            }
                            findViewById<TextView>(R.id.participant_nametag2).apply {
                                text = currentUsersInView[0].userName
                            }
                            manageUserView(
                                userVideoOn = it.participantVideoOn[1],
                                userViewId = R.id.participant2,
                                userAvatarId = R.id.participant_avatar2)
                        } else findViewById<FrameLayout>(R.id.participant2_container).visibility = View.GONE

                        if (it.currentUsersInViewCount > 2) {
                            findViewById<FrameLayout>(R.id.participant3_container).apply {
                                layoutParams.width = viewWidth - 20
                                layoutParams.height = viewWidth - 20
                                visibility = View.VISIBLE
                            }
                            findViewById<TextView>(R.id.participant_nametag3).apply {
                                text = currentUsersInView[0].userName
                            }
                            manageUserView(
                                userVideoOn = it.participantVideoOn[2],
                                userViewId = R.id.participant3,
                                userAvatarId = R.id.participant_avatar3)
                        } else findViewById<FrameLayout>(R.id.participant3_container).visibility = View.GONE

                        if (it.currentUsersInViewCount > 3) {
                            findViewById<FrameLayout>(R.id.participant4_container).apply {
                                layoutParams.width = viewWidth - 20
                                layoutParams.height = viewWidth - 20
                                visibility = View.VISIBLE
                            }
                            findViewById<TextView>(R.id.participant_nametag4).apply {
                                text = currentUsersInView[0].userName
                            }
                            manageUserView(
                                userVideoOn = it.participantVideoOn[3],
                                userViewId = R.id.participant4,
                                userAvatarId = R.id.participant_avatar4)
                        } else findViewById<FrameLayout>(R.id.participant4_container).visibility = View.GONE
                    }
                }

                launch {
                    zoomSessionViewModel.zoomSessionUIState.collect {
                        if (it.inSession) {
                            page = it.pageNumber
                            backbtn.visibility =
                                if (it.maxPages > 1 && it.pageNumber != 1) View.VISIBLE else View.GONE
                            forwardbtn.visibility =
                                if (it.maxPages > 1 && it.maxPages != it.pageNumber) View.VISIBLE else View.GONE
                            waitingRoomOverlay.visibility =
                                if (it.waitingForHostAdmission) View.VISIBLE else View.GONE
                            hostOptionsBtn.visibility = if (it.isHost) View.VISIBLE else View.GONE
                            suppressHoldSwitchListener = true
                            enableHoldInSessionSwitch.isChecked = it.holdEnabled
                            suppressHoldSwitchListener = false
                            if (hostParticipantsPanel.visibility == View.VISIBLE) renderParticipantsList()
                            if (hostAdmitPanel.visibility == View.VISIBLE) renderOnHoldList(it.onHoldParticipants)
                        } else {
                            val intent = Intent(context, JoinSession::class.java)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}