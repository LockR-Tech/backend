"""Liệt kê câu tiếng Việt server tự soạn chưa có bản tiếng Anh trong bảng dịch.

App gửi `X-App-Language: en` ⇒ common-lib `EnglishResponseAdvice` dịch JSON trả về theo
`common-lib/src/main/resources/i18n/server-text-en.json`. Câu mới thêm vào code mà chưa có trong bảng
sẽ hiện tiếng Việt cho khách dùng tiếng Anh. Chạy sau khi thêm/đổi câu báo lỗi, tiêu đề thông báo…:

    python -I scripts/i18n/server_text_missing.py            # in câu thiếu kèm file:dòng
    python -I scripts/i18n/server_text_missing.py --json     # in khung JSON để dán vào bảng rồi dịch

Mẫu câu: biểu thức `"a" + x + "b"` ⇒ "a{}b"; `"..%s".formatted(..)` ⇒ %s thành {}. Bỏ qua log.*,
comment, text block (khung HTML thư — các câu trong đó thêm tay vào bảng), file *Sms* (SMS viết không
dấu, mẫu SMS thêm tay), prompt và tài liệu của assistant-service,
mô tả cài đặt admin (*SettingsCatalog*).
"""
import json
import os
import re
import sys

VI = 'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ'
VI_RE = re.compile('[' + VI + VI.upper() + ']')

TOKEN = re.compile(r'''
    (?P<tb>""".*?""")
  | (?P<str>"(?:[^"\\\n]|\\.)*")
  | (?P<chr>'(?:[^'\\\n]|\\.)+')
  | (?P<lc>//[^\n]*)
  | (?P<bc>/\*.*?\*/)
  | (?P<op>->|==|!=|<=|>=|&&|\|\||\+=|::)
  | (?P<id>[A-Za-z_$][\w$]*)
  | (?P<num>\d[\w.]*)
  | (?P<p>[(){}\[\];,.+?:=<>!&|*/%^~@-])
  | (?P<ws>\s+)
''', re.S | re.X)

BOUNDARY = {',', ';', '=', '?', ':', '->', 'return', '==', '!=', '&&', '||', '+=', 'throw', 'new', 'case', 'yield'}
FMT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfx]')


def unescape(lit):
    out, i, body = [], 0, lit[1:-1]
    while i < len(body):
        c = body[i]
        if c == '\\' and i + 1 < len(body):
            n = body[i + 1]
            if n == 'u':
                out.append(chr(int(body[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({'n': '\n', 't': '\t', '"': '"', "'": "'", '\\': '\\'}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def tokenize(src):
    out = []
    pos = 0
    while pos < len(src):
        m = TOKEN.match(src, pos)
        if not m:
            out.append(('p', src[pos], pos))
            pos += 1
            continue
        if m.lastgroup not in ('ws', 'lc', 'bc'):
            out.append((m.lastgroup, m.group(), pos))
        pos = m.end()
    return out


def templates_in(src):
    toks = tokenize(src)
    n = len(toks)
    depth, d = [], 0
    for _, t, _ in toks:
        if t in ('(', '[', '{'):
            depth.append(d)
            d += 1
        elif t in (')', ']', '}'):
            d -= 1
            depth.append(d)
        else:
            depth.append(d)

    def in_log_call(i):
        # literal nằm trong ngoặc của log.xxx( … ) ?
        lvl = depth[i]
        j = i - 1
        while j >= 0:
            if toks[j][1] == '(' and depth[j] < lvl:
                lvl = depth[j]
                if j >= 3 and toks[j - 2][1] == '.' and toks[j - 3][1] == 'log' and \
                        toks[j - 1][1] in ('info', 'warn', 'debug', 'error', 'trace'):
                    return True
                if lvl == 0:
                    return False
            if toks[j][1] in (';', '{', '}') and depth[j] < depth[i]:
                return False
            j -= 1
        return False

    used, results = set(), []
    for i, (k, t, p) in enumerate(toks):
        if k != 'str' or i in used or not VI_RE.search(unescape(t)):
            continue
        if in_log_call(i):
            used.add(i)
            continue
        lvl = depth[i]
        a = i
        while a - 1 >= 0 and depth[a - 1] >= lvl and not (depth[a - 1] == lvl and toks[a - 1][1] in BOUNDARY):
            a -= 1
        b = i
        while b + 1 < n and depth[b + 1] >= lvl and not (depth[b + 1] == lvl and toks[b + 1][1] in BOUNDARY):
            b += 1
        # tách toán hạng theo '+' cùng cấp
        operands, cur = [], []
        for q in range(a, b + 1):
            if toks[q][1] == '+' and depth[q] == lvl:
                operands.append(cur)
                cur = []
            else:
                cur.append(q)
        operands.append(cur)
        text, fmt = '', False
        for op in operands:
            if not op:
                continue
            first = toks[op[0]]
            if first[0] == 'str' and depth[op[0]] == lvl and (len(op) == 1 or toks[op[1]][1] == '.'):
                text += unescape(first[1])
                used.add(op[0])
                if len(op) > 2 and toks[op[2]][1] == 'formatted':
                    fmt = True
            elif not text.endswith('{}'):
                text += '{}'
        before = ''.join(x[1] for x in toks[max(0, a - 4):a])
        if fmt or before.endswith('String.format(') or before.endswith('.formatted('):
            text = FMT.sub('{}', text).replace('%%', '%')
        if VI_RE.search(text):
            results.append((text, toks[a][2] if toks[a][0] == 'str' else p))
    return results



EXCLUDE = re.compile(
    r'(Sms|scripts/|SettingsCatalog|HardwareConfigValidator|GatewayProvisioning|RevenueReport'
    r'|DroneDeliverySimulator|common-lib/src/main/java/com/huynqb/laundrylocker/common/i18n/'
    r'|assistant-service/(?!src/main/java/com/huynqb/laundrylocker/assistant/(chat/AssistantService'
    r'|controller/AssistantExceptionHandler)))')
# Không phải câu hiển thị: định dạng số/ngày, mã viết hoa ghép nội dung chuyển khoản, trang HTML.
IGNORE = re.compile(r'^(%,dđ|HH:mm .*|[A-ZÀ-Ỹ ]{2,12}|<!DOCTYPE html>.*)$', re.S)


def main():
    root = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
    catalog_path = os.path.join(root, 'common-lib', 'src', 'main', 'resources', 'i18n', 'server-text-en.json')
    catalog = json.load(open(catalog_path, encoding='utf-8'))
    known = {k.strip() for k in catalog} | {k.replace('{}', '%.0f').strip() for k in catalog}
    missing = {}
    for dirpath, dirs, files in os.walk(root):
        parts = dirpath.replace(os.sep, '/').split('/')
        if 'test' in parts or 'target' in parts or 'node_modules' in parts:
            continue
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, root).replace(os.sep, '/')
            if EXCLUDE.search(rel):
                continue
            src = open(path, encoding='utf-8').read()
            for text, pos in templates_in(src):
                if text.strip() in known or IGNORE.match(text.strip()):
                    continue
                missing.setdefault(text, f"{rel}:{src.count(chr(10), 0, pos) + 1}")
    out = sys.stdout.buffer
    if '--json' in sys.argv:
        out.write(json.dumps({k: '' for k in missing}, ensure_ascii=False, indent=2).encode('utf-8') + b'\n')
    else:
        for text, where in missing.items():
            out.write(f'{where}\t{text}\n'.encode('utf-8'))
    out.write(f'{len(missing)} câu chưa có bản tiếng Anh\n'.encode('utf-8'))
    sys.exit(1 if missing else 0)


if __name__ == '__main__':
    main()
