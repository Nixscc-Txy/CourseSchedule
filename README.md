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
├── app/src/main/
│   ├── assets/
│   │   └── schedule.json          # ★ 课表数据（唯一数据源）
│   └── java/com/example/courseschedule/
│       ├── model/
│       │   └── Course.kt          # 课程数据模型
│       ├── data/
│       │   ├── CourseRepository.kt # 从 JSON 加载课表
│       │   ├── SettingsManager.kt  # 学期起始日/主题偏好
│       │   └── TimeUtils.kt       # 上课时间定义
│       ├── viewmodel/
│       │   └── ScheduleViewModel.kt
│       └── ui/                     # Compose 界面组件
└── tools/
    ├── excel_to_json.py           # Excel(.xlsx) → schedule.json
    ├── matrix_xls_to_json.py      # 教务系统矩阵式课表(.xls) → schedule.json
    └── pdf_to_json.py             # PDF → schedule.json
```

## 课表数据格式

所有课程数据存储在 **`app/src/main/assets/schedule.json`**，结构如下：

```json
{
  "totalWeeks": 19,
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

更换课表只需替换一个文件：**`app/src/main/assets/schedule.json`**，然后重新构建运行即可。无需修改任何 Kotlin 代码。

以下提供三种方式，根据你的课表格式选择。

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

## 功能说明

| 功能 | 说明 |
|------|------|
| 按周切换 | 左右箭头切换周次，自动筛选当前周课程 |
| 自动定位 | 设置学期起始日期后，自动跳转到当前周 |
| 课程详情 | 点击课程块弹窗显示教师、教室、周次详情 |
| 今日高亮 | 当天列高亮显示 |
| 主题切换 | 支持浅色/深色/跟随系统三种模式 |
| 手机导入课表 | 在手机上直接选择教务系统导出的 `.xls` 文件，自动解析并替换课表（无需电脑） |
| 冲突课程选择 | 导入班级课表时选择实际选修的英语、日语等教学班，主课表和桌面小组件同步使用选择结果 |

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
