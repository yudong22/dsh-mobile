#!/usr/bin/env bash
# 视觉回归：对四个底部 Tab 各截一张图，并存到指定目录。
#
# 用法：
#   scripts/visual_snapshots.sh <输出目录>            # 默认浅色
#   scripts/visual_snapshots.sh <输出目录> dark       # 深色模式
#
# 依赖：一台已连接且屏幕常亮的设备/模拟器（ANDROID_SERIAL 可选）。
# 深色模式通过系统 uimode 切换，截完会切回浅色。
set -euo pipefail

OUT=${1:?用法: scripts/visual_snapshots.sh <输出目录> [dark]}
MODE=${2:-light}
PKG=com.clarklevis.dsh.android
SERIAL=${ANDROID_SERIAL:-}
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

mkdir -p "$OUT"

# 深色模式用 cmd uimode night yes / no（数字参数在新版本上已不接受）
if [[ "$MODE" == "dark" ]]; then
  "${ADB[@]}" shell "cmd uimode night yes" >/dev/null
else
  "${ADB[@]}" shell "cmd uimode night no" >/dev/null
fi
sleep 1

# 预授予运行时权限：否则冷启动弹出的权限对话框会挡住整个页面，
# 截出来的图全是 GrantPermissionsActivity，视觉回归就变成了假验证。
# 这里与 Gradle 测试侧的 GrantPermissionRule 是同一个目的的两处实现。
for perm in android.permission.POST_NOTIFICATIONS android.permission.CAMERA; do
  "${ADB[@]}" shell pm grant "$PKG" "$perm" 2>/dev/null || true
done

"${ADB[@]}" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null

# 等应用真正就绪：`hosts.ready` 为假时整个组合直接 return，画面是空白画布。
# 等待方式：轮询 uiautomator dump 直到看到我们自己的包名节点（权限对话框会盖住它，故同时排除）。
for i in $(seq 1 30); do
  if "${ADB[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 \
     && "${ADB[@]}" shell cat /sdcard/ui.xml 2>/dev/null | grep -q "package=\"$PKG\""; then
    echo "应用已就绪（第 ${i} 次探测）"
    break
  fi
  sleep 1
done
sleep 2

shot() { # shot <文件名>
  "${ADB[@]}" exec-out screencap -p > "$OUT/$1.png"
  echo "  -> $OUT/$1.png"
}

# 用 uiautomator 的 content-desc 定位 Tab 中心点，而不是写死像素坐标——
# 不同屏幕密度/分辨率下写死坐标会点到错误位置，截出来的"项目页"其实是首页
# （视觉回归因此变成假验证）。
tab_center() { # tab_center <content-desc> -> 输出 "x y"
  "${ADB[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "${ADB[@]}" shell cat /sdcard/ui.xml 2>/dev/null \
    | tr '>' '\n' \
    | grep -oE "content-desc=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
    | grep -oE "\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]" | tail -1 \
    | awk -F'[][]' '{ split($2,a,","); split($4,b,","); print int((a[1]+b[1])/2), int((a[2]+b[2])/2) }'
}

tap_desc() { # tap_desc <content-desc>
  local xy
  xy=$(tab_center "$1")
  if [[ -z "$xy" ]]; then echo "  !! 找不到 Tab：$1"; return 1; fi
  "${ADB[@]}" shell input tap $xy >/dev/null 2>&1
}

# 首页（任务列表 Tab）默认就是启动页
shot 01_tasks

tap_desc "项目";      sleep 2; shot 02_projects
tap_desc "定时任务";  sleep 2; shot 03_schedules
tap_desc "设置";      sleep 2; shot 04_settings

# 抽屉（回到首页后点左上汉堡）
tap_desc "任务列表";  sleep 2
"${ADB[@]}" shell input tap 90 200 >/dev/null 2>&1 || true
sleep 2; shot 05_drawer

# 切回浅色，避免污染后续手工检查
if [[ "$MODE" == "dark" ]]; then
  "${ADB[@]}" shell "cmd uimode night no" >/dev/null
fi

echo "完成：$MODE 模式，输出目录 $OUT"
