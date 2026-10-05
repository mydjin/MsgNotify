# MsgNotify · 通知语音播报

把手机通知栏里的消息读出来。微信、QQ、短信、来电、各类 App 推送，开车、做饭、健身时不用掏手机也能知道谁找你、说了什么。

纯 Android 原生（Java）实现，无第三方 SDK、无网络请求、无任何数据上传，所有设置和播报记录只存在本机。

- 最低系统：Android 8.0（API 26）
- 实测机型：荣耀 200（Android 16 / MagicOS 10）

***

## 来源

之前使用华为+FreeBuds耳机，有个快应用功能，外出的时候戴着耳机就可以知道消息，不用没事就拿出手机。

换成了荣耀手机，enmmm，辣鸡，半天都不回复也不支持快应用。

用通知语音播报APP，发现不仅播不了还要氪金。

看GitHub上有个项目：[GitHub - lcjfly/AndroidNotificationTTS: 一款Android app，用来语音播报通知信息 · GitHub](https://github.com/lcjfly/AndroidNotificationTTS)

但是安装好了用不了。因此用AI基于这个GitHub项目重写了一个。

## 功能

| 模块     | 能力                                 |
| ------ | ---------------------------------- |
| 通知播报   | 监听系统通知栏，按应用白名单播报标题 + 内容            |
| 微信折叠消息 | 四级回退解析，能读出「3 条消息」折叠背后的真实内容         |
| 来电播报   | 响铃时播报来电号码                          |
| 短信播报   | 收到短信时播报发件人与正文                      |
| 播报格式   | 自定义模板，可选播报应用名 / 标题 / 内容，带实时预览      |
| 音色设置   | 切换系统 TTS 引擎，调节音量、音调、语速             |
| 关键词过滤  | 黑名单关键词命中则跳过，白名单关键词可限定只播报特定内容       |
| 免打扰时段  | 设定时间区间，区间内只记录不播报（支持跨零点）            |
| 耳机模式   | 可设为仅在连接耳机时播报                       |
| 音量键打断  | 播报中按音量减键立即停止当前播报                   |
| 播报记录   | 保留最近 200 条，区分已播报 / 未播报并给出跳过原因，支持重播 |
| 保活     | 前台常驻通知 + 开机自启 + 监听服务断线自愈重绑         |

***

## 设计说明

### 整体链路

```
系统通知栏
    │
    ▼
NotifyListener (NotificationListenerService)
    │  提取 title / content，微信折叠消息四级回退
    │  3 秒内同包同内容去重
    ▼
SpeakGate  ── 过滤链 ──▶ 被拦截则记一条「未播报 + 原因」
    │  总开关 / 应用白名单 / 关键词 / 免打扰时段 / 耳机模式 / 屏幕状态
    ▼
TextBuilder  按模板拼装最终播报文本
    │
    ▼
Speaker (TextToSpeech)  排队播报，回写记录状态
    ▲
    │  VolumeWatcher 监听音量键 → 打断
```

来电与短信走 `PhoneCallReceiver` / `SmsReceiver`，汇入同一条 `SpeakGate` 链路，复用全部过滤规则与播报格式。

### 为什么必须是原生，不能用 UniApp

播报的前提是读取通知栏，这依赖 Android 系统级的 `NotificationListenerService`。这类服务要求在 `AndroidManifest` 里静态声明并绑定 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限，由系统直接拉起进程，跨端框架的 JS 层拿不到这个入口。同理，前台服务保活、`ContentObserver` 监听音量键、开机广播自启，都必须落在原生层。

### 关键技术点

**微信折叠消息的四级回退**

微信在通知栏常把多条消息折叠成「3 条消息」，直接取 `EXTRA_TEXT` 只能读到这句汇总。解析按下列顺序逐级回退，取到为止：

1. `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification()` 取消息列表最后一条——聊天类通知的标准结构，最准
2. `EXTRA_BIG_TEXT` 展开后的长文本
3. `EXTRA_TEXT` 普通文本，并清理 `[2条]` 之类前缀；若正则判定为折叠汇总文案则继续回退
4. `EXTRA_TEXT_LINES` 收件箱样式多行，倒序取第一条非空行

> 用 AndroidX 的 `NotificationCompat.MessagingStyle` 而非 `android.app.Notification.MessagingStyle`：后者的 `extractMessagingStyleFromNotification` 方法在 compileSdk 的 `android.jar` 中不可见，直接调用无法编译。

**前台服务类型**

Android 14+ 要求前台服务声明类型。本应用的常驻通知服务既非媒体也非定位，使用 `specialUse`：

```xml
<service android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="notification_tts" />
</service>
```

**音量键打断**

系统没有提供音量键的全局监听回调。做法是对 `Settings.System.CONTENT_URI` 注册 `ContentObserver`，回调时比对 `STREAM_MUSIC` 音量的前后值，检测到调低且当前正在播报，就立即 `stop()`。

**断线自愈**

国产 ROM 杀后台可能导致监听服务被解绑，表现为「开关是开的但完全不播报」。`NotifyListener.requestRebind()` 可以主动请求系统重新绑定，权限自检页的「检测并修复」就是触发这个动作。

### 代码结构

```
app/src/main/java/com/msgnotify/app/
├── App.java                      Application，初始化 TTS
├── core/
│   ├── Prefs.java                SharedPreferences 封装，全部设置项键名与默认值
│   ├── SpeakGate.java            播报决策入口，过滤链 + 写记录
│   ├── Conditions.java           各项过滤条件判定（免打扰、耳机、关键词）
│   ├── TextBuilder.java          按模板拼装播报文本，含预览
│   ├── Speaker.java              TextToSpeech 封装，队列 / 引擎 / 音量音调语速
│   ├── VolumeWatcher.java        音量键打断
│   ├── RecordStore.java          播报记录持久化，上限 200 条
│   └── SpeakRecord.java          单条记录模型
├── service/
│   ├── NotifyListener.java       通知监听，内容提取与去重
│   └── KeepAliveService.java     前台常驻通知
├── receiver/
│   ├── BootReceiver.java         开机自启
│   ├── PhoneCallReceiver.java    来电播报
│   └── SmsReceiver.java          短信播报
├── ui/                           9 个页面，BaseActivity 统一标题栏
└── util/
    ├── Apps.java                 已安装应用列表、图标、名称
    └── SysNav.java               跳转各系统设置页
```

UI 刻意只用 `AppCompat` + `LinearLayout` / `RelativeLayout` / `RecyclerView`，不引入 Material 库、ConstraintLayout、ViewBinding。依赖只有两个：`appcompat` 和 `recyclerview`，APK 约 2.7 MB。

***

## 使用说明

### 安装

从 [Releases](../../releases) 下载 APK，或自行构建（见下文）。首次安装时系统会提示「来自未知来源」，选择继续安装。

### 首次启动：逐项授权

打开应用 → 首页底部**权限自检** → 从上到下点开每一项：

| 权限        | 作用        | 不给的后果         |
| --------- | --------- | ------------- |
| 通知使用权     | 读取通知栏     | 完全无法工作        |
| 通知发送权限    | 显示常驻通知    | 保活失效，后台易被杀    |
| 忽略电池优化    | 防止系统休眠进程  | 息屏一段时间后不播报    |
| 后台弹出界面    | 国产 ROM 特有 | 影响后台存活        |
| 电话状态 / 短信 | 来电、短信播报   | 这两项播报不可用，其他正常 |

全部变绿后回到首页，打开顶部**主开关**，状态卡片应显示运行中。

### 基本配置

1. **播报应用** — 勾选需要播报的 App。不勾选则不播报任何通知，这是最常见的「没声音」原因
2. **播报格式** — 选择播报内容的组成，页面底部有实时预览
3. **音色设置** — 调音量、音调、语速；如果声音不自然，在这里切换 TTS 引擎
4. **播报设置** — 免打扰时段、耳机模式、来电短信开关、关键词过滤

### 验证是否正常

1. 首页点**发送测试通知**，应立刻听到播报
2. 打开**播报记录**，应看到刚才那条，状态为已播报
3. 让别人发条微信，锁屏状态下测试
4. 播报途中按**音量减**键，应立即静音
5. 把免打扰时段设成覆盖当前时间，再发测试通知，记录里应显示未播报并注明原因

***

## 注意事项

### 保活（最关键，国产 ROM 必配）

应用被系统杀掉后，通知监听服务就断了，表现为开关还是开着但一声不响。以 **MagicOS 10 / 荣耀** 为例：

- **设置 → 应用和服务 → 应用启动管理** → 找到本应用 → 关闭「自动管理」→ 手动勾选**允许自启动**、**允许关联启动**、**允许后台活动**
- **最近任务界面**下拉应用卡片 → 点锁图标加锁，防止一键清理
- **设置 → 电池 → 耗电管理** → 确认本应用为「不限制」

小米需要在安全中心设置自启动 + 省电策略无限制；华为在「应用启动管理」做同样操作；OPPO / vivo 在后台冻结与自启动管理里放行。思路一致：**自启动 + 后台活动 + 不省电 + 任务锁**，四件套都要配。

### 其他须知

- **依赖系统 TTS 引擎**。手机上没有可用的中文 TTS 时无法发声，需要先在系统设置里安装并选定一个引擎（部分机型默认引擎不支持中文）
- **播报有延迟是正常的**。TTS 初始化、引擎排队都需要时间，尤其是息屏后的第一条
- **记录上限 200 条**，超出自动丢弃最早的，不提供导出
- **不播报自己的通知**。常驻通知与测试通知被显式排除，否则会自我循环
- **去重窗口 3 秒**。同一应用在 3 秒内推送相同内容只播一次，某些 App 会重复推送同一条通知
- **通知使用权被系统收回**时，重新打开开关往往无效，用权限自检页的「检测并修复」主动触发重绑
- **升级要用同一签名**。签名不一致时系统会拒绝覆盖安装，必须先卸载，设置和记录随之清空

### 隐私

所有通知内容只在本机解析与存储，没有任何联网代码，`AndroidManifest` 里也没有声明 `INTERNET` 权限。但请注意，这个应用会**读取全部通知内容**，权限很大，建议只安装自己构建或信任来源的 APK。

***

## 自行构建

环境：JDK 17+、Android SDK（platform 35、build-tools 36.0.0）。

```bash
git clone <this-repo>
cd MsgNotify
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。

### 配置自己的签名（可选）

**仓库内不包含任何密钥文件**，不配置签名也能构建，只是 APK 会使用默认 debug 签名。想用固定签名以便覆盖升级，先生成密钥库：

```bash
keytool -genkeypair -v -keystore app/msgnotify.keystore \
  -alias msgnotify -keyalg RSA -keysize 2048 -validity 10000
```

再在项目根目录创建 `keystore.properties`：

```properties
storeFile=app/msgnotify.keystore
storePassword=你的密码
keyAlias=msgnotify
keyPassword=你的密码
```

这两个文件都已在 `.gitignore` 中，不会被提交。

### GitHub Actions 云端构建

推送到 `main` 分支自动触发 `.github/workflows/build-apk.yml`，构建产物上传为 Artifact 并发布 Release。

要让云端也用自有签名，在仓库 **Settings → Secrets and variables → Actions** 添加四个 Secret：

| Secret              | 值                                                  |
| ------------------- | -------------------------------------------------- |
| `KEYSTORE_BASE64`   | 密钥库文件的 base64，`base64 -w 0 app/msgnotify.keystore` |
| `KEYSTORE_PASSWORD` | 密钥库密码                                              |
| `KEY_ALIAS`         | 密钥别名                                               |
| `KEY_PASSWORD`      | 密钥密码                                               |

不配置时流水线会跳过签名步骤，仍能正常出包。

***

## 参考

- [AndroidNotificationTTS2](https://github.com/lcjfly/AndroidNotificationTTS2)
- [Android 通知监听实践](https://www.jianshu.com/p/6f86ef5c2182)
- 华为+FreeBuds快应用、通知语音播报APP

## License

MIT，个人自用项目，不提供任何形式的保证。
