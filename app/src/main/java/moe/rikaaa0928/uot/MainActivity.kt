package moe.rikaaa0928.uot

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {
    companion object {
        init {
            System.loadLibrary("uog")
        }
        const val MESSAGE_ACTION = "moe.rikaaa0928.uot.permission.SHOW_MESSAGE"
    }

    private lateinit var configRecyclerView: RecyclerView
    private lateinit var addConfigButton: Button
    private lateinit var switchStart: Switch
    private lateinit var configAdapter: ConfigAdapter
    private var configList: MutableList<Config> = mutableListOf()
    private var activeConfigPosition: Int = -1
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Received broadcast: ${intent?.action}")
            intent?.getStringExtra("message")?.let { message ->
                Log.d("MainActivity", "Message content: $message")
                showMessage(message)
            }
        }
    }

    // 在 MainActivity 类中添加自定义队列类
    private class LimitedQueue<T> : ArrayList<T>() {
        override fun add(element: T): Boolean {
            if (size >= 3) {
                removeFirstOrNull()
            }
            return super.add(element)
        }
    }
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
        ActivityCompat.requestPermissions(
            this,
            arrayOf("com.wireguard.android.permission.CONTROL_TUNNELS"),
            1
        )
        switchStart.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startGrpcService()
            } else {
                stopGrpcService()
            }
        }

        val initer = Init()
        initer.callInit(baseContext)

        // 根据 Android 版本使用不同的注册方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                messageReceiver,
                IntentFilter(MESSAGE_ACTION),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                messageReceiver,
                IntentFilter(MESSAGE_ACTION)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        unregisterReceiver(messageReceiver)
    }

    private fun setupRecyclerView() {
        configAdapter = ConfigAdapter(
            configList,
            onItemClick = { position ->
                if (switchStart.isChecked) {
                    showSwitchEnabledMessage()
                } else {
                    setActiveConfig(position)
                }
            },
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
            configList =
                Gson().fromJson(configsJson, object : TypeToken<MutableList<Config>>() {}.type)
            configAdapter.updateConfigList(configList)  // 更新适配器的数据
        }
        activeConfigPosition = sharedPreferences.getInt("activeConfig", -1)
        configAdapter.setActivePosition(activeConfigPosition)  // 设置活跃配置
        switchStart.isChecked = sharedPreferences.getBoolean("isStarted", false)
        showMessage(
            String.format(
                "switch %s-%s",
                switchStart.isChecked,
                sharedPreferences.getBoolean("isStarted", false)
            )
        )
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
        val editTextWGName = dialogView.findViewById<EditText>(R.id.editTextWGName)

        if (config != null) {
            editTextListenPort.setText(config.listenPort)
            editTextPassword.setText(config.password)
            editTextGrpcEndpoint.setText(config.grpcEndpoint)
            if (config.wgName == null) {
                editTextWGName.setText("")
            } else {
                editTextWGName.setText(config.wgName)
            }

        }

        AlertDialog.Builder(this)
            .setTitle(if (config == null) "添加新配置" else "编辑配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newConfig = Config(
                    editTextListenPort.text.toString(),
                    editTextPassword.text.toString(),
                    editTextGrpcEndpoint.text.toString(),
                    editTextWGName.text.toString()
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
            val conf = configList[activeConfigPosition]
            val intent = Intent(this, UogGrpc::class.java)
            intent.putExtra("config", Gson().toJson(conf))
            startForegroundService(intent)
            conf.wgName?.let { startWireGuardTunnel(baseContext, it) }
        }
    }

    private fun stopGrpcService() {
        val conf = configList[activeConfigPosition]
        conf.wgName?.let { stopWireGuardTunnel(baseContext, it) }
        val intent = Intent(this, UogGrpc::class.java)
        stopService(intent)
    }

    private fun startWireGuardTunnel(context: Context, tunnelName: String) {
        val intent = Intent().apply {
            action = "com.wireguard.android.action.SET_TUNNEL_UP"
            putExtra("tunnel", tunnelName)
            // 设置接收应用的包名
            setPackage("com.wireguard.android")
        }
        context.sendBroadcast(intent)
    }

    // 停止隧道
    private fun stopWireGuardTunnel(context: Context, tunnelName: String) {
        val intent = Intent().apply {
            action = "com.wireguard.android.action.SET_TUNNEL_DOWN"
            putExtra("tunnel", tunnelName)
            setPackage("com.wireguard.android")
        }
        context.sendBroadcast(intent)
    }

    private fun showSwitchEnabledMessage() {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("请先关闭开关再切换配置。")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showMessage(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).apply {
            // 设置半透明背景
            view.setBackgroundColor(Color.parseColor("#CC323232"))
            // 添加点击监听，点击任意位置消失
            view.setOnClickListener { dismiss() }
        }.show()
    }
}

data class Config(
    var listenPort: String,
    var password: String,
    var grpcEndpoint: String,
    val wgName: String?
)
