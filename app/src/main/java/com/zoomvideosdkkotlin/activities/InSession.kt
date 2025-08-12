package com.zoomvideosdkkotlin.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.zoomvideosdkkotlin.viewmodel.ZoomSessionViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            permissionsMap.forEach { (permission, isGranted) ->
                when (permission) {
                    "android.permission.RECORD_AUDIO" -> recordAudioGranted = isGranted
                    "android.permission.CAMERA" -> cameraGranted = isGranted
                }
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
            setOnClickListener { zoomSessionViewModel.closeSession(true) }
        }
        findViewById<MaterialButton>(R.id.endbtn).apply {
            setOnClickListener { zoomSessionViewModel.closeSession(false) }
        }
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