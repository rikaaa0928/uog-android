package moe.rikaaa0928.uot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object {
        init {
            System.loadLibrary("uog")
        }
    }

    private lateinit var editTextListenPort: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextGrpcEndpoint: EditText

    private lateinit var switchStart: Switch
    private lateinit var buttonSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTextListenPort = findViewById(R.id.editTextListenPort)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextGrpcEndpoint = findViewById(R.id.editTextGrpcEndpoint)
        switchStart = findViewById(R.id.switchStart)
        buttonSave = findViewById(R.id.buttonSave)

        loadPreferences()

        switchStart.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                savePreferences()
                startGrpcService()
            } else {
                savePreferences()
                stopGrpcService()
            }
        }

        buttonSave.setOnClickListener {
            if (switchStart.isChecked) {
                //restart
            }
            savePreferences()
        }
        val initer = Init()
        initer.callInit(baseContext)
    }

    private fun startGrpcService() {
        val intent = Intent(this, UotGrpc::class.java)
        startForegroundService(intent)
    }

    private fun stopGrpcService() {
        val intent = Intent(this, UotGrpc::class.java)
        stopService(intent)
    }

    private fun savePreferences() {
        val sharedPreferences = getSharedPreferences("AppConfig", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("ListenPort", editTextListenPort.text.toString())
        editor.putString("Password", editTextPassword.text.toString())
        editor.putString("GrpcEndpoint", editTextGrpcEndpoint.text.toString())
        editor.putBoolean("Start", switchStart.isChecked)
        editor.apply()
    }

    private fun loadPreferences() {
        val sharedPreferences = getSharedPreferences("AppConfig", MODE_PRIVATE)
        editTextListenPort.setText(sharedPreferences.getString("ListenPort", ""))
        editTextPassword.setText(sharedPreferences.getString("Password", ""))
        editTextGrpcEndpoint.setText(sharedPreferences.getString("GrpcEndpoint", ""))
        val isStartChecked = sharedPreferences.getBoolean("Start", false)
        switchStart.isChecked = isStartChecked
        if (isStartChecked) {
            startGrpcService()
        }
    }
}