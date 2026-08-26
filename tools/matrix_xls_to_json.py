"""
学校系统导出的矩阵式课表 (.xls) → schedule.json 转换脚本

适用格式（教务系统导出）：
  - 第 1 行: 表头(学期/班级/专业)
  - 第 2 行: 列头 (节次 | 星期一 ~ 星期日)
  - 之后每行是一个时间段(上午一/二, 下午三/四, 晚上五)
  - 每个单元格内可含多门课程, 用换行分隔
  - 每条课程格式: 课程名/(节次)周次/校区 教室/教师/教学班/学分
    例如: Java程序设计/(1-2节)1-16周/中心校区 教学楼412/李老师/Java程序设计-0005/4.0

用法:
  python matrix_xls_to_json.py 课表.xls -o ../app/src/main/assets/schedule.json

需要: pip install xlrd  (读取 .xls; 若学校导出为 .xlsx 请先另存为 .xls, 或改用 excel_to_json.py)
"""
import sys, json, re, argparse
import xlrd

COLORS = ["#AED9F0", "#F9D9B8", "#BDE8D0", "#FAD9A8", "#E4CEF8",
          "#9FD4F5", "#F7E7B0", "#D6EEC2", "#C6ECE3", "#F5C8D6",
          "#F4E2D1", "#DAE7F0", "#F2CFC2", "#CCE8B8"]

# 每行时间段 -> 节次区间 (行 2..6: 上午一/二, 下午三/四, 晚上五)
ROW_SLOTS = {
    2: (1, 2), 3: (3, 4), 4: (5, 6), 5: (7, 8), 6: (9, 10),
}


def parse_weeks(text):
    """解析周次字符串, 支持 1-16周 / 1-2周,4周 / 10-12周(双),13-16周 / 1-15周(单)"""
    if text is None:
        return []
    text = str(text).strip()
    weeks = set()

    text = text.replace('周', '').replace(' ', '')

    parts = re.split(r'[,，]', text)
    for part in parts:
        if not part.strip():
            continue
        odd_only = '单' in part
        even_only = '双' in part
        part = part.replace('(单)', '').replace('(双)', '').replace('单', '').replace('双', '').strip()

        m = re.match(r'(\d+)[-—](\d+)', part)
        if m:
            start, end = int(m.group(1)), int(m.group(2))
            for w in range(start, end + 1):
                if odd_only and w % 2 == 0:
                    continue
                if even_only and w % 2 == 1:
                    continue
                weeks.add(w)
        else:
            try:
                weeks.add(int(part))
            except ValueError:
                pass

    return sorted(weeks)


def parse_entry(text, day_of_week, fallback_slots):
    """
    解析单条课程字符串:
      课程名/(1-2节)1-16周/中心校区 教学楼412/李老师/Java程序设计-0005/4.0
    返回 dict 或 None
    """
    text = text.strip()
    if not text:
        return None

    parts = text.split('/')
    if len(parts) < 4:
        return None

    name = parts[0].strip()

    # 节次: (1-2节) 或 (9节)
    slot_m = re.search(r'\((\d+)(?:-(\d+))?节?\)', parts[1])
    if slot_m:
        start_slot = int(slot_m.group(1))
        end_slot = int(slot_m.group(2)) if slot_m.group(2) else start_slot
        week_text = parts[1][slot_m.end():]
    else:
        start_slot, end_slot = fallback_slots
        week_text = parts[1]

    weeks = parse_weeks(week_text)
    if not weeks:
        return None

    # 地点: "中心校区 教学楼213" -> 教室 "教学楼213" (去掉校区前缀)
    location = parts[2].strip()
    classroom = re.sub(r'^\S*校区\s*', '', location) if location else ''

    teacher = parts[3].strip() if len(parts) > 3 else ''
    teaching_class = parts[4].strip() if len(parts) > 4 else ''

    return {
        "name": name,
        "teacher": teacher,
        "teachingClass": teaching_class,
        "classroom": classroom,
        "dayOfWeek": day_of_week,
        "startSlot": start_slot,
        "endSlot": end_slot,
        "weeks": weeks,
    }


def convert(path):
    wb = xlrd.open_workbook(path)
    sh = wb.sheet_by_index(0)

    entries = []
    for r in range(sh.nrows):
        # 只处理数据行: 第 1 列(索引0)为时间段名, 或该行属于 ROW_SLOTS
        if r not in ROW_SLOTS:
            continue
        fallback = ROW_SLOTS[r]
        for c in range(2, sh.ncols):  # 列 2..8 = 星期一..星期日
            day_of_week = c - 1
            cell = sh.cell_value(r, c)
            if cell is None:
                continue
            for line in str(cell).split('\n'):
                entry = parse_entry(line, day_of_week, fallback)
                if entry:
                    entries.append(entry)
    wb.release_resources()

    # 同一课程同一颜色 (按课程名首次出现顺序分配)
    color_of = {}
    for e in entries:
        if e["name"] not in color_of:
            color_of[e["name"]] = COLORS[len(color_of) % len(COLORS)]

    courses = []
    for e in entries:
        courses.append({
            "name": e["name"],
            "teacher": e["teacher"],
            "teachingClass": e["teachingClass"],
            "classroom": e["classroom"],
            "dayOfWeek": e["dayOfWeek"],
            "startSlot": e["startSlot"],
            "endSlot": e["endSlot"],
            "weeks": e["weeks"],
            "color": color_of[e["name"]],
        })

    max_week = max((w for e in entries for w in e["weeks"]), default=16)
    return {
        "totalWeeks": max_week,
        "courses": courses,
    }


def main():
    parser = argparse.ArgumentParser(description='矩阵式课表 .xls → schedule.json')
    parser.add_argument('xls', help='课表 .xls 文件路径')
    parser.add_argument('-o', '--output', default='schedule.json', help='输出文件 (默认 schedule.json)')
    args = parser.parse_args()

    result = convert(args.xls)

    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"✅ 已生成: {args.output}")
    print(f"   课程条目数: {len(result['courses'])}")
    print(f"   总周数: {result['totalWeeks']}")
    print(f"\n   复制到: app/src/main/assets/schedule.json")
    print(f"   然后 Android Studio 重新 Run")


if __name__ == '__main__':
    main()
