#!/bin/sh
#
# display-debug.sh — collect display/graphics diagnostics on the
# STM32MP157C-DK2 (FRIDA NT35510 DSI panel / LTDC / Etnaviv / Weston).
#
# Copy to the board and run as root:
#   scp display-debug.sh root@<board>:/tmp/
#   ssh root@<board> 'sh /tmp/display-debug.sh'
#
# Or capture to a file for sharing:
#   sh /tmp/display-debug.sh > /tmp/display-debug.log 2>&1
#
# It only reads state (no modprobe/restart), so it is safe to run on a
# live system. Sections that need a missing tool are skipped with a note.

set -u

sec() { printf '\n========== %s ==========\n' "$1"; }
have() { command -v "$1" >/dev/null 2>&1; }
run() {
	if have "$1"; then
		"$@" 2>&1
	else
		printf '[skip] %s not installed\n' "$1"
	fi
}

if [ "$(id -u)" != "0" ]; then
	echo "WARNING: not running as root — dmesg/modetest output may be empty." >&2
fi

sec "SYSTEM"
run uname -a
echo "--- /etc/os-release ---"
cat /etc/os-release 2>/dev/null
echo "--- uptime ---"
uptime 2>/dev/null

sec "DRM DEVICES (/dev/dri)"
# Expected on this board: card0 = etnaviv GPU, card1 = LTDC display,
# renderD128 = etnaviv render node. weston.service binds --drm-device=card1.
ls -l /dev/dri/ 2>&1 || echo "no /dev/dri — DRM driver not loaded?"
echo "--- by-path ---"
ls -l /dev/dri/by-path/ 2>/dev/null

sec "DRM CARDS IN SYSFS"
for c in /sys/class/drm/card*; do
	[ -e "$c" ] || continue
	name=$(cat "$c/device/driver/module/drivers" 2>/dev/null)
	printf '%s -> driver: ' "$c"
	readlink -f "$c/device/driver" 2>/dev/null || echo "?"
done
echo "--- connectors / status / modes ---"
for s in /sys/class/drm/*/status; do
	[ -e "$s" ] || continue
	conn=$(dirname "$s")
	printf '%-40s %s\n' "${conn#/sys/class/drm/}" "$(cat "$s")"
done
echo "--- enabled fbs ---"
for e in /sys/class/drm/*/enabled; do
	[ -e "$e" ] || continue
	printf '%-40s %s\n' "$(dirname "$e" | sed 's#/sys/class/drm/##')" "$(cat "$e")"
done

sec "KERNEL MODULES (graphics)"
lsmod 2>/dev/null | grep -Ei 'drm|stm|ltdc|dsi|mipi|etnaviv|panel|nt35510|cec' || \
	echo "(none matched — likely built-in; check dmesg below)"

sec "DMESG: DRM / LTDC / DSI / PANEL"
if have dmesg; then
	dmesg | grep -Ei 'drm|ltdc|dsi|mipi|panel|nt35510|etnaviv|gpu-subsystem|deferred|atomic|vblank' \
		|| echo "(no matching dmesg lines)"
else
	echo "[skip] dmesg not available"
fi

sec "DMESG: DEFERRED PROBE STILL PENDING"
# LTDC waits on the DSI panel; anything stuck here explains a dark screen.
if [ -d /sys/kernel/debug/devices_deferred ]; then
	cat /sys/kernel/debug/devices_deferred 2>/dev/null || echo "(cannot read; mount debugfs?)"
else
	echo "(debugfs devices_deferred not present)"
	echo "try: mount -t debugfs none /sys/kernel/debug"
fi

sec "DEVICE TREE: LTDC / DSI / PANEL nodes"
for n in $(find /proc/device-tree -maxdepth 3 \
		\( -iname '*ltdc*' -o -iname '*dsi*' -o -iname '*panel*' \) 2>/dev/null); do
	st="$n/status"
	if [ -e "$st" ]; then
		printf '%-60s status=%s\n' "${n#/proc/device-tree/}" "$(tr -d '\0' < "$st")"
	else
		printf '%-60s (no status -> okay)\n' "${n#/proc/device-tree/}"
	fi
done

sec "MODETEST (connectors, modes, planes)"
# modetest comes from the libdrm-tests Debian package.
if have modetest; then
	echo "--- card1 (LTDC display) ---"
	modetest -M stm 2>&1 | head -120
	echo "--- card0 (etnaviv) ---"
	modetest -M etnaviv 2>&1 | head -40
else
	echo "[skip] modetest not installed (apt-get install libdrm-tests)"
fi

sec "MESA / GL / EGL (etnaviv Gallium)"
# Weston on this branch tries hardware GL: render on renderD128, scan out
# on card1. If GL init fails Weston falls back / dies — check this section.
run glxinfo -B
echo "--- eglinfo ---"
run eglinfo
echo "--- mesa DRI drivers present ---"
find / -name 'etnaviv_dri.so' 2>/dev/null
ls /usr/lib/*/dri/ 2>/dev/null

sec "WESTON SERVICE STATUS"
run systemctl --no-pager status weston.service
echo "--- wvkbd ---"
run systemctl --no-pager status wvkbd.service

sec "WESTON JOURNAL (last boot)"
if have journalctl; then
	journalctl -b -u weston.service --no-pager 2>&1 | tail -120
else
	echo "[skip] journalctl not available"
fi

sec "WESTON CONFIG"
echo "--- /etc/xdg/weston/weston.ini ---"
cat /etc/xdg/weston/weston.ini 2>/dev/null || \
	cat /etc/skel/.config/weston.ini 2>/dev/null || echo "(weston.ini not found)"
echo "--- ExecStart from unit ---"
systemctl cat weston.service 2>/dev/null | grep -E 'ExecStart|drm-device|backend|use-pixman'

sec "PROCESSES / RUNTIME DIR"
ps -ef 2>/dev/null | grep -E 'weston|wvkbd|Xwayland' | grep -v grep
echo "--- XDG_RUNTIME_DIR ---"
ls -l /run/user/0/ 2>/dev/null

sec "FRAMEBUFFER / BACKLIGHT"
ls -l /dev/fb* 2>/dev/null || echo "(no /dev/fb*)"
for b in /sys/class/backlight/*; do
	[ -e "$b" ] || continue
	printf '%s: brightness=%s max=%s power=%s\n' \
		"$(basename "$b")" \
		"$(cat "$b/brightness" 2>/dev/null)" \
		"$(cat "$b/max_brightness" 2>/dev/null)" \
		"$(cat "$b/bl_power" 2>/dev/null)"
done

sec "DONE"
echo "Tip: if card1 is missing, the panel/DSI/LTDC deferred-probe chain failed —"
echo "look at the DMESG and devices_deferred sections above."
