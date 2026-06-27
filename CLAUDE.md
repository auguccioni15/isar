# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is Isar

Isar (Integration System for Automated Root filesystem generation) is a BitBake-based build system that produces Debian-based root filesystems. It wraps Debian tooling (`mmdebstrap`, `sbuild`, `schroot`, `apt`) with BitBake's task/dependency system to build reproducible embedded Linux images.

## Build Setup

### Method 1: kas-container (recommended, no host dependencies)

```sh
# Interactive menu to select machine/distro/image
./kas/kas-container menu

# Build after configuration
./kas/kas-container build

# Drop into build shell
./kas/kas-container shell
```

### Method 2: Native BitBake

Install host dependencies first (see `doc/user_manual.md` for the full list — requires `mmdebstrap`, `sbuild`, `schroot`, `reprepro`, `qemu-user-static`, etc.).

```sh
# Initialize build directory
. isar-init-build-env ../build

# Build a single target (multiconfig syntax: mc:<machine>-<distro>:<image>)
bitbake mc:qemuamd64-bookworm:isar-image-base

# Build multiple targets
bitbake mc:qemuamd64-bookworm:isar-image-base mc:qemuarm-bookworm:isar-image-base
```

### Test a QEMU image after build

```sh
scripts/start_vm -a amd64 -d bookworm
# default root password: root
```

## Architecture

### Layers

- **`meta/`** — Core Isar framework: BitBake classes (`classes-recipe/`), core recipes (bootstrap, schroot, apt repos, images), and distro configs.
- **`meta-isar/`** — BSP/demo layer: machine configs (`conf/machine/`), multiconfig entries (`conf/multiconfig/`), and BSP recipes (U-Boot, TF-A, OP-TEE, kernels).
- **`meta-test/`** — Test-only layer with recipes used by the Avocado CI suite (`testsuite/citest.py`).
- **`kas/`** — Kconfig-based configuration fragments consumed by the `kas` tool: `machine/`, `distro/`, `image/`, `opt/`, `package/`.

### Key concepts

**Multiconfig builds**: Each target is identified as `mc:<machine>-<distro>`. Machine and distro names come from `meta-isar/conf/multiconfig/<machine>-<distro>.conf`. This lets a single `bitbake` invocation build for many machine/distro pairs simultaneously.

Adding a new machine requires two steps: (1) create `meta-isar/conf/multiconfig/<machine>-<distro>.conf`, and (2) register it in `BBMULTICONFIG`. For full CI builds, append it to `meta-isar/conf/mc.conf`. For a single-machine kas build, set it via `local_conf_header` in the kas YAML:
```yaml
local_conf_header:
  my_multiconfig: |
    BBMULTICONFIG = "mymachine-bookworm"
```

**Build pipeline**: For each target, Isar:
1. Bootstraps a minimal Debian base system via `mmdebstrap` (`meta/recipes-core/isar-mmdebstrap/`)
2. Creates an sbuild chroot rootfs for native or cross compilation (`meta/recipes-devtools/sbuild-chroot/`)
3. Builds custom `.deb` packages inside the chroot
4. Populates the target rootfs from an apt repository of those packages
5. Generates bootable images via `wic` or other image types

**Package recipes**: Custom packages are built by `sbuild` inside the schroot and deposited into a local apt repo before being installed into the rootfs. Choose the class based on the source:

| Class | Use when |
|---|---|
| `dpkg` | Upstream source tarball + `debian/` directory in `SRC_URI` |
| `dpkg-raw` | Pure file installation with no upstream source (config files, systemd units, symlinks) |
| `dpkg-gbp` | Debian Git-packaging workflow (`git-buildpackage`) |
| `dpkg-prebuilt` | Pre-built `.deb` to repack or install directly |
| `dpkg-source` | Low-level base; rarely inherited directly |

**IMAGE_INSTALL vs IMAGE_PREINSTALL**: `IMAGE_INSTALL` lists custom packages built by Isar recipes (installed from the local apt repo). `IMAGE_PREINSTALL` lists packages installed directly from the upstream Debian mirror without building. Use `IMAGE_PREINSTALL` for standard Debian packages (e.g. `weston`, `bluez`, `iproute2`).

**Image types**: Controlled by `IMAGE_FSTYPES`; `wic` images use `.wks.in` files under `meta-isar/scripts/lib/wic/canned-wks/` and `meta/scripts/lib/wic/`.

**Kernel config fragments**: Add `.cfg` files (one `CONFIG_FOO=y` per line) to `SRC_URI` in the kernel recipe. The `linux-kernel.bbclass` merges them via `merge_config.sh` on top of the board defconfig.

**Machine config overrides**: BitBake override syntax (`:machinename`) is used throughout. `MACHINEOVERRIDES =. "foo:"` prepends a new override so that `:foo` conditionals apply to all machines that `require` a base `.conf`.

**Cross-compilation**: Enabled globally with `ISAR_CROSS_COMPILE = "1"`. Opt individual recipes out with `ISAR_CROSS_COMPILE = "0"`.

### Important variables

In a kas build there is **no hand-edited `conf/local.conf`** — only `meta-isar/conf/local.conf.sample` exists, and the build directory's `local.conf` is generated. Set these variables in the multiconfig `.conf` file, a machine/distro `.conf`, or a kas fragment's `local_conf_header` instead.

| Variable | Purpose |
|---|---|
| `MACHINE` | Target hardware (e.g. `qemuamd64`) |
| `DISTRO` | Debian distribution (e.g. `debian-bookworm`) |
| `DISTRO_ARCH` | Target architecture (e.g. `amd64`, `armhf`, `arm64`) |
| `IMAGE_INSTALL` | Custom Isar-built packages to install |
| `IMAGE_PREINSTALL` | Standard Debian packages to install from the mirror |
| `KERNEL_NAME` | Selects the kernel recipe (`linux-<name>`) |
| `IMAGE_FSTYPES` | Output image formats (`ext4`, `wic`, `oci-archive`, …) |
| `ISAR_CROSS_COMPILE` | `"1"` to enable cross-compilation |
| `COMPATIBLE_MACHINE` | Regex in a recipe restricting which machines can build it |

## BitBake Debugging

```sh
# Force a recipe to rebuild from scratch
bitbake -f -c clean mc:qemuamd64-bookworm:my-package
bitbake mc:qemuamd64-bookworm:my-package

# Run a single task
bitbake -c do_install mc:qemuamd64-bookworm:my-package

# Inspect the final value of a variable for a recipe
bitbake -e mc:qemuamd64-bookworm:my-package | grep '^VARIABLE='

# Show dependency graph (outputs to task-depends.dot)
bitbake -g mc:qemuamd64-bookworm:isar-image-base

# Build output artifacts land in:
# kas/build/tmp/work/<distro>-<arch>/<recipe>/<version>/
# WIC images: kas/build/tmp/deploy/images/<machine>/
```

## Running Tests

Tests use the [Avocado](https://avocado-framework.readthedocs.io/) framework. Set up the venv first:

```sh
virtualenv --python python3 /tmp/avocado_venv
source /tmp/avocado_venv/bin/activate
pip install avocado-framework==100.1
```

From inside the build shell (`kas-container shell` or after sourcing `isar-init-build-env`):

```sh
cd /work/isar/testsuite   # or isar/testsuite

# Quick developer test
avocado run citest.py -t dev --max-parallel-tasks=1

# Single target
avocado run citest.py -t single --max-parallel-tasks=1 -p machine=qemuamd64 -p distro=bullseye

# Fast build suite
avocado run citest.py -t fast --max-parallel-tasks=1

# Full CI suite
avocado run citest.py -t full --max-parallel-tasks=1
```

Test code style: PEP8/flake8; format with `black -S -l 79 <file>`. Use single quotes for data strings, double quotes for human-readable strings.

## STM32MP157C-DK2 target

The `stm32mp157c-dk2` machine (`meta-isar/conf/machine/stm32mp157c-dk2.conf`) is the primary development target in this repo. The physical board is the STM32MP157F-DK2 (variant F adds CryptoCell-312) but TF-A 2.4 has no dedicated F-variant DTB, so the C-variant config is used throughout.

**Build command:**
```sh
kas-container --isar build kas/kas-stm32mp1-c-dk2.yaml
```

Use `kas/kas-stm32mp1-c-dk2.yaml` — it sets `BBMULTICONFIG = "stm32mp157c-dk2-bookworm"` via `local_conf_header`. The older `kas/kas-stm32mp1-dk2.yml` is missing that line and will not select the multiconfig; prefer the `-c-dk2.yaml` file.

The build produces a `.wic` image (`IMAGE_FSTYPES = "wic"`) plus a `.wic.bmap` under `kas/build/tmp/deploy/images/stm32mp157c-dk2/`. Flash it to the SD card with `bmaptool copy <image>.wic /dev/<sd>` (bmaptool auto-discovers the adjacent `.wic.bmap`; falls back to `dd` if bmaptool is unavailable). The rootfs auto-expands to fill the card on first boot (see `expand-rootfs` below).

Key points:

- Extends `stm32mp15x.conf`; uses `stm32mp157c-dk2.dtb` for both kernel and TF-A.
- Boot chain: TF-A (`AARCH32_SP=optee`) → OP-TEE → U-Boot → kernel; WKS file `stm32mp15x-dk2.wks.in` creates a FAT32 bootfs + ext4 rootfs layout.
- OP-TEE (`meta-isar/recipes-bsp/optee-os/optee-os-stm32mp15x_3.21.0.inc`) embeds `stm32mp157c-dk2.dts` (not `ev1.dts` — the upstream default is for the evaluation board, not the DK2).
- U-Boot carries three local patches (`meta-isar/recipes-bsp/u-boot/files/`): `0001` restores `config.mk` for non-FIP stm32image generation; `0002` drops the hard-coded `CONFIG_DEFAULT_DEVICE_TREE="stm32mp157c-ev1"` (so the build system can inject the correct DTS) and enables early debug UART on UART4 (0x40010000, 64 MHz HSI clock); `0003` disables `pwr_regulators` in U-Boot's pre-reloc DT overlay — TF-A maps PWR registers (0x50001000) to the secure world, so probing that driver during pre-reloc crashes U-Boot silently before any console output.
- WiFi (CYW43438 via SDMMC2/SDIO): `CONFIG_BRCMFMAC=y` (built-in, not module) is required — as a module the driver loads too late and the chip's PLL fails to lock (HT Avail timeout). See `meta-isar/recipes-kernel/linux/files/stm32mp15x.cfg`.
- The kernel recipe (`meta-isar/recipes-kernel/linux/linux-stm32mp_6.1.bb`) patches `dwc3-stm32.c` to add a missing `bitfield.h` include and, in `do_prepare_build:append`, injects two `.dtsi` overlays by appending `#include` lines to `stm32mp157c-dk2.dts`: `-wifi.dtsi` (SDIO WiFi node) and `-no-hdmi.dtsi` (disables the unresponsive SII902X HDMI bridge that otherwise pins LTDC in deferred probe). The display panel is left as the stock node (see below).
- **Display panel (OTM8009A)**: The board's DSI panel is driven by the **stock `orisetech,otm8009a`** node already present in `stm32mp157c-dk2.dts` — no override is injected. This matches the known-good OpenSTLinux/Yocto build (`~/poky`, kernel 6.6.116, DTB `stm32mp157f-dk2`), whose deployed DTB drives the very same panel as `orisetech,otm8009a` and lights it up. Earlier this branch assumed the panel was a FRIDA FRD397B25009 (Novatek NT35510) and injected `stm32mp157c-dk2-frida-panel.dtsi` plus a v6.12 backport of `panel-novatek-nt35510.c`; that left the panel dark and has been reverted, and both files have been deleted. If the panel ever proves to genuinely be an NT35510, re-create them, add both to `SRC_URI`, and restore the `#include` injection; otherwise stay on OTM8009A.
- **OTM8009A driver needs a 2-line backport on v6.1 or the panel stays dark**: With the *stock* v6.1-stm32mp `panel-orisetech-otm8009a.c` the DK2 panel stays **black as night** even though the entire pipeline is provably up — LTDC `card0` CRTC `active=1` with the correct `480x800@29.7` mode, `fifo_underrun_error=0`, panel driver bound to `5a000000.dsi.0`, `v3v3`/`reg18` rails on, backlight `power=0`. Writing to `/dev/fb0` with weston stopped is also black, and toggling `bl_power` does nothing — so it is **not** weston, GPU/card numbering, regulators, the `no-hdmi.dtsi` endpoint surgery (LTDC `endpoint@1`→DSI is intact), or panel identity (the OpenSTLinux 6.6 build lights the *same* board). The root cause is two fixes present in the v6.6-stm32mp driver but missing at our v6.1 SRCREV: (1) `MIPI_DSI_MODE_NO_EOT_PACKET` in `dsi->mode_flags` — without it the DSI emits EoT packets and the OTM8009A never locks the video stream; (2) a real reset **pulse** (assert 20 ms, de-assert 100 ms) on resume — the v6.1 driver only de-asserts reset, so the controller is never reset before its DCS init. Both are applied by `sed` in `do_prepare_build:append` of `linux-stm32mp_6.1.bb` (guarded on `MIPI_DSI_MODE_NO_EOT_PACKET`). Do not drop this block; the stock driver does not light the panel on 6.1. Diagnostic reference: compare against `~/poky/.../linux-stm32mp/6.6.116-.../kernel-source/drivers/gpu/drm/panel/panel-orisetech-otm8009a.c`.
- **DRM stack must be built-in (`=y`), not modules**: The whole DRM stack is built `=y`. This is **mandatory on the v6.1-stm32mp vendor tree**, not a preference: that tree's `stm-drm` driver calls `device_is_bound()` (driver core, `drivers/base/dd.c`) and `stm32_rifsc_check_access_by_id()` (STM RIF/firewall), neither of which is `EXPORT_SYMBOL`'d. Built as a module (`=m`), modpost fails to link with `ERROR: modpost: "<sym>" [drivers/gpu/drm/stm/stm-drm.ko] undefined!` on both symbols. Built-in, the references resolve against vmlinux and the driver probes fine via deferred probe. `stm32mp15x.cfg` sets `CONFIG_DRM=y`, `CONFIG_DRM_KMS_HELPER=y`, `CONFIG_DRM_STM=y`, `CONFIG_DRM_STM_DSI=y`, `CONFIG_DRM_DW_MIPI_DSI=y`, `CONFIG_DRM_MIPI_DSI=y`, `CONFIG_DRM_PANEL_ORISETECH_OTM8009A=y`. Note `CONFIG_DRM=y` is required explicitly because `multi_v7_defconfig` ships `CONFIG_DRM=m`, and Kconfig cannot keep a `=y` child driver on a `=m` core — without it the children silently demote back to `=m` and reproduce the link failure. (An earlier experiment built the stack `=m` to mirror a "known-good" Yocto config — but that reference is kernel **6.6.116**, a *different* vendor tree where these symbols are handled; it does not carry over to 6.1. Do not flip this block back to `=m`.) `weston.service` still resolves/polls the LTDC card by its by-path symlink, which works identically for built-in or modular DRM.
- WiFi firmware and Bluetooth come from the Debian `firmware-brcm80211` package (`IMAGE_PREINSTALL`, non-free-firmware). Additionally, `firmware-brcm-dk2` (`meta-isar/recipes-bsp/firmware-brcm-dk2/`) is a `dpkg-raw` package that creates the board-specific NVRAM symlink the driver looks for first: `brcmfmac43430-sdio.st,stm32mp157c-dk2.txt → brcmfmac43430-sdio.MUR1DX.txt`.
- `initramfs-tee-ftpm-hook` is removed from `IMAGE_INSTALL` because `CFG_EARLY_TA` is not enabled in OP-TEE, so `/dev/tpmrm0` is absent at initramfs time and the hook would stall the rootfs mount.
- Weston and the on-screen keyboard (`wvkbd`) are configured via the `weston-dk2-config` dpkg-raw package (`meta-isar/recipes-graphics/weston-dk2-config/`), which installs systemd services for both and `weston.ini`. It also provides the multiarch symlink for `weston-desktop-shell` (needed on armhf).
- **Wayland runtime dir must be `/run/weston`, NOT `/run/user/0`**: `/run/user/0` is the per-user dir managed by `systemd-logind`, which mounts a fresh tmpfs over it the moment *anyone* opens a session (SSH/console login). weston starts at boot and binds its socket in `/run/user/0` before any login; a later login then shadows weston's socket with logind's tmpfs, so the socket vanishes for every other process (`ss` still shows the bind path, but `ls`/`connect` get `ENOENT`) and all Wayland clients crash-loop with *"failed to connect to Wayland display: No such file or directory"*. Fix: `weston.service` sets `Environment=XDG_RUNTIME_DIR=/run/weston` (plain dir, logind never touches it) and creates it in `ExecStartPre`; every Wayland client unit (`wvkbd.service`, `dk2-launcher.service`) sets the same `XDG_RUNTIME_DIR=/run/weston`. If a black screen returns with clients reporting "No such file or directory", check that nothing reverted these units back to `/run/user/0`.
- **Wayland socket name must be pinned (`--socket=wayland-1`)**: weston by default auto-picks the first free `wayland-N` socket in `XDG_RUNTIME_DIR`. After a restart a stale `wayland-0` lock can survive in `/run/weston`, so the next weston instance lands on `wayland-1` — a *non-deterministic* name, same class of bug as the DRM card numbering. Any client started with a hardcoded `WAYLAND_DISPLAY` then connects to the wrong socket and crash-loops (seen with `wvkbd.service`: `wvkbd-mobintl` exiting `status=1/FAILURE` in a tight `Restart=on-failure` loop while the display itself is fully up). Fix: `weston.service` passes `--socket=wayland-1` to fix the name, and every Wayland client unit sets `Environment=WAYLAND_DISPLAY=wayland-1` plus an `ExecStartPre` that waits for the socket (`until [ -S /run/weston/wayland-1 ]`) before launching, since weston creates the socket a moment after the service is "started".
- **Home screen & app launcher (`dk2-launcher`)**: `weston-desktop-shell` *cannot* draw icons on the desktop background (it only has the bottom panel + a background), so the OpenSTLinux-style grid of large app icons is provided by a separate dpkg-raw package `meta-isar/recipes-graphics/dk2-launcher/`. It is a small Python3 + GTK3 fullscreen app (`/usr/bin/dk2-launcher`) that renders one icon tile per `.desktop` file found in `/etc/dk2-launcher/apps/` and launches it by parsing the `Exec` line and calling `subprocess.Popen(..., start_new_session=True)` (NOT `Gio.DesktopAppInfo`, which proved unreliable under Weston) — `start_new_session` detaches the child from the launcher's controlling tty so it survives independently. Autostarts under Weston via `dk2-launcher.service` (same `wayland-1` socket handling as above). Other packages add their own tiles by dropping a `.desktop` into that directory — no need to edit the launcher. Depends on `python3-gi`, `gir1.2-gtk-3.0`, `adwaita-icon-theme` (tile icons are resolved by icon *name* from the Adwaita theme). Ships only a `Terminal` tile itself.
- **Warehouse stock tracker (`dk2-inventory`)**: dpkg-raw package `meta-isar/recipes-graphics/dk2-inventory/` — a Python3 + GTK3 fullscreen app (`/usr/bin/dk2-inventory`) backed by a SQLite DB at `/var/lib/dk2-inventory/inventory.db` (persists across reboots; `sqlite3` is in the Python stdlib). A USB barcode scanner (IR/laser) acts as a *keyboard wedge* — it types the code and presses Enter — so the app keeps an always-focused `Gtk.Entry` whose `activate` handler processes each scan. A CARICO/SCARICO toggle selects whether a scan does `quantita +1` or `-1` (`UPDATE prodotti SET quantita = quantita ± 1 WHERE codice = ?`); an unknown code opens a "Nuovo prodotto" dialog (name + initial quantity). Depends on `python3-gi`, `gir1.2-gtk-3.0`, `libsqlite3-0`. Installs `magazzino.desktop` into `/etc/dk2-launcher/apps/`, so the **Magazzino** tile appears in `dk2-launcher` only when this package is installed. A companion CLI, `import-articoli` (same recipe dir), bulk-loads the `prodotti` table from a CSV (`codice,nome,quantita`; `--aggiorna` sums onto existing quantities instead of overwriting) against the same `inventory.db`.
- **GTK fullscreen clients must fit the 480 px panel width**: The DSI panel is 480 px wide (portrait). weston rejects any fullscreen surface wider than the output with a Wayland protocol error (e.g. `buffer 801x800 ...`), and GTK then aborts with `Gtk couldn't be initialized`. A GTK toplevel's size is driven by its children's *minimum* width, so any widget whose natural/minimum width can grow past 480 px (a `TreeView` column sized to its content, a `Label` with a long unwrapped string, a dialog showing an unbounded product name) will silently crash the app at startup or when that data appears. Defenses used in `dk2-inventory`, apply them to any new tile: fixed `TreeViewColumn` widths summing < 480 with `Pango.EllipsizeMode.END` on the flexible column; `ScrolledWindow` with `set_min_content_width(0)` + `set_propagate_natural_width(False)` so list width never propagates up; ellipsize/`max-width-chars` on any label that shows external data. Since the touchscreen has no keyboard, also give every fullscreen app an on-screen way out (a `✕` button calling `Gtk.main_quit()`) rather than relying on Esc.
- **DRM card ordering & rendering**: The Etnaviv GPU (GC400, present on all MP157 variants) and the LTDC display controller each claim a `/dev/dri/cardN`, and **the numbering is NOT stable** — it depends on probe / module-load order (observed in the field: LTDC came up as `card0`, etnaviv as `card1`, the opposite of an earlier assumption; with `=m` the order is even less predictable). So `weston.service` must not hardcode a number: it resolves the LTDC card from its stable by-path symlink `platform-5a001000.display-controller-card` (`--drm-device="$(basename "$(readlink -f …)")"`) and polls for that symlink in `ExecStartPre` rather than using `Requires=dev-dri-cardN.device` (the systemd device unit can time out before LTDC finishes its deferred probe). Rendering uses **`--use-pixman`** (CPU rendering straight to the display-only LTDC framebuffer) — the safe, proven path. An earlier experiment tried hardware GL (render on etnaviv `renderD128`, scan out on the LTDC KMS device) by dropping `--use-pixman`; that is parked behind the pixman fallback. The Mesa userspace stack is still in `IMAGE_PREINSTALL` (`libgl1-mesa-dri`, `libegl-mesa0`, `libgbm1`) for if GL is revisited.
- **Do NOT add an `ExecStartPre` that writes to `/sys/class/tty/tty0/active`**: `tty0/active` is a **read-only** sysfs file (it reports the currently active VT, e.g. `tty1`). A line like `ExecStartPre=/bin/sh -c 'echo 2 > /sys/class/tty/tty0/active'` makes dash fail to open the file for writing and exit with **status 2**, which systemd reports as `weston.service: Control process exited, code=exited, status=2/INVALIDARGUMENT` — note "**Control** process", i.e. an `ExecStartPre`, not weston itself. The service then crash-loops (`Restart=on-failure`) and the screen stays black even though the whole display chain (LTDC `card0`, `card0-DSI-1 connected`, OTM8009A panel, backlight `power=0`) is fully up. VT acquisition is already handled by weston via `--tty=2` together with `StandardInput=tty` + `TTYPath=/dev/tty2`; no manual VT switch is needed. This bad line was added and then removed — if a black screen ever returns with weston crash-looping, check `systemctl status weston` for a failing `ExecStartPre` before suspecting the panel/DRM.
- **Display debugging**: `meta-isar/recipes-graphics/weston-dk2-config/files/display-debug.sh` is a read-only diagnostics script (DRM devices, sysfs connectors, dmesg DRM/LTDC/DSI/panel lines, deferred-probe state, modetest, Mesa/GL, weston service+journal, framebuffer/backlight). Copy it to the board and run as root to collect the full display state in one shot.
- **expand-rootfs**: A one-shot service (`meta-isar/recipes-support/expand-rootfs/`) that runs at first boot to expand the rootfs partition and filesystem to fill the SD card. Uses `growpart` (from `cloud-guest-utils`) and `resize2fs`. Installed only on `stm32mp157c-dk2`.
- **STM32_THERMAL disabled**: `CONFIG_STM32_THERMAL` is intentionally not set in `meta-isar/recipes-kernel/linux/files/stm32mp15x.cfg` — the thermal driver causes an immediate hardware reset on the F-variant board when using the C-variant DTS.
- `u-boot-script-stm32mp157c-dk2` (`meta-isar/recipes-bsp/u-boot-script-stm32mp157c-dk2/`) overrides the default `/etc/default/u-boot-script` config file using `DEBIAN_REPLACES = "u-boot-script"` and runs `update-u-boot-script` in its `postinst` to regenerate `boot.scr`.
- The multiconfig `meta-isar/conf/multiconfig/stm32mp157c-dk2-bookworm.conf` maps the kas machine/distro selection to the BitBake `mc:stm32mp157c-dk2-bookworm:*` target.

## Contributing

Patches go to the `isar-users@googlegroups.com` mailing list — GitHub PRs are not used for review. The default branch is `master`, but base patches on the `next` branch (`git checkout -b my-work origin/next`). Every patch needs a `Signed-off-by` line (`git commit -s`). See `CONTRIBUTING.md` for full guidelines.

```sh
# Prepare patches against next
git format-patch -v2 --cover-letter origin/next..HEAD

# Send to the mailing list
git send-email --to=isar-users@googlegroups.com *.patch
```

`master` is the main development branch. `next` is the CI/integration branch; changes are promoted from `next` to `master` roughly every two weeks.
