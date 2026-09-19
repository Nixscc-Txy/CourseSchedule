# CourseSchedule 课表查看器

一款 Android 课表查看应用，使用 Jetpack Compose 构建。支持按周查看课程、设置学期起始日期自动定位当前周、浅色/深色主题切换。

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | ViewModel + StateFlow |
| 数据 | JSON 文件（assets） |
| 最低 SDK | Android 8.0 (API 26) |
| 语言 | Kotlin |

## 项目结构

```
CourseSchedule/
├── app/src/
│   ├── main/
│   │   ├── assets/schedule.json      # 内置课表。出厂 courses 为空数组，用户导入的课表优先于此
│   │   ├── java/com/example/courseschedule/
│   │   │   ├── model/                # Course.kt / ScheduleData.kt
│   │   │   ├── data/
│   │   │   │   ├── CourseRepository.kt     # 加载课表（导入的文件优先，回落到内置 assets）
│   │   │   │   ├── ScheduleImporter.kt     # 识别文件格式 / 保存 / 清除导入的课表
│   │   │   │   ├── ScheduleSelection.kt    # 冲突识别、冲突簇合并、选课结果存取
│   │   │   │   ├── SettingsManager.kt      # 学期起始日 / 主题偏好
│   │   │   │   ├── TimeUtils.kt            # 作息时间表（周视图与小组件共用同一份定义）
│   │   │   │   ├── JsonScheduleParser.kt   # schedule.json 解析
│   │   │   │   ├── MatrixScheduleParser.kt # 教务矩阵式课表解析
│   │   │   │   ├── XlsReader.kt            # OLE2/BIFF8 极简读取（无第三方依赖）
│   │   │   │   └── XlsxReader.kt           # xlsx（ZIP + XML）读取
│   │   │   ├── viewmodel/ScheduleViewModel.kt
│   │   │   ├── ui/                   # Compose 界面：周视图、设置页、选择弹窗、主题
│   │   │   └── widget/               # 桌面小组件
│   │   └── res/                      # 图标、主题、布局、备份规则
│   └── test/                         # 单元测试（含一份教务导出格式的 .xls 固件，课程与教师均已化名）
├── tools/                            # 电脑端转换脚本（App 运行不需要）
└── design/                           # 应用图标源文件
```

> 注意区分两个名字：`applicationId` 是 `com.CourseSchedule.courseschedule`（手机上的应用标识、也是覆盖安装的依据），
> 而 Kotlin 代码里的 `namespace` 仍是 `com.example.courseschedule`（内部包路径，用户看不到，改它要动所有源文件，收益为零）。

## 课表数据格式

课程数据有两个来源，**导入的优先**：

1. **用户导入的课表** — 存在 App 私有目录 `files/imported_schedule.json`，在手机上通过「导入课表」写入
2. **内置课表** — `app/src/main/assets/schedule.json`，**出厂是空的**（`courses` 为空数组），
   只有开发时想把课表预置进 APK 才需要动它

两者的 JSON 结构相同：

```json
{
  "totalWeeks": 16,
  "courses": [
    {
      "name": "课程名称",
      "teacher": "教师名",
      "classroom": "教室名称",
      "dayOfWeek": 1,
      "startSlot": 1,
      "endSlot": 2,
      "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      "color": "#A3D8F6"
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalWeeks` | int | 学期总周数 |
| `name` | string | 课程名称 |
| `teacher` | string | 授课教师 |
| `classroom` | string | 上课教室 |
| `dayOfWeek` | int | 星期几，1=周一 … 7=周日 |
| `startSlot` | int | 起始节次，1-12 |
| `endSlot` | int | 结束节次，1-12 |
| `weeks` | int[] | 上课周次列表，如 `[1,2,3,5,7,9]` |
| `color` | string | 课表单元格背景色，十六进制 `#RRGGBB` |

### 节次对应关系

节次与时间段的对应关系定义在 `TimeUtils.kt`：

| 节次 | 时间 |
|------|------|
| 1-2 节 | 08:30 - 10:05 |
| 3-4 节 | 10:25 - 12:00 |
| 5-6 节 | 14:00 - 15:35 |
| 7-8 节 | 15:55 - 17:30 |
| 9-10 节 | 19:00 - 20:35 |
| 11-12 节 | 20:50 - 22:25 |

---

## 如何更换课表

**日常使用推荐在手机上直接导入** —— 见下方[手机导入课表](#手机导入课表推荐)，不需要电脑。

下面三种方式是在**电脑上生成 `schedule.json`**，适合开发者、或课表格式比较特殊需要先转换的情况。
生成后有两种用法：

- 在手机上把这个 `schedule.json` 当作课表文件导入（App 同样支持）
- 或者替换 `app/src/main/assets/schedule.json` 后重新构建，把它预置进 APK

以下按你的课表格式选一种。

### 方式一：从学校 Excel 导出（通用方法）

适用于学校系统导出的 `.xls` / `.xlsx` 课表文件。

**步骤：**

1. **检查 Excel 结构** — 运行检测命令查看列对应关系：
   ```bash
   cd tools
   python excel_to_json.py 你的课表.xlsx --inspect
   ```
   它将打印前几行内容及列号，帮助你确认课程名、星期、节次、周次、教师、教室各在哪一列。

2. **运行转换** — 根据上一步确认的列号执行：
   ```bash
   python excel_to_json.py 你的课表.xlsx \
     --day-col 3 \        # 星期所在列
     --slot-col 1 \       # 节次所在列
     --name-col 2 \       # 课程名所在列
     --week-col 5 \       # 周次所在列
     --teacher-col 6 \    # 教师所在列
     --room-col 7 \       # 教室所在列
     --total-weeks 19 \   # 学期总周数
     -o ../app/src/main/assets/schedule.json
   ```

3. **重新构建** — 在 Android Studio 中 Run 或 `./gradlew assembleDebug`。

> **矩阵式课表（教务系统导出）**：如果课表是"行=时间段、列=星期、单元格内含课程信息"的矩阵格式
> （如 `Java程序设计/(1-2节)1-16周/中心校区 教学楼412/李老师/...`），使用
> `tools/matrix_xls_to_json.py`：
> ```bash
> cd tools
> pip install xlrd
> python matrix_xls_to_json.py 你的课表.xls -o ../app/src/main/assets/schedule.json
> ```
> 注意：该脚本读取 `.xls` 格式；若学校导出的是 `.xlsx` 但打开报错，通常是内容仍为 `.xls`
> （扩展名不符），直接传入即可，或用 Excel 另存为 `.xls` 再转换。
### 方式二：手动编辑 JSON

适用于课表数量不多、或只需微调的情况。

直接编辑 `app/src/main/assets/schedule.json`，按[数据格式](#字段说明)添加/修改课程条目。

**示例** — 添加一门"高等数学"：
```json
{
  "name": "高等数学",
  "teacher": "张三",
  "classroom": "知行楼201",
  "dayOfWeek": 1,
  "startSlot": 1,
  "endSlot": 2,
  "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
  "color": "#FFDAB9"
}
```

**注意：**
- `dayOfWeek` 从 1 开始（1=周一，7=周日）
- `weeks` 必须是数组，不能写 `"1-16"` 这种字符串
- 同一课程在不同周上课地点不同，拆成多条记录（仅 `weeks` 和 `classroom` 不同）
- 冲突识别以「课程名 + 教师 + 教室」（`teachingClass` 仅用于显示）区分不同教学班：导入班级课表后，App 会在确认弹窗中要求选择实际选修的课程；同一门课因周次拆成多条记录时只作为一个选项
- 颜色建议使用柔和色（如 `#A3D8F6`、`#FFDAB9`），每组同一课程使用相同颜色

### 方式三：从 PDF 导出

如果有 PDF 格式的课表，可使用 `tools/pdf_to_json.py`：

```bash
cd tools
pip install pdfplumber
python pdf_to_json.py 课表.pdf
```

> PDF 解析受排版影响较大，建议优先使用 Excel 方式。

---

## 构建与运行

```bash
# 调试构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

或在 Android Studio 中打开项目直接 Run。

### 发布构建（签名）

签名密钥库放在**项目文件夹之外**，这样整个项目可以安全地打包/分享。
项目根目录的 `keystore.properties` 只记录"去哪找钥匙 + 密码"，已被 `.gitignore` 忽略：

```properties
storeFile=C:/path/to/your/release.jks
storePassword=***
keyAlias=courseschedule
keyPassword=***
```

```bash
./gradlew clean test assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`（release 已开启 R8 代码压缩和资源压缩）。

> 缺少 `keystore.properties` 时 release 构建会**直接报错**，不会静默产出一个装不上的未签名包。

验证签名：

```bash
$ANDROID_HOME/build-tools/<版本>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

输出的 `SHA-256 digest` 应等于 `92:16:80:13:82:D7:13:D5:4B:99:5E:3A:B9:35:AD:4F:89:A9:EA:2E:04:95:E4:25:18:D3:62:C1:16:86:36:95`。

> ⚠️ **签名密钥库和它的密码必须单独备份。** 同一个应用只有用同一把钥匙签名才能覆盖安装升级；
> 换了钥匙，用户会看到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，**必须先卸载**再装新版，
> 而卸载会丢掉他导入的课表和设置。每次发新版记得把 `versionCode` 加一，否则新包装不上去。

## 首次使用

App **出厂是空课表**（`app/src/main/assets/schedule.json` 里 `courses` 为空数组），第一次打开会显示
「还没有课表」引导页，点「去导入课表」导入教务系统导出的文件即可。

## 功能说明

| 功能 | 说明 |
|------|------|
| 按周切换 | 左右箭头切换周次，自动筛选当前周课程（首尾周自动禁用对应箭头） |
| 回到本周 | 翻到别的周后，周次栏下方出现「⤴ 回到本周（第 N 周）」，一键跳回；停在本周时显示「本周」标记 |
| 自动定位 | 设置学期起始日期后，自动跳转到当前周；未设置时课表上方给出提示和「去设置」入口 |
| 上课时间 | 周视图左侧同时显示节次和实际时间（`1-2` / `08:30`），课程详情里也带 `08:30-10:05` |
| 课程详情 | 点击课程块弹窗显示教师、教学班、教室、时间、周次详情 |
| 今日高亮 | 当天列高亮显示 |
| 主题切换 | 支持浅色/深色/跟随系统三种模式；状态栏图标跟随 App 内主题，深色模式冷启动不白闪 |
| 手机导入课表 | 在手机上直接选择教务系统导出的 `.xls` / `.xlsx` 文件，自动解析并替换课表（无需电脑） |
| 冲突课程选择 | 班级课表里同一时段开着多门平行的选修课时，弹窗让用户挑出自己实际要上的那门（也可以选「这组我都不上」）；可选项相同的多个时段自动合并成一个问题，只回答一次 |
| 冲突提示 | 课表里还有没解决的时间冲突时，课表上方显示「有 N 处时间冲突还没选」，点「去选择」直接补选，不需要重新导入文件 |
| 同格多门课 | 未解决冲突时，同一格里的多门课**全部显示**、各自可点，而不是只显示第一门 |
| 清空课表 | 设置页「🗑 清空课表」，删掉已导入的课表和选课结果，课表变为空白（有二次确认） |
| 桌面小组件 | 显示今日剩余课程和「明天有早八」提醒；跨午夜由闹钟自动刷新，不依赖系统最长 30 分钟的更新周期 |

## 手机导入课表（推荐）

从教务系统下载新的 `.xls` 课表后，**无需电脑**即可在手机上更新课表：

1. 打开 App，点击顶部「⚙」进入设置
2. 在设置页点击「📥 导入课表」
3. 在文件选择器中找到教务系统下载的课表文件（通常在"下载"目录）
4. 确认弹窗显示解析出的课程数与周数，点击「导入」
5. 课表立即生效，桌面小组件同步更新

说明：
- 支持教务系统的矩阵式 `.xls` / `.xlsx` 课表（行=时间段、列=星期、单元格内为课程信息，微信/QQ传的 xlsx 亦可），
  也支持 `schedule.json` 格式
- 导入的课表保存在 App 私有存储中，优先于内置课表；导错了重新导入正确文件即可
- 内置解析器 `app/src/main/java/com/example/courseschedule/data/XlsReader.kt`
  为 OLE2/BIFF8 极简实现，无第三方依赖，用真实课表文件进行了单元测试验证
- 小组件 `CourseWidgetReceiver` 同样读取"导入优先"的课表，自动生效

## 学期切换

每次新学期只需：

1. 按以上方式更新 `schedule.json`，或在手机上直接导入新课表
2. 在 App 设置中修改学期起始日期（点击课表顶部齿轮图标）
3. App 会自动根据当前日期和起始日期计算当前周
