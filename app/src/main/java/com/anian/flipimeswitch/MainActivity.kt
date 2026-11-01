package com.anian.flipimeswitch

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val SOGOU = "com.sohu.inputmethod.sogou.xiaomi"
        private const val PERM_REQ = 1001

        private const val ACCENT = 0xFF2F6BFF.toInt()
        private const val INK = 0xFF111827.toInt()
        private const val MUTED = 0xFF8A94A6.toInt()
        private const val BODY = 0xFF4B5563.toInt()
        private const val PANEL = 0xFFF2F3F5.toInt()
        private const val PANEL_LOG = 0xFFFAFAFA.toInt()
        private const val BG = 0xFFFAFBFC.toInt()
        private const val BTN_GRAY = 0xFFEDEFF2.toInt()
        private const val BTN_TEXT = 0xFF333333.toInt()
    }

    private val logBuffer = StringBuilder()
    private val io = Executors.newSingleThreadExecutor()

    private val permListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            runOnUiThread(Runnable {
                append("Shizuku 授权结果：${if (grantResult == PackageManager.PERMISSION_GRANTED) "已授予" else "被拒绝"}")
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        buildUi()
        Shizuku.addRequestPermissionResultListener(permListener)
    }

    /** 状态栏 / 导航栏与背景同色，去掉顶部那条灰色横条 */
    private fun setupEdgeToEdge() {
        window.statusBarColor = BG
        window.navigationBarColor = BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        io.shutdownNow()
        super.onDestroy()
    }

    // ---------------- 主页 ----------------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(BG)
        }

        root.addView(LinearLayout(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        root.addView(TextView(this).apply {
            text = "FlipImeSwitch"
            textSize = 32f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(INK)
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(primaryButton("权限") { showShizukuStatus() }, matchHeight(dp(58), top = 48))
        root.addView(primaryButton("启动") { doUninstall() }, matchHeight(dp(58), top = 14))
        root.addView(primaryButton("恢复") { doRestore() }, matchHeight(dp(58), top = 14))
        root.addView(primaryButton("导出") { exportLog() }, matchHeight(dp(58), top = 14))

        root.addView(LinearLayout(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 17f
        isAllCaps = false
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        background = rippleFill(ACCENT, 20, 0x33FFFFFF)
        setOnClickListener { onClick() }
    }

    // ---------------- 通用二级页 ----------------

    /**
     * 统一的二级页面板：标题 + 可选正文 + 主按钮 + 可选次按钮。
     * 圆角、按钮样式全部与「日志」页保持一致。
     */
    private fun showPanel(
        title: String,
        message: String? = null,
        primaryText: String = "关闭",
        primaryAccent: Boolean = false,
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
        }

        box.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(INK)
        }, matchWrap())

        if (!message.isNullOrBlank()) {
            box.addView(TextView(this).apply {
                text = message
                textSize = 14f
                setTextColor(BODY)
                setPadding(0, dp(12), 0, 0)
                setLineSpacing(dp(4).toFloat(), 1f)
            }, matchWrap())
        }

        var dlg: AlertDialog? = null

        box.addView(panelButton(primaryText, primaryAccent) {
            dlg?.dismiss()
            onPrimary?.invoke()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { topMargin = dp(22) })

        if (secondaryText != null) {
            box.addView(panelButton(secondaryText, false) {
                dlg?.dismiss()
                onSecondary?.invoke()
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { topMargin = dp(10) })
        }

        dlg = AlertDialog.Builder(this).setView(box).create()
        styleDialog(dlg)
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** 二级页里的胶囊按钮：accent=true 为主色，否则浅灰 */
    private fun panelButton(text: String, accent: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (accent) {
            setTextColor(Color.WHITE)
            background = rippleFill(ACCENT, 14, 0x33FFFFFF)
        } else {
            setTextColor(BTN_TEXT)
            background = rippleFill(BTN_GRAY, 14, 0x1F000000)
        }
        setOnClickListener { onClick() }
    }

    /** 所有二级页统一的大圆角窗口 */
    private fun styleDialog(d: AlertDialog) {
        d.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(26).toFloat()
        })
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(MUTED)
    }

    // ---------------- 权限页 ----------------

    private fun showShizukuStatus() {
        val ping = shizukuPing()
        val granted = shizukuGranted()
        val title = when {
            !ping -> "Shizuku服务未运行"
            !granted -> "Shizuku未授权"
            else -> "Shizuku已就绪"
        }
        append("检查 Shizuku 状态：${shizukuText()}")

        if (ping && !granted) {
            showPanel(
                title = title,
                primaryText = "申请授权",
                primaryAccent = true,
                onPrimary = {
                    append("正在申请 Shizuku 权限…")
                    try {
                        Shizuku.requestPermission(PERM_REQ)
                    } catch (e: Throwable) {
                        warn("Shizuku 连接失败", e.toString())
                    }
                },
                secondaryText = "关闭"
            )
        } else {
            showPanel(title = title, primaryText = "关闭")
        }
    }

    // ---------------- 状态 ----------------

    /** Shizuku 状态文案，供「权限」弹窗与日志导出使用 */
    private fun shizukuText(): String = when {
        !shizukuPing() -> "服务未运行"
        !shizukuGranted() -> "已运行，未授权"
        else -> "已就绪（shell 权限可用）"
    }

    private fun shizukuPing(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    private fun shizukuGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** 把输入法 ID 换成易读的应用名，取不到就退回包名 */
    private fun imeLabel(id: String?): String {
        if (id.isNullOrBlank()) return "读取失败"
        val pkg = id.substringBefore("/")
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .inputMethodList
                .firstOrNull { it.id == id || it.packageName == pkg }
                ?.let { return it.loadLabel(packageManager).toString() }
        } catch (e: Throwable) {
            // 落到下面的包名解析
        }
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: Throwable) {
            pkg
        }
    }

    // ---------------- 导出 ----------------

    private fun exportLog() {
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        val readable = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
        val body = buildString {
            append("FlipImeSwitch 日志导出\n")
            append("时间：$readable\n\n")
            append("--- 当前状态 ---\n")
            append("Shizuku：${shizukuText()}\n")
            append("当前输入法：${imeLabel(currentDefault())}\n\n")
            append("--- 执行日志 ---\n")
            append(if (logBuffer.isEmpty()) "（暂无日志）\n" else logBuffer.toString())
        }
        val name = "FlipImeSwitch-log-$stamp.txt"

        // 优先写到公共「下载」目录，方便在文件管理器里找到
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("系统未返回可写位置")
            contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            toast("已导出到 下载/$name")
            append("日志已导出：下载/$name")
            return
        } catch (e: Throwable) {
            // 退回 App 私有外部目录
            try {
                val f = File(getExternalFilesDir(null), name)
                f.writeText(body)
                toast("已导出到 ${f.absolutePath}")
                append("日志已导出：${f.absolutePath}")
                return
            } catch (e2: Throwable) {
                toast("导出失败：${e2.message ?: e2}")
                append("[导出失败] ${e2.message ?: e2}")
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ---------------- 绘制辅助 ----------------

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun rippleFill(color: Int, radiusDp: Int, rippleColor: Int) =
        RippleDrawable(ColorStateList.valueOf(rippleColor), rounded(color, radiusDp), null)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun matchHeight(h: Int, top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, h
    ).apply { topMargin = dp(top) }

    private fun append(s: String) {
        logBuffer.append(s).append('\n')
    }

    // ---------------- Shizuku 与包状态 ----------------

    /**
     * 安装状态一律以 shell 的 pm 结果为准。
     *
     * 实测坑：MIUI 上「用户级卸载后刚用 install-existing 装回」的包，其用户状态是
     * stopped=true / notLaunched=true，此时 PackageManager.getPackageInfo() 仍会抛
     * NameNotFoundException，导致状态显示「已卸载」且卸载按钮被错误拒绝执行。
     * 而 `pm list packages --user 0 <pkg>` 能正确列出，故以它为准。
     * 必须在后台线程调用（exec 走 Shizuku 跨进程）。
     */
    private fun querySogouInstalled(): Boolean {
        if (shizukuReady()) {
            try {
                val r = exec("pm list packages --user 0 $SOGOU")
                return r.contains("package:$SOGOU")
            } catch (e: Throwable) {
                // 落到本地兜底
            }
        }
        return try {
            packageManager.getPackageInfo(SOGOU, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun shizukuReady(): Boolean = shizukuPing() && shizukuGranted()

    private fun enabledOthers(): List<android.view.inputmethod.InputMethodInfo> = try {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList
            .filter { it.packageName != SOGOU }
    } catch (e: Throwable) {
        emptyList()
    }

    private fun currentDefault(): String? = try {
        Settings.Secure.getString(contentResolver, "default_input_method")
    } catch (e: Throwable) {
        null
    }

    // ---------------- 动作 ----------------

    private fun doUninstall() {
        if (!ensureShizuku()) return
        append("正在核对搜狗安装状态…")
        io.execute {
            val installed = querySogouInstalled()
            ui { onSogouCheckedForUninstall(installed) }
        }
    }

    private fun onSogouCheckedForUninstall(installed: Boolean) {
        if (!installed) {
            showPanel(title = "当前已处于启动状态", primaryText = "关闭")
            append("[启动] 当前已处于启动状态")
            return
        }
        val others = enabledOthers()
        if (others.isEmpty()) {
            warn(
                "拒绝卸载：没有其他可用输入法",
                "系统里没有任何「已启用」的非搜狗输入法。\n\n如果现在卸载搜狗，手机将没有任何可用键盘，连输入法设置都可能打不开。\n\n请先安装并启用至少一个其他输入法，再回来点这个按钮。"
            )
            return
        }
        val def = currentDefault()
        val defPkg = def?.substringBefore("/")
        val keep = defPkg != null && defPkg != SOGOU && others.any { it.packageName == defPkg }
        val target = if (keep) null else others.first()
        showPanel(
            title = "是否确认启动？",
            primaryText = "确认",
            primaryAccent = true,
            onPrimary = { runUninstall(target?.id) },
            secondaryText = "关闭"
        )
    }

    private fun runUninstall(targetId: String?) {
        append("开始执行卸载流程…")
        io.execute {
            try {
                var canProceed = true
                if (targetId != null) {
                    val r = exec("ime set $targetId")
                    ui { append("切换默认输入法：$r") }
                    if (!r.startsWith("exit=0")) {
                        ui {
                            warn(
                                "已中止卸载",
                                "切换默认输入法失败，为避免卸载后没有可用键盘，已停止。\n\n$r"
                            )
                        }
                        canProceed = false
                    }
                }
                if (canProceed) {
                    val r = exec("pm uninstall -k --user 0 $SOGOU")
                    ui {
                        append("卸载搜狗：$r")
                    }
                }
            } catch (e: Throwable) {
                ui { warn("执行失败", e.toString()) }
            }
        }
    }

    private fun doRestore() {
        if (!ensureShizuku()) return
        append("正在核对搜狗安装状态…")
        io.execute {
            val installed = querySogouInstalled()
            ui { onSogouCheckedForRestore(installed) }
        }
    }

    private fun onSogouCheckedForRestore(installed: Boolean) {
        if (installed) {
            showPanel(title = "当前已恢复默认状态", primaryText = "关闭")
            append("[恢复] 当前已恢复默认状态")
            return
        }
        showPanel(
            title = "是否确认恢复？",
            primaryText = "确认",
            primaryAccent = true,
            onPrimary = {
                append("开始恢复搜狗…")
                io.execute {
                    try {
                        val r = exec("cmd package install-existing --user 0 $SOGOU")
                        ui {
                            append("恢复搜狗：$r")
                        }
                    } catch (e: Throwable) {
                        ui {
                            warn(
                                "恢复失败",
                                "install-existing 失败时，可改用 adb 手动安装 incoming/SogouIME-10462.apk。\n\n$e"
                            )
                        }
                    }
                }
            },
            secondaryText = "关闭"
        )
    }

    private fun ensureShizuku(): Boolean {
        val ok = shizukuReady()
        if (!ok) {
            warn(
                "Shizuku 未就绪",
                "卸载 / 恢复都需要 shell 权限。\n\n请先点「权限」检查并授权；若提示服务未运行，请到 Shizuku App 里用「无线调试」启动服务。"
            )
            requestShizuku()
        }
        return ok
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                append("正在申请 Shizuku 权限…")
                Shizuku.requestPermission(PERM_REQ)
            }
        } catch (e: Throwable) {
            // 状态弹窗已经给过提示，这里静默
        }
    }

    private fun exec(command: String): String {
        val stringArrayClass = Class.forName("[Ljava.lang.String;")
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", stringArrayClass, stringArrayClass, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        val code = p.waitFor()
        return buildString {
            append("exit=$code")
            if (out.isNotBlank()) append(" | out=${out.trim()}")
            if (err.isNotBlank()) append(" | err=${err.trim()}")
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread(Runnable { block() })

    private fun warn(title: String, msg: String) {
        showPanel(title = title, message = msg, primaryText = "关闭")
        append("[$title] ${msg.replace('\n', ' ').take(120)}")
    }
}
