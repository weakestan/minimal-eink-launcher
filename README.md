# 极简桌面 · Minimal E-Ink Launcher

一个只做一件事的安卓桌面：**把你要用的 App 摆成你要的样子，其余一律不要。**
针对墨水屏（E-Ink）设备做了重点优化——白底黑字、无动画、无留影。

* 纯 Android Framework API，**零第三方依赖**，release 包仅 **55 KB**
* 最低支持 Android 5.0（API 21），compileSdk / targetSdk 34
* 无需 root，无需联网，不申请任何敏感权限

<p>
  <img src="shot_home.png" alt="界面截图" width="420">
  <img src="shot_new_bar.png" alt="界面截图" width="420">
</p>

## 功能

### 桌面

* **只显示你要的 App**：进入设置可逐个显示 / 隐藏，也支持一键全部显示或全部隐藏。
* **自由格位摆放**：长按图标进入编辑模式后拖动，可放到任意格子，**允许中间留空**——比如第一行只放 1 个、第二行放 2 个。空位会被保留，不会自动补位。
* **拖动时不被遮挡**：拖动中的图标会临时置顶绘制，移动到下一行时压住其它图标而不是被压住，并显示黑色实线方框标示落点。
* **位置持久化**：布局按「应用 → 格位」保存，重启后位置不变；隐藏的 App 重新显示时会回到它原来的格子。
* **一键恢复默认排列**。

### 墨水屏（E-Ink）优化

设置页提供「墨水屏模式」开关，未手动选择时按设备型号自动识别。

* 纯白底 + 纯黑字，面板改为白底黑描边
* 去掉全部渐变、阴影、半透明与圆角填充
* 电量 / WiFi / 蓝牙图标改用灰阶配色，充电时只保留黑色闪电
* 取消淡入淡出、页面切换动画、平滑滚动等高频重绘
* 时钟、电量等文本内容不变时不重绘，减少闪屏与残影

### 状态栏

自绘的时钟、电量（充电显示闪电）、WiFi 信号强度与蓝牙状态，不依赖系统状态栏，墨水屏下也能保持清晰。

### 华为中国区机型的桌面接管

华为中国区 ROM 会拦截第三方桌面成为默认桌面（`HwPackageManagerService` 拒绝 `replacePreferredActivity`）。
本项目内置一个可选的**无障碍服务**：只监听「系统桌面是否进入前台」，一旦切换走就立刻切回极简桌面，从而覆盖手势上滑、Home 键、导航栏回桌面三种入口。

> 该服务**只监听窗口变化，不读取屏幕内容**，也不需要联网。不开启时不影响正常使用，只是无法在华为中国区机型上直接设为默认桌面。

## 编译

环境要求：

* JDK 17
* Android SDK（compileSdk 34）
* Gradle 由 Wrapper 提供（8.9），AGP 8.5.2，无需另外安装

```bash
cd LauncherApp
./gradlew assembleDebug      # 生成 debug 包
./gradlew assembleRelease    # 生成 release 包
```

产物位于 `LauncherApp/app/build/outputs/apk/`。

### release 签名

签名信息从 `LauncherApp/keystore.properties` 读取，该文件**不随源码分发**（已在 `.gitignore` 中排除）。没有该文件时，`assembleRelease` 仍可构建，但产物是未签名的。

需要自行创建 `LauncherApp/keystore.properties`：

```properties
storeFile=../your.jks
storePassword=你的密钥库口令
keyAlias=你的别名
keyPassword=你的密钥口令
```

> 注意：请勿把 `.jks` 密钥库和 `keystore.properties` 提交到公开仓库。

## 安装

从 [Releases](../../releases) 下载 APK 后安装，或使用 adb：

```bash
adb install -r 极简桌面-release.apk
```

若之前装过 debug 包，因签名不同需先卸载再安装。

首次启动后如需替换系统桌面：设置 → 默认应用 → 桌面 → 选择「极简桌面」。华为中国区机型请改用上述无障碍接管方式。

## 权限说明

| 权限 | 用途 |
|---|---|
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 显示 WiFi 连接状态与信号强度 |
| `BLUETOOTH`（仅 API 30 及以下） | 显示蓝牙开关状态 |
| 无障碍服务（可选，需手动开启） | 华为中国区机型上接管桌面 |

均为查询类权限，桌面启动时不会申请任何弹窗授权。

## 项目结构

```
LauncherApp/app/src/main/java/com/stan/launcher/
├── LauncherActivity.java     桌面主界面：布局、编辑模式、拖动、状态刷新
├── AppGridLayout.java        格位网格容器，按格位摆放与拖动落点计算
├── AppIconView.java          单个图标（图标 + 名称 + 隐藏角标）
├── AppRepo.java              应用扫描、显示/隐藏、格位与顺序持久化
├── AppInfo.java              应用信息模型
├── SettingsActivity.java     设置页：显示隐藏、墨水屏模式、默认桌面、接管开关
├── AppSettingAdapter.java    设置页应用列表适配器
├── Skin.java                 皮肤/主题属性读取（深色与墨水屏两套）
├── BatteryView.java          自绘电量图标
├── WifiSignalView.java       自绘 WiFi 信号图标
└── HomeKeyService.java       无障碍服务：桌面接管
```
