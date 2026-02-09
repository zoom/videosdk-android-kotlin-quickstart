package com.zoomvideosdkkotlin.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.zoomvideosdkkotlin.utils.ApiClient
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.zoomvideosdkkotlin.R
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.awaitResponse
import java.io.Serializable
import io.github.cdimascio.dotenv.dotenv

data class JWTOptions(
    @SerializedName("sessionName") val sessionName: String,
    @SerializedName("role") val role: Int,
    @SerializedName("userIdentity") val userIdentity: String,
    @SerializedName("sessionkey") val sessionkey: String,
    @SerializedName("geo_regions") val geo_regions: String,
    @SerializedName("cloud_recording_option") val cloud_recording_option: Int,
    @SerializedName("cloud_recording_election") val cloud_recording_election: Int,
    @SerializedName("telemetry_tracking_id") val telemetry_tracking_id: String,
    @SerializedName("video_webrtc_mode") val video_webrtc_mode: Int,
    @SerializedName("audio_webrtc_mode") val audio_webrtc_mode: Int,
)
data class Signature(val signature: String)
data class Config(val sessionName: String, val userName: String, val password: String?, val jwt: String ): Serializable

class JoinSession : AppCompatActivity() {
    val context: Context = this
    private val dotenv = dotenv {
        directory = "/assets"
        filename = "env"
    }

    private val endpointURL: String = dotenv["ENDPOINT_URL"]

    private lateinit var sessionNameTextField: TextInputLayout
    private lateinit var usernameTextField: TextInputLayout
    private lateinit var passwordTextField: TextInputLayout
    private lateinit var jwtTokenTextField: TextInputLayout
    private lateinit var joinSessionButton: Button
    private lateinit var sessionName: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var jwtToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_join_session)
        sessionNameTextField = findViewById(R.id.sessionNameTextField)
        usernameTextField = findViewById(R.id.usernameTextField)
        passwordTextField = findViewById(R.id.passwordTextField)
        jwtTokenTextField = findViewById(R.id.jwtTokenTextField)
        joinSessionButton = findViewById(R.id.joinsessionBtn)
        sessionName = findViewById<TextInputEditText>(R.id.sessionNameTextEditField).text.toString()
        username = findViewById<TextInputEditText>(R.id.usernameTextEditField).text.toString()
        password = findViewById<TextInputEditText>(R.id.passwordTextEditField).text.toString()
        jwtToken = findViewById<TextInputEditText>(R.id.jwtTokenTextEditField).text.toString()

        sessionNameTextField.editText?.doOnTextChanged { sessionName : CharSequence?, _:Int, _:Int, _:Int ->
            this.sessionName = sessionName.toString()
        }
        usernameTextField.editText?.doOnTextChanged { username: CharSequence?, _:Int, _:Int, _:Int ->
            this.username = username.toString()
        }
        passwordTextField.editText?.doOnTextChanged { password: CharSequence?, _:Int, _:Int, _:Int ->
            this.password = password.toString()
        }
        jwtTokenTextField.editText?.doOnTextChanged { jwtToken: CharSequence?, _:Int, _:Int, _:Int ->
            this.jwtToken = jwtToken.toString()
        }

        joinSessionButton.setOnClickListener {
            val body = JWTOptions(
                sessionName = sessionName,
                role = 1,
                userIdentity = null.toString(),
                sessionkey = null.toString(),
                geo_regions = null.toString(),
                cloud_recording_option = 0,
                cloud_recording_election = 0,
                telemetry_tracking_id = "internal-dev5",
                video_webrtc_mode = 0,
                audio_webrtc_mode = 0
            )

            if (endpointURL.isEmpty()) {
                println("JWT from local " + jwtToken)
                val config = Config(sessionName, username, password, jwtToken)
                val intent = Intent(context, InSession::class.java).apply {
                    putExtra("config", config)
                }
                startActivity(intent)
            } else {
                lifecycleScope.launch {
                    val response =
                        ApiClient.apiService.getJWT(sessionName, username, password, body)
                            .awaitResponse()
                    if (response.isSuccessful) {
                        val jwt = Gson().fromJson(response.body(), Signature::class.java)
                        val config = Config(sessionName, username, password, jwt.signature)
                        println("JWT from server " + jwt.signature)
                        val intent = Intent(context, InSession::class.java).apply {
                            putExtra("config", config)
                        }
                        startActivity(intent)
                    } else {
                        println("error")
                    }
                }
            }
        }
    }
}