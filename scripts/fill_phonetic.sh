#!/usr/bin/env bash
# ============================================================
# fill_phonetic.sh — 给 vocabulary 表所有缺读音的单词批量填充音标（IPA，美音）
#
# 用法（在服务器宿主机上执行，MySQL 在宿主机）：
#   bash scripts/fill_phonetic.sh
#
# 特点：
#   - 只填充 phonetic 为空的行，已有读音的不会覆盖；可重复执行。
#   - 音标来源：开源词典 open-dict-data/ipa-dict（en_US，约 12.6 万词条）。
#   - 词典已下载则复用，不重复下载。
#   - 词典里没有的词会保留为空，并在最后统计出来。
# ============================================================
set -euo pipefail

DB="english_tutor_system"
MYSQL_USER="root"
MYSQL_PASS="1234"
MYSQL="mysql -u${MYSQL_USER} -p${MYSQL_PASS} --default-character-set=utf8mb4"

WORK="/tmp/phonetic_fill"
mkdir -p "$WORK"
DICT="$WORK/en_US.txt"
TSV="$WORK/words.tsv"
SQL="$WORK/updates.sql"

command -v mysql   >/dev/null 2>&1 || { echo "缺少 mysql 客户端"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "缺少 python3，请先执行: apt-get install -y python3"; exit 1; }

echo "== 1/5 确保 vocabulary.phonetic 列存在 =="
$MYSQL -D "$DB" <<'SQL'
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vocabulary' AND COLUMN_NAME = 'phonetic');
SET @s := IF(@c = 0,
  'ALTER TABLE vocabulary ADD COLUMN phonetic VARCHAR(100) NULL COMMENT ''读音（音标）'' AFTER word',
  'SELECT ''skip: phonetic 已存在'' AS info');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SQL

echo "== 2/5 下载 IPA 词典（en_US，约 3MB） =="
if [ ! -s "$DICT" ]; then
  for url in \
    "https://cdn.jsdelivr.net/gh/open-dict-data/ipa-dict@master/data/en_US.txt" \
    "https://raw.githubusercontent.com/open-dict-data/ipa-dict/master/data/en_US.txt" ; do
    echo "  尝试: $url"
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL --connect-timeout 20 "$url" -o "$DICT" && [ -s "$DICT" ] && break
    else
      wget -q --timeout=20 -O "$DICT" "$url" && [ -s "$DICT" ] && break
    fi
  done
fi
[ -s "$DICT" ] || { echo "词典下载失败，请检查服务器网络（可手动下载后放到 $DICT）"; exit 1; }
echo "  词典大小: $(wc -c < "$DICT") 字节"

echo "== 3/5 导出缺读音的单词 =="
$MYSQL -N -B -D "$DB" -e "SELECT id, word FROM vocabulary WHERE phonetic IS NULL OR phonetic = ''" > "$TSV"
echo "  待处理: $(wc -l < "$TSV") 个"

echo "== 4/5 生成 UPDATE 语句 =="
python3 - "$DICT" "$TSV" "$SQL" <<'PY'
import io, sys
dict_path, tsv_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]

ipa_map = {}
with io.open(dict_path, encoding='utf-8') as f:
    for line in f:
        line = line.rstrip('\n')
        if '\t' not in line:
            continue
        w, ipa = line.split('\t', 1)
        w = w.strip().lower()
        if not w or w in ipa_map:
            continue
        ipa_map[w] = ipa.split(',')[0].strip()   # 一词多音时取第一个

def esc(s):
    return s.replace('\\', '\\\\').replace("'", "''")

rows, missing = [], []
total = matched = 0
with io.open(tsv_path, encoding='utf-8-sig', errors='replace') as f:
    for line in f:
        line = line.rstrip('\n')
        if '\t' not in line:
            continue
        i, w = line.split('\t', 1)
        i = i.strip().lstrip('\ufeff')
        w = w.strip()
        if not i:
            continue
        total += 1
        ipa = ipa_map.get(w.lower())
        if ipa:
            matched += 1
            rows.append("UPDATE vocabulary SET phonetic='%s' WHERE id=%s AND (phonetic IS NULL OR phonetic='');" % (esc(ipa), i))
        else:
            missing.append(w)

with io.open(out_path, 'w', encoding='utf-8') as f:
    f.write('SET NAMES utf8mb4;\n')
    f.write('\n'.join(rows) + '\n')

print('  匹配 %d / %d，未找到发音 %d 个' % (matched, total, len(missing)))
if missing:
    print('  未找到（示例）: ' + ', '.join(missing[:50]))
PY

echo "== 5/5 写回数据库 =="
$MYSQL -D "$DB" < "$SQL"

REMAIN=$($MYSQL -N -B -D "$DB" -e "SELECT COUNT(*) FROM vocabulary WHERE phonetic IS NULL OR phonetic = ''")
echo "完成。仍为空（词典里没有该词）: ${REMAIN} 个"