# 发版

## 版本号规则

`@kotlin
// app/build.gradle.kts
versionCode = 1          // 必须每版递增，Android 靠它判断能不能覆盖安装
versionName = "1.0.0"    // 给人看的，同时用于"检查更新"的版本比较
`@

- **同版本号覆盖安装会被拒；低版本号装不上去**（`INSTALL_FAILED_VERSION_DOWNGRADE`）
- 「检查更新」拿 GitHub Release 的 `tag_name`（如 `v1.0.1`）与本地 `versionName` 做**数值比较**，所以两者要保持一致
- 忘了抬 `versionCode` 是最常见的事故：用户下载了新包却装不上

## 签名配置

签名密钥库放在**项目文件夹之外**，这样整个项目可以安全地打包/分享。项目根目录的 `keystore.properties` 只记录"去哪找钥匙 + 密码"，已被 `.gitignore` 忽略：

`@properties
storeFile=C:/path/to/your/release.jks
storePassword=***
keyAlias=courseschedule
keyPassword=***
`@

**缺少 `keystore.properties` 时 release 构建会直接报错**，不会静默产出一个装不上的未签名包。

`@
⚠️ 签名密钥库和它的密码必须单独备份。
同一个应用只有用同一把钥匙签名才能覆盖安装升级；换了钥匙，用户会看到
INSTALL_FAILED_UPDATE_INCOMPATIBLE —— 必须先卸载再装新版，而卸载会丢掉他导入的课表和设置。
`@

验证签名（指纹应与下面记录的一致）：

`@bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
`@

本项目正式包的签名证书 SHA-256：

`@
92:16:80:13:82:D7:13:D5:4B:99:5E:3A:B9:35:AD:4F:89:A9:EA:2E:04:95:E4:25:18:D3:62:C1:16:86:36:95
`@

### debug 与 release 用同一把钥匙

`app/build.gradle.kts` 里 `debug` 构建类型也挂上了 release 签名（仅当 `keystore.properties` 存在时）。这样手机上装了正式版之后，**从 Android Studio 直接 Run 就能覆盖安装**，不会因为"debug 用调试密钥、正式包用正式密钥"而被迫卸载重装（那会丢掉用户的课表和设置）。

代价：debug 包也带正式签名，不要把它当作"随便分享"的测试包。别人 clone 下来没有 `keystore.properties` 时会自动退回默认调试签名，照常能构建。

## 发版一条命令

`@powershell
# 1) 先提交并推送 —— 脚本不会替你 commit / push
git add -A
git commit -m "发版 1.0.1: ..."
git push

# 2) 发版
pwsh -File tools/release.ps1 -Version 1.0.1 -Notes "本次更新内容..."
pwsh -File tools/release.ps1 -Version 1.1.0 -NotesFile CHANGELOG.md   # 也可以用文件
`@

脚本依次做四件事：

1. `versionCode` 自增、`versionName` 改成 `-Version` 的值
2. `./gradlew clean test assembleRelease`
3. `gh release create v1.0.1 app-release.apk`（把 APK 作为附件挂上）
4. 打印提醒你提交版本号改动

前提：装了 [gh](https://cli.github.com) 并 `gh auth login`。脚本用 .NET 显式读写 UTF-8，避免 Windows PowerShell 5.1 把 `build.gradle.kts` 里的中文注释读坏或写出 BOM。

## 手动发版（不使用脚本）

`@bash
./gradlew clean test assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
`@

然后到 GitHub 仓库的 **Releases → Draft a new release**：

1. **Choose a tag** 填 `v1.0.1`（新 tag）
2. 填标题与说明
3. 把 `app-release.apk` 拖进附件区（**必须是 release 包，不要传 `app-debug.apk`**）
4. **Publish release**

> 顺序很重要：**先把提交 push 上去，再建 Release**。Release 的 tag 会指向远端某个提交，先建 tag 再 push 容易让两者错位。

## 发版前检查清单

| 检查 | 说明 |
|---|---|
| `versionCode` 已 +1 | 否则新包装不上 |
| 用的是同一把签名钥匙 | 换钥匙 = 老用户必须卸载重装 |
| 发布说明不含过时描述 | 例如现在有 `INTERNET` 权限了，别再写"无需任何权限" |
| 附件是 release 包 | 约 1.5 MB 的 `app-release.apk` |
| 发布说明简洁 | 它会原样显示在 App 的更新弹窗里（只去掉 Markdown 记号） |

## 检查更新是怎么工作的

设置页的「检查更新」是**只检测、不自动安装**：

`@
点「检查更新」
  → GET https://api.github.com/repos/Nixscc-Txy/CourseSchedule/releases/latest
      ├─ 有新版 → 弹窗显示版本与发布说明，[稍后] [去下载]
      ├─ 已最新 → Toast 提示
      └─ 失败  → 弹窗说明原因 + [打开发布页] 兜底
`@

实现要点（`data/AppUpdate.kt`）：

- 请求必须带 `User-Agent`，GitHub API 对没有 UA 的请求直接返回 403
- 超时 8 秒；失败不报错误对话框了事，而是给出「打开发布页」的兜底路径（国内访问 `api.github.com` 时好时坏，实测在部分运营商 5G 下可用）
- 版本比较**按点分段做数值比较**，不是字符串比大小（`1.0.10` 字符串比 `1.0.9` 小，版本上却更大）
- 版本号格式看不懂时按"有更新"处理，把判断权交给用户，而不是默默说"已是最新"

### 为什么 App 不自己下载安装

- 自己下载 APK 并拉起安装器需要 `REQUEST_INSTALL_PACKAGES`，用户还要额外授权；而"打开浏览器下载"只需 `INTERNET`
- 国内应用商店普遍不允许 App 内自行下载安装 APK，将来若要上架，这条路得拆掉
- 因此本项目**只申请 `INTERNET` 一个权限**，且只用于读取一次发布信息，不上传任何数据

## 发布之后怎么验证

1. Releases 页确认附件是 `app-release.apk`
2. 手机上装**旧版** → 设置 → 检查更新 → 应显示「发现新版本 v1.0.1」+ 你的说明
3. 点「去下载」→ 浏览器下载 → 覆盖安装 → 课表和设置都还在

## 发错了怎么回滚

`@bash
gh release delete v1.0.1 --yes --cleanup-tag   # 删 Release 并删掉对应 tag
`@

修好后重新发版，记得 `versionCode` 再抬一档。
