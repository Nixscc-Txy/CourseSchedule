"""
课表 PDF → JSON 转换脚本
用法: python pdf_to_json.py 课表.pdf [schedule.json]
需要: pip install pymupdf
"""
import sys, json, re, fitz

def extract_courses(text):
    """从 PDF 文本中提取课程信息"""
    courses = []

    # 每个课程匹配: 课程名/周次/地点/教师/...
    # 格式: 课程名/周次/地点/教师/...
    pattern = r'([一-鿿（）A-Za-zⅡ]+)/\s*(\d+-\d+周.*?)(?:/\s*([一-鿿A-Za-z\d\-]+))?/\s*([一-鿿]+)'

    lines = text.split('\n')
    full_text = ' '.join(lines)

    # 按课程分隔: 课程名/周次模式
    course_pattern = re.compile(
        r'([一-鿿A-Za-z（）Ⅱ]+)/'
        r'([\d\-,\(\)单双周]+)/'
        r'\s*([一-鿿\d\-A-Za-z]*)/'
        r'\s*([一-鿿]+)'
    )

    for m in course_pattern.finditer(full_text):
        name = m.group(1).strip()
        week_str = m.group(2).strip()
        location = m.group(3).strip() if m.group(3) else ""
        teacher = m.group(4).strip() if m.group(4) else ""

        if len(name) < 2 or len(name) > 30:
            continue
        if not re.search(r'\d', week_str):
            continue

        # 过滤掉非课程条目
        skip_words = ['学号', '学期', '打印', '注', '内容', '顺序', '正式', '结束', '开始']
        if any(w in name for w in skip_words):
            continue

        weeks = parse_weeks(week_str)
        if weeks:
            courses.append({
                'name': name,
                'week_str': week_str,
                'location': location,
                'teacher': teacher,
                'weeks': weeks
            })

    return courses

def parse_weeks(week_str):
    """解析周次字符串, 返回周次列表"""
    weeks = set()

    # 去掉"周"字
    week_str = week_str.replace('周', '')

    # 处理形如 "1-16" 的范围
    range_pattern = re.compile(r'(\d+)-(\d+)')

    parts = week_str.split(',')
    for part in parts:
        part = part.strip()
        if not part:
            continue

        # 检查单双周标记
        odd_only = '单' in part or '(单)' in part
        even_only = '双' in part or '(双)' in part
        part = part.replace('(单)', '').replace('(双)', '').replace('单', '').replace('双', '').strip()

        m = range_pattern.match(part)
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

    return sorted(weeks) if weeks else None


def main():
    if len(sys.argv) < 2:
        print("用法: python pdf_to_json.py 课表.pdf [输出.json]")
        print("需要: pip install pymupdf")
        sys.exit(1)

    pdf_path = sys.argv[1]
    output_path = sys.argv[2] if len(sys.argv) > 2 else "schedule.json"

    print(f"读取 PDF: {pdf_path}")
    doc = fitz.open(pdf_path)
    text = ""
    for page in doc:
        text += page.get_text()
    doc.close()

    print("解析课表数据...")
    courses = extract_courses(text)

    if not courses:
        print("警告: 未能自动提取课程，请检查 PDF 格式")
        print("将生成空白模板，请手动填写")
        courses = []

    # 自动检测总周数
    total_weeks = 19
    for line in text.split('\n'):
        m = re.search(r'共\s*(\d+)\s*周', line)
        if m:
            total_weeks = int(m.group(1))
            break

    # 自动检测开学日期
    start_date = ""
    for line in text.split('\n'):
        m = re.search(r'(\d{4}-\d{2}-\d{2}).*正式上课', line)
        if m:
            start_date = m.group(1)
            break

    # 构建 JSON
    colors = ["#A3D8F6", "#FFDAB9", "#C8E6C9", "#FFE0B2", "#E1BEE7",
              "#B3E5FC", "#FFECB3", "#F0F4C3", "#B2DFDB", "#F8BBD0",
              "#D7CCC8", "#CFD8DC", "#FFCCBC", "#DCEDC8", "#B3E5FC"]

    output_courses = []
    for i, c in enumerate(courses):
        # 需要手动填写 dayOfWeek, startSlot, endSlot
        output_courses.append({
            "name": c['name'],
            "teacher": c.get('teacher', ''),
            "classroom": c.get('location', ''),
            "dayOfWeek": 1,
            "startSlot": 1,
            "endSlot": 2,
            "weeks": c['weeks'],
            "color": colors[i % len(colors)]
        })

    result = {
        "totalWeeks": total_weeks,
        "_start_date": start_date or "请填写开学日期",
        "_note": "dayOfWeek(1=周一..7=周日), startSlot/endSlot(1-2,3-4,5-6,7-8,9-10,11-12) 请对照原始课表手动调整",
        "courses": output_courses
    }

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"已生成: {output_path}")
    print(f"课程数: {len(courses)}")
    print(f"总周数: {total_weeks}")
    if start_date:
        print(f"开学日期: {start_date}")
    print("\n⚠ dayOfWeek 和 startSlot/endSlot 需要对照原始课表手动调整")
    print("  然后复制到 app/src/main/assets/schedule.json 重新编译")


if __name__ == '__main__':
    main()
