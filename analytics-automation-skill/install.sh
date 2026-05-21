#!/usr/bin/env bash
# install.sh — Cài skill firebase-events-impl vào 1 project Android.
# Usage:
#   ./install.sh                          # prompt nhập path (mặc định: thư mục hiện tại)
#   ./install.sh /path/to/android/project # cài vào project chỉ định, không hỏi
#
# Script copy `.claude/` + tạo `docs/analytics/`, validate dependencies.

# Self-promote to bash if invoked via sh/dash (script needs bash arrays + [[ ]])
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# Colors
red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
blue()   { printf '\033[34m%s\033[0m\n' "$*"; }
gray()   { printf '\033[90m%s\033[0m\n' "$*"; }

blue "═══ analytics-automation installer ═══"
echo "Source: $SCRIPT_DIR"
echo

# ---- Resolve target ----
if [[ $# -ge 1 && -n "${1:-}" ]]; then
    # Arg mode — không hỏi
    TARGET_INPUT="$1"
else
    # Interactive mode — prompt
    DEFAULT_TARGET="$(pwd)"
    gray "Nhập đường dẫn Android project (Enter = thư mục hiện tại)"
    gray "  Ví dụ: ~/AndroidStudioProjects/MyApp"
    gray "         /home/me/code/my-android-app"
    gray "         . (= thư mục hiện tại)"
    read -r -e -p "Target: " TARGET_INPUT
    TARGET_INPUT="${TARGET_INPUT:-$DEFAULT_TARGET}"
fi

# Tilde + env var expansion (read không tự expand ~)
TARGET_INPUT="${TARGET_INPUT/#\~/$HOME}"
TARGET_INPUT="$(eval echo "$TARGET_INPUT")"

# Validate exists + resolve to absolute path
if [[ ! -d "$TARGET_INPUT" ]]; then
    red "❌ Target directory không tồn tại: $TARGET_INPUT"
    yellow "   Tạo thư mục trước, hoặc nhập đúng path."
    exit 1
fi
TARGET="$(cd "$TARGET_INPUT" && pwd)"
echo "Target: $TARGET"
echo

if [[ ! -d "$SCRIPT_DIR/.claude/skills/firebase-events-impl" ]]; then
    red "❌ Không tìm thấy .claude/skills/firebase-events-impl trong $SCRIPT_DIR"
    red "   Script này phải chạy từ thư mục đã unzip analytics-automation/"
    exit 1
fi

if [[ ! -f "$TARGET/settings.gradle.kts" && ! -f "$TARGET/settings.gradle" && ! -d "$TARGET/app" ]]; then
    yellow "⚠ Target không giống Android project (không thấy settings.gradle hoặc app/)"
    read -r -p "Vẫn tiếp tục? (y/N) " ans
    [[ "$ans" =~ ^[Yy]$ ]] || exit 1
fi

# ---- Merge .claude/ (NEVER replace — preserve other skills/commands/docs/settings) ----
echo
blue "═══ Merge skill vào .claude/ ═══"
gray "Chỉ copy 8 slash commands + 1 skill 'firebase-events-impl'."
gray "Mọi file/folder khác trong .claude/ (skills khác, docs, settings, CLAUDE.md) ĐƯỢC GIỮ NGUYÊN."
echo

mkdir -p "$TARGET/.claude/commands" "$TARGET/.claude/skills"

# Snapshot what user has BEFORE we touch
EXISTING_PRESERVED=()
if [[ -d "$TARGET/.claude" ]]; then
    # Use temp file instead of process substitution (< <(...)) — avoid syntax errors
    # on shells that parse the file before self-promote takes effect.
    TMPFILE=$(mktemp 2>/dev/null || echo "/tmp/install_preserved_$$.tmp")
    find "$TARGET/.claude" -mindepth 1 \( -type f -o -type l \) 2>/dev/null > "$TMPFILE" || true
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        rel="${f#$TARGET/.claude/}"
        # Skip the parts we're about to write to
        case "$rel" in
            commands/analytics-*.md) continue ;;
            skills/firebase-events-impl|skills/firebase-events-impl/*) continue ;;
        esac
        EXISTING_PRESERVED+=("$rel")
    done < "$TMPFILE"
    rm -f "$TMPFILE"
fi

# 1. Copy our 8 slash commands — only overwrite same-name files
CMDS_ADDED=0
CMDS_UPDATED=0
CMDS_UNCHANGED=0
for cmd in "$SCRIPT_DIR/.claude/commands/"analytics-*.md; do
    [[ -f "$cmd" ]] || continue
    cmd_name=$(basename "$cmd")
    target_cmd="$TARGET/.claude/commands/$cmd_name"
    if [[ -f "$target_cmd" ]]; then
        if cmp -s "$cmd" "$target_cmd"; then
            CMDS_UNCHANGED=$((CMDS_UNCHANGED+1))
            continue
        else
            cp "$cmd" "$target_cmd"
            CMDS_UPDATED=$((CMDS_UPDATED+1))
            yellow "  ~ Updated commands/$cmd_name"
        fi
    else
        cp "$cmd" "$target_cmd"
        CMDS_ADDED=$((CMDS_ADDED+1))
        green "  + Added commands/$cmd_name"
    fi
done

# 2. Replace our skill (it's ours, we own this subdir entirely)
SKILL_DST="$TARGET/.claude/skills/firebase-events-impl"
if [[ -d "$SKILL_DST" ]]; then
    rm -rf "$SKILL_DST"
    cp -R "$SCRIPT_DIR/.claude/skills/firebase-events-impl" "$SKILL_DST"
    yellow "  ~ Updated skills/firebase-events-impl/"
else
    cp -R "$SCRIPT_DIR/.claude/skills/firebase-events-impl" "$SKILL_DST"
    green "  + Added skills/firebase-events-impl/"
fi

# Report
echo
green "✓ Merge xong:"
echo "    Commands:  +$CMDS_ADDED added, ~$CMDS_UPDATED updated, =$CMDS_UNCHANGED unchanged"
echo "    Skill:     firebase-events-impl/ (đầy đủ)"
if [[ ${#EXISTING_PRESERVED[@]} -gt 0 ]]; then
    echo "    Preserved: ${#EXISTING_PRESERVED[@]} file/folder khác trong .claude/ (không touch)"
    if [[ ${#EXISTING_PRESERVED[@]} -le 10 ]]; then
        for p in "${EXISTING_PRESERVED[@]}"; do
            gray "      .claude/$p"
        done
    else
        for p in "${EXISTING_PRESERVED[@]:0:5}"; do
            gray "      .claude/$p"
        done
        gray "      ... và $((${#EXISTING_PRESERVED[@]} - 5)) file/folder khác"
    fi
fi

# ---- Create docs/analytics/ ----
mkdir -p "$TARGET/docs/analytics"
green "✓ Tạo docs/analytics/ (chỗ chứa event-spec.yaml + reports)"

# ---- Add to .gitignore ----
GITIGNORE="$TARGET/.gitignore"
ENTRIES=(".claude-cache/")
if [[ -f "$GITIGNORE" ]]; then
    for entry in "${ENTRIES[@]}"; do
        if ! grep -qxF "$entry" "$GITIGNORE"; then
            echo "$entry" >> "$GITIGNORE"
            green "✓ Thêm $entry vào .gitignore"
        fi
    done
else
    printf '%s\n' "${ENTRIES[@]}" > "$GITIGNORE"
    green "✓ Tạo .gitignore với .claude-cache/"
fi

# ---- Check Python ----
if ! command -v python3 &> /dev/null; then
    if ! command -v python &> /dev/null; then
        red "❌ Không có Python — cài Python 3.11+ rồi chạy lại"
        exit 1
    fi
fi
PY=$(command -v python3 || command -v python)
green "✓ Python: $($PY --version 2>&1)"

# ---- Install Python deps ----
echo
read -r -p "Cài Python deps (pyyaml, jinja2) bằng pip? (Y/n) " ans
if [[ ! "$ans" =~ ^[Nn]$ ]]; then
    if $PY -m pip install -r "$TARGET/.claude/skills/firebase-events-impl/scripts/requirements.txt" \
        --quiet --user 2>/dev/null || \
       $PY -m pip install -r "$TARGET/.claude/skills/firebase-events-impl/scripts/requirements.txt" \
        --break-system-packages --quiet 2>/dev/null; then
        green "✓ Đã cài pyyaml + jinja2"
    else
        yellow "⚠ Cài pip deps lỗi. Cài tay:"
        yellow "  pip install pyyaml jinja2"
    fi
fi

# ---- Verify skill works ----
echo
echo "═══ Verify skill scripts ═══"
cd "$TARGET"
if $PY .claude/skills/firebase-events-impl/scripts/validate_spec.py --help &>/dev/null; then
    green "✓ validate_spec.py works"
fi
if $PY .claude/skills/firebase-events-impl/scripts/scaffold_spec.py --help &>/dev/null; then
    green "✓ scaffold_spec.py works"
fi

# ---- Detect module ----
echo
echo "═══ Detect module firebase-events ═══"
if $PY .claude/skills/firebase-events-impl/scripts/sync_from_module.py --check 2>&1 | tail -5; then
    :
fi

# ---- Summary ----
echo
green "╔══════════════════════════════════════════════════════════════╗"
green "║  ✅ Cài đặt xong!                                            ║"
green "╚══════════════════════════════════════════════════════════════╝"
echo
echo "📁 Cấu trúc:"
echo "   $TARGET/.claude/                       (skill)"
echo "   $TARGET/docs/analytics/                (output dir)"
echo
echo "🚀 Bước kế:"
echo "   1. cd $TARGET"
echo "   2. claude  (mở Claude Code)"
echo "   3. Trong Claude Code:"
echo "      > /analytics-sync-module"
echo "      > /analytics-scaffold"
echo "      > /analytics-setup"
echo "      > /analytics-audit"
echo "      > /analytics-generate --apply-missing"
echo "      > /analytics-verify"
echo
echo "📖 Đọc thêm:"
echo "   $SCRIPT_DIR/docs/usage-guide.md  (hướng dẫn member)"
echo "   $SCRIPT_DIR/docs/faq.md          (câu hỏi thường gặp)"
