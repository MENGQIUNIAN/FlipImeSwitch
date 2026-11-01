# FlipImeSwitch

让小米折叠屏手机的外屏（小屏）可以使用任意第三方输入法。

## 背景

小米折叠屏在合盖后，系统会强制把默认输入法改写为搜狗输入法——即使你已经在内屏把默认输入法设成了微信输入法、豆包输入法等。表现就是：展开用你选的输入法，一合盖外屏就变回搜狗。

逆向分析 `miui-services.jar` 后定位到根因：`com.android.server.inputmethod.SogouInputMethodSwitcher` 中的 `isSogouMethodLocked()` / `getSogouMethodIdLocked()`。合盖时（`settings get global current_device_state` 变为 CLOSED），系统会查找搜狗输入法的服务，并把 `default_input_method` 改写过去。

这个改写逻辑有一个隐含前提：**搜狗输入法对当前用户可用**。一旦搜狗对用户 0 被卸载，`getSogouMethodIdLocked()` 解析不到服务、返回 null，强制改写那条分支就失效了——合盖后默认输入法保持不变。

FlipImeSwitch 就是把这一步做成一个按钮。

## 功能

| 按钮 | 作用 |
|---|---|
| 权限 | 检查 Shizuku 状态，未授权时申请 |
| 启动 | 检查是否还有其他已启用的输入法，确认后对用户 0 卸载搜狗（`pm uninstall -k --user 0`），包体与数据保留 |
| 恢复 | 执行 `cmd package install-existing --user 0` 把搜狗装回来 |
| 导出 | 把当前状态和执行日志导出为 txt 到系统「下载」目录 |

安全设计：

- 系统里没有至少一个「已启用」的非搜狗输入法时，**拒绝卸载**并给出警告，避免卸完没有键盘可用
- 卸载前先把默认输入法切到其他输入法；切换失败则中止卸载
- 卸载带 `-k`，只解除用户 0 的安装状态，不删除包体与数据，随时可恢复

## 使用要求

- 小米折叠屏手机（实测设备：小米 MIX Flip 2，型号 `2505APX7BC`，产品代号 `bixi`）
- Android 11+（`minSdk 30`）
- **Shizuku**（免 root，走「无线调试」启动）

> 注意：合盖外屏状态下 MIUI 会拦截第三方 App 启动，所以本应用需要**展开手机在内屏打开**。它改的是系统输入法配置，内屏操作、外屏照样生效。

## 构建

需要 Android SDK（platform 34 / build-tools 34.0.0）与 JDK 17。

```bash
# 先创建 local.properties 指向你的 SDK 目录
echo "sdk.dir=/path/to/AndroidSdk" > local.properties

./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

依赖：

- `dev.rikka.shizuku:api:13.1.5`
- `dev.rikka.shizuku:provider:13.1.5`

## 实现说明

- **执行 shell**：Shizuku 13.1.5 的 `Shizuku.newProcess(String[], String[], String)` 是私有静态方法，这里用反射调用（已用字节码确认它直接转发给 `IShizukuService.newProcess`），因此不必额外写 AIDL UserService。
- **安装状态判断**：一律走 `pm list packages --user 0 <pkg>`，不用 `PackageManager.getPackageInfo`。原因是 `install-existing` 装回的包用户状态为 `stopped=true / notLaunched=true`，此时 `getPackageInfo` 仍会抛 `NameNotFoundException`，会导致状态误判、按钮被错误拒绝。
- **图标**：使用 Adaptive Icon（前景 + 背景），保证桌面、最近任务、外屏三处显示一致。

## 已知限制

- 免 root 模式下，手机每次重启后需要用「无线调试」重新激活一次 Shizuku
- 目前只处理小米版搜狗（`com.sohu.inputmethod.sogou.xiaomi`）。其他 ROM 的包名可能不同，需自行修改 `MainActivity.SOGOU`
- 系统大版本 OTA 后，用户级卸载标记可能被重置，需要重新执行一次
- 「没有任何已启用输入法时拒绝卸载」这条分支未做实机验证（构造该场景会让手机暂时没有可用键盘）

## 免责声明

本项目通过逆向分析系统组件、并借助 Shizuku 执行 shell 命令来修改输入法安装状态，**会改动系统应用的状态**。虽然只做用户级卸载、保留数据、并提供恢复入口，仍请自行评估风险。作者不对任何数据丢失、系统异常或保修问题负责。

请勿用于商业分发或违反当地法律法规的用途。

## License

[MIT](LICENSE)
