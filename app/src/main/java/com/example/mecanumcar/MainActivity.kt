package com.example.mecanumcar

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 麦轮小车蓝牙遥控 (KT6368A BLE)
 * 每 50ms 发送一帧 8 字节: DD 77 X Y A B C D
 *   X: 左 -100(0x9C) .. 右 +100(0x64)    Y: 下 -100(0x9C) .. 上 +100(0x64)
 *   A/B/C/D: 按下 1 / 松开 0
 */
class MainActivity : Activity() {

    companion object {
        const val TARGET_NAME = "w"          // 小车蓝牙名称
        const val SEND_INTERVAL_MS = 50L
        const val REQ_PERM = 1
    }

    private lateinit var car: BleCar
    private lateinit var control: ControlView
    private lateinit var statusTv: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            car.send(buildFrame())
            handler.postDelayed(this, SEND_INTERVAL_MS)
        }
    }

    private fun buildFrame(): ByteArray {
        val p = control.pressed
        return byteArrayOf(
            0xDD.toByte(), 0x77.toByte(),
            control.joyX.toByte(), control.joyY.toByte(),
            (if (p[0]) 1 else 0).toByte(), (if (p[1]) 1 else 0).toByte(),
            (if (p[2]) 1 else 0).toByte(), (if (p[3]) 1 else 0).toByte()
        )
    }

    private fun zeroFrame(): ByteArray =
        byteArrayOf(0xDD.toByte(), 0x77.toByte(), 0, 0, 0, 0, 0, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        car = BleCar(this, TARGET_NAME,
            onStatus = { statusTv.text = it },
            onNeedPick = { pickDevice(it) })

        control = ControlView(this)
        val root = FrameLayout(this)
        root.addView(control, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(24, 16, 24, 16)

        val btnConnect = Button(this)
        btnConnect.text = "连接"
        btnConnect.setOnClickListener { startConnect() }
        val btnDisc = Button(this)
        btnDisc.text = "断开"
        btnDisc.setOnClickListener { car.disconnect() }
        statusTv = TextView(this)
        statusTv.setTextColor(Color.WHITE)
        statusTv.textSize = 14f
        statusTv.text = "未连接"
        statusTv.setPadding(24, 0, 0, 0)

        bar.addView(btnConnect)
        bar.addView(btnDisc)
        bar.addView(statusTv)
        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START))

        setContentView(root)
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        car.send(zeroFrame())          // 离开界面先发停车帧, 再断开 (小车 0.5s 收不到帧也会自动停)
        car.disconnect()
    }

    // ---------------- 权限 / 连接 ----------------
    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startConnect() {
        val missing = neededPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQ_PERM)
            return
        }
        car.startScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                car.startScan()
            } else {
                Toast.makeText(this, "需要蓝牙权限才能连接小车", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickDevice(list: List<BluetoothDevice>) {
        val items = list.map { "${car.nameOf(it)}   ${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("没找到名为 \"$TARGET_NAME\" 的设备, 请手动选择")
            .setItems(items) { _, i -> car.connect(list[i]) }
            .setNegativeButton("取消", null)
            .show()
    }
}
