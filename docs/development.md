# 开发

## 技术栈

| 层级 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 架构 | ViewModel + StateFlow（单 Activity） |
| 数据 | JSON 文件（assets 内置 / 导入文件优先） |
| 最低 SDK | Android 8.0（API 26），targetSdk 36 |
| 语言 | Kotlin 2.2 |
| 依赖 | 仅 AndroidX 与 Compose，**无任何第三方库** |

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17+（本项目在 JDK 25 上验证过） |
| Android SDK | compileSdk 36 / platform-tools |
| Gradle | 由 wrapper 提供（9.4.1），无需单独安装 |
| Android Studio | 可选，用命令行同样能构建 |

## 常用命令

`@bash
./gradlew assembleDebug          # 调试包 -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # 装到已连接的设备
./gradlew test                   # 跑单元测试（55 个，全部在 JVM 上，不需要设备）
./gradlew clean test assembleRelease   # 发布前的完整校验（需要签名配置，见发版文档）
`@

Windows 上用 `./gradlew.bat`。

> ⚠️ 在 Windows PowerShell 5.1 下直接跑长构建容易被超时打断；一旦客户端被杀，**Gradle daemon 仍会继续执行**，此时再发一次构建就会出现两个构建抢锁、慢到离谱。构建卡住时先 `./gradlew --status` 看有没有残留 daemon。

## 目录结构

`@
CourseSchedule/
├── app/src/main/
│   ├── assets/schedule.json           # 内置课表（出厂 courses 为空数组）
│   ├── java/com/example/courseschedule/
│   │   ├── MainActivity.kt            # 唯一 Activity，页面切换与弹窗统一渲染
│   │   ├── model/                     # Course / ScheduleData
│   │   ├── data/                      # 全部业务逻辑（见下表）
│   │   ├── viewmodel/                 # ScheduleViewModel（StateFlow 单一数据源）
│   │   ├── ui/                        # Compose 界面
│   │   └── widget/                    # 桌面小组件
│   └── res/                           # 图标、主题、小组件布局、备份规则
├── app/src/test/                      # 单元测试 + 化名的 .xls 固件
├── docs/                              # 文档（本目录）
├── tools/                             # 电脑端转换脚本、发版脚本
└── design/                            # 应用图标源文件
`@

## 架构与数据流

`@
                    ┌──────────────────────────────┐
   内置 assets ───▶ │ CourseRepository.load()      │ ◀── files/imported_schedule.json
   （出厂为空）      │  导入的优先，回落到 assets      │
                    └──────────────┬───────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │ ScheduleViewModel            │  StateFlow: courses / currentWeek
                    │  · 周次与开学日期换算          │            today / themeMode
                    │  · 冲突选课结果过滤            │            widgetTheme / error
                    │  · 编辑课程后写盘              │
                    └──────────────┬───────────────┘
                        ┌──────────┴──────────┐
                        ▼                     ▼
                  WeekViewScreen          CourseWidgetReceiver
                  （HorizontalPager 分周）（RemoteViews，读同一份数据）
`@

**几个贯穿全局的约定：**

- **课程身份键** = `课程名 + 教师 + 教室`（`Course.selectionKey()`）。去重、冲突识别、选课结果全基于它；`teachingClass` 只用于显示。
- **作息表只有一份**：`TimeUtils.slotTimes`，周视图与小组件共用，不要在界面里另抄一套。
- **不做第三方依赖**：`.xls` / `.xlsx` 解析、`"检查更新"` 的 HTTP 请求都是手写的（`XlsReader` / `XlsxReader` / `AppUpdate`），项目只有 AndroidX 与 Compose。
- **界面文案与注释用中文**，注释解释"为什么这么做"，而不是复述代码。

## 关键文件

| 文件 | 职责 |
|---|---|
| `data/CourseRepository.kt` | 唯一的课表读取入口：导入的优先，失败回落内置 assets |
| `data/ScheduleImporter.kt` | 按文件头魔数分派解析器（OLE2 / ZIP / JSON），并负责存盘与清除 |
| `data/XlsReader.kt` | 手写 OLE2 复合文档 + BIFF8 读取（纯 JVM，可单测） |
| `data/XlsxReader.kt` | ZIP + DOM 读第一个工作表，处理富文本与粘连课程 |
| `data/MatrixScheduleParser.kt` | 教务"矩阵式"网格 → Course；周次支持 `1-16周 / 单双周 / 逗号列表` |
| `data/ScheduleSelection.kt` | 冲突识别（并查集）、冲突簇合并、选课结果按课表签名存取 |
| `data/TimeUtils.kt` | 作息时间表（周视图与小组件共用） |
| `data/SettingsManager.kt` | 学期起始日 / 主题 / 小组件配色；含纯函数 `isDarkWidget()` |
| `data/AppUpdate.kt` | 版本信息、GitHub 发布检查、发布说明清理 |
| `viewmodel/ScheduleViewModel.kt` | 全部界面状态的唯一来源 |
| `ui/WeekViewScreen.kt` | 周视图：HorizontalPager 分周、滑动与瞬移、编辑入口 |
| `ui/CourseSelectionDialog.kt` | 冲突选课弹窗（导入确认与事后补选共用） |
| `ui/SettingsScreen.kt` | 设置页：导入、清空、开学日期、主题、小组件配色、检查更新 |
| `widget/CourseWidgetReceiver.kt` | 桌面小组件：取课、配色、跨午夜刷新 |

## 单元测试

`@bash
./gradlew test
`@

覆盖范围（55 个用例，全部纯 JVM）：

| 测试 | 覆盖 |
|---|---|
| `MatrixScheduleParserTest` | 周次解析、单双周、矩阵单元格解析 |
| `XlsReaderTest` | 真实教务 `.xls` 固件的端到端解析 |
| `XlsxReaderTest` | 代码合成的 `.xlsx`：共享字符串、富文本断行、粘连拆分 |
| `ScheduleSelectionTest` / `ConflictReproTest` | 冲突识别、冲突簇合并、真实课表回归 |
| `TimeUtilsTest` | 节次 ↔ 时间映射，含连堂课跨时段 |
| `ClassroomTextTest` | 教室字段拆成"教学楼 / 教室号" |
| `WidgetThemeTest` | 小组件配色的跟随规则 |
| `VersionCompareTest` | 版本号按数值比较（`1.0.10 > 1.0.9`） |
| `AppUpdateTest` | 发布说明的 Markdown 清理 |

测试固件 `app/src/test/resources/schedule_fixture.xls` 里的**课程与教师姓名均已化名**，请保持这一点 —— 不要提交真实教务数据或截图。

> 界面（Compose）与小组件（RemoteViews）没有自动化测试：前者需要引入 Compose UI 测试依赖，后者需要 Robolectric。当前靠"改完装到真机上验证"，改动这两处时请手动回归。
