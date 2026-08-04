"""
课表 Excel → JSON 转换脚本

用法:
  python excel_to_json.py 课表.xlsx --day-col 3 --slot-col 1 --name-col 2 --week-col 5 --teacher-col 6 --room-col 7

如果不指定列号，脚本会打印所有单元格内容，方便你确认列对应关系。

需要: pip install openpyxl
"""
import sys, json, re, argparse
from openpyxl import load_workbook


COLORS = ["#A3D8F6", "#FFDAB9", "#C8E6C9", "#FFE0B2", "#E1BEE7",
          "#B3E5FC", "#FFECB3", "#F0F4C3", "#B2DFDB", "#F8BBD0",
          "#D7CCC8", "#CFD8DC", "#FFCCBC", "#DCEDC8"]


def parse_weeks(text):
    """解析周次字符串, 返回周次列表"""
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
            except:
                pass

    return sorted(weeks)


def inspect_excel(path):
    """打印 Excel 内容供确认列对应关系"""
    wb = load_workbook(path)
    ws = wb.active
    print(f"表名: {ws.title}, 行数: {ws.max_row}, 列数: {ws.max_column}\n")
    for row in ws.iter_rows(min_row=1, max_row=min(ws.max_row, 12), values_only=True):
        for i, cell in enumerate(row):
            if cell is not None and str(cell).strip():
                print(f"  列{i+1}: {str(cell)[:100]}")
        print("---")
    wb.close()


def convert(path, day_col, slot_col, name_col, week_col, teacher_col, room_col, total_weeks):
    """转换 Excel 到 JSON"""
    wb = load_workbook(path)
    ws = wb.active

    day_map = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7,
               '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7,
               '周一': 1, '周二': 2, '周三': 3, '周四': 4, '周五': 5, '周六': 6, '周日': 7}

    slot_map = {'1-2': (1, 2), '3-4': (3, 4), '5-6': (5, 6),
                '7-8': (7, 8), '9-10': (9, 10), '11-12': (11, 12),
                '上午一': (1, 2), '上午二': (3, 4), '下午一': (5, 6),
                '下午二': (7, 8), '晚上': (9, 10),
                '一': (1, 2), '二': (3, 4), '三': (5, 6), '四': (7, 8), '五': (9, 10)}

    courses = []

    for row_idx, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
        name = str(row[name_col - 1]).strip() if name_col and len(row) >= name_col and row[name_col - 1] else ""
        if not name or len(name) < 2:
            continue

        day_str = str(row[day_col - 1]).strip() if day_col and len(row) >= day_col and row[day_col - 1] else ""
        day_of_week = 1
        for k, v in day_map.items():
            if k in day_str:
                day_of_week = v
                break

        slot_str = str(row[slot_col - 1]).strip() if slot_col and len(row) >= slot_col and row[slot_col - 1] else ""
        start_slot, end_slot = 1, 2
        for k, v in slot_map.items():
            if k in slot_str:
                start_slot, end_slot = v
                break

        week_str = str(row[week_col - 1]) if week_col and len(row) >= week_col and row[week_col - 1] else ""
        weeks = parse_weeks(week_str)

        teacher = str(row[teacher_col - 1]).strip() if teacher_col and len(row) >= teacher_col and row[teacher_col - 1] else ""
        room = str(row[room_col - 1]).strip() if room_col and len(row) >= room_col and row[room_col - 1] else ""

        if weeks:
            courses.append({
                "name": name,
                "teacher": teacher,
                "classroom": room,
                "dayOfWeek": day_of_week,
                "startSlot": start_slot,
                "endSlot": end_slot,
                "weeks": weeks,
                "color": COLORS[len(courses) % len(COLORS)]
            })

    wb.close()

    result = {
        "totalWeeks": total_weeks,
        "courses": courses
    }

    return result


def main():
    parser = argparse.ArgumentParser(description='课表 Excel → JSON')
    parser.add_argument('excel', help='Excel 文件路径')
    parser.add_argument('--inspect', action='store_true', help='先查看 Excel 内容确认列号')
    parser.add_argument('--day-col', type=int, help='星期列号')
    parser.add_argument('--slot-col', type=int, help='节次列号')
    parser.add_argument('--name-col', type=int, help='课程名列号')
    parser.add_argument('--week-col', type=int, help='周次列号')
    parser.add_argument('--teacher-col', type=int, help='教师列号')
    parser.add_argument('--room-col', type=int, help='教室列号')
    parser.add_argument('--total-weeks', type=int, default=19, help='总周数 (默认19)')
    parser.add_argument('-o', '--output', default='schedule.json', help='输出文件 (默认 schedule.json)')

    args = parser.parse_args()

    if args.inspect or not args.name_col:
        print("查看 Excel 内容:\n")
        inspect_excel(args.excel)
        if not args.name_col:
            print("\n请根据上面的列号, 指定参数重新运行:")
            print("  python excel_to_json.py 课表.xlsx --day-col 列号 --slot-col 列号 --name-col 列号 --week-col 列号 --teacher-col 列号 --room-col 列号")
            return

    result = convert(
        args.excel,
        args.day_col, args.slot_col, args.name_col,
        args.week_col, args.teacher_col, args.room_col,
        args.total_weeks
    )

    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"✅ 已生成: {args.output}")
    print(f"   课程数: {len(result['courses'])}")
    print(f"   总周数: {result['totalWeeks']}")
    print(f"\n   复制到: app/src/main/assets/schedule.json")
    print(f"   然后 Android Studio 重新 Run")


if __name__ == '__main__':
    main()
