package moe.rikaaa0928.uot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import moe.rikaaa0928.uot.ConfigAdapter

class MainActivity : AppCompatActivity() {
    companion object {
        init {
            System.loadLibrary("uog")
        }
    }

    private lateinit var configRecyclerView: RecyclerView
    private lateinit var addConfigButton: Button
    private lateinit var switchStart: Switch
    private lateinit var configAdapter: ConfigAdapter
    private var configList: MutableList<Config> = mutableListOf()
    private var activeConfigPosition: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configRecyclerView = findViewById(R.id.configRecyclerView)
        addConfigButton = findViewById(R.id.addConfigButton)
        switchStart = findViewById(R.id.switchStart)

        setupRecyclerView()
        loadConfigurations()

        addConfigButton.setOnClickListener {
            addNewConfiguration()
        }

        switchStart.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startGrpcService()
            } else {
                stopGrpcService()
            }
        }

        val initer = Init()
        initer.callInit(baseContext)
    }

    private fun setupRecyclerView() {
        configAdapter = ConfigAdapter(
            configList,
            onItemClick = { position -> setActiveConfig(position) },
            onEditClick = { position -> editConfiguration(position) },
            onDeleteClick = { position -> deleteConfiguration(position) }
        )
        configRecyclerView.layoutManager = LinearLayoutManager(this)
        configRecyclerView.adapter = configAdapter
    }

    private fun loadConfigurations() {
        val sharedPreferences = getSharedPreferences("AppConfigs", MODE_PRIVATE)
        val configsJson = sharedPreferences.getString("configs", null)
        if (configsJson != null) {
            configList = Gson().fromJson(configsJson, object : TypeToken<MutableList<Config>>() {}.type)
            configAdapter.updateConfigList(configList)  // 更新适配器的数据
        }
        activeConfigPosition = sharedPreferences.getInt("activeConfig", -1)
        configAdapter.setActivePosition(activeConfigPosition)  // 设置活跃配置
        switchStart.isChecked = sharedPreferences.getBoolean("isStarted", false)
        if (switchStart.isChecked) {
            startGrpcService()
        }
    }

    private fun saveConfigurations() {
        val sharedPreferences = getSharedPreferences("AppConfigs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("configs", Gson().toJson(configList))
        editor.putInt("activeConfig", activeConfigPosition)
        editor.putBoolean("isStarted", switchStart.isChecked)
        editor.apply()
    }

    private fun addNewConfiguration() {
        showConfigDialog(null) { newConfig ->
            configList.add(newConfig)
            configAdapter.notifyItemInserted(configList.size - 1)
            saveConfigurations()
        }
    }

    private fun editConfiguration(position: Int) {
        showConfigDialog(configList[position]) { updatedConfig ->
            configList[position] = updatedConfig
            configAdapter.notifyItemChanged(position)
            saveConfigurations()
        }
    }

    private fun showConfigDialog(config: Config?, onSave: (Config) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_config, null)
        val editTextListenPort = dialogView.findViewById<EditText>(R.id.editTextListenPort)
        val editTextPassword = dialogView.findViewById<EditText>(R.id.editTextPassword)
        val editTextGrpcEndpoint = dialogView.findViewById<EditText>(R.id.editTextGrpcEndpoint)

        if (config != null) {
            editTextListenPort.setText(config.listenPort)
            editTextPassword.setText(config.password)
            editTextGrpcEndpoint.setText(config.grpcEndpoint)
        }

        AlertDialog.Builder(this)
            .setTitle(if (config == null) "添加新配置" else "编辑配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newConfig = Config(
                    editTextListenPort.text.toString(),
                    editTextPassword.text.toString(),
                    editTextGrpcEndpoint.text.toString()
                )
                onSave(newConfig)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteConfiguration(position: Int) {
        configList.removeAt(position)
        configAdapter.notifyItemRemoved(position)
        if (position == activeConfigPosition) {
            activeConfigPosition = -1
            switchStart.isChecked = false
            stopGrpcService()
        }
        saveConfigurations()
    }

    private fun setActiveConfig(position: Int) {
        activeConfigPosition = position
        configAdapter.setActivePosition(position)
        configAdapter.notifyDataSetChanged()
        saveConfigurations()
    }

    private fun startGrpcService() {
        if (activeConfigPosition != -1) {
            val intent = Intent(this, UotGrpc::class.java)
            intent.putExtra("config", Gson().toJson(configList[activeConfigPosition]))
            startForegroundService(intent)
        }
    }

    private fun stopGrpcService() {
        val intent = Intent(this, UotGrpc::class.java)
        stopService(intent)
    }
}

data class Config(
    var listenPort: String,
    var password: String,
    var grpcEndpoint: String
)
