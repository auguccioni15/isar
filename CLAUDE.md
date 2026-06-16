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
- The kernel recipe (`meta-isar/recipes-kernel/linux/linux-stm32mp_6.1.bb`) patches `dwc3-stm32.c` to add a missing `bitfield.h` include and, in `do_prepare_build:append`, injects three `.dtsi` overlays by appending `#include` lines to `stm32mp157c-dk2.dts`: `-wifi.dtsi` (SDIO WiFi node), `-no-hdmi.dtsi` (disables the unresponsive SII902X HDMI bridge that otherwise pins LTDC in deferred probe), and `-frida-panel.dtsi` (display panel, see below).
- **Display panel (FRIDA FRD397B25009 / Novatek NT35510)**: The board ships a FRIDA NT35510 DSI panel, *not* the reference MB1166 OTM8009A that `stm32mp157c-dk2.dts` hardcodes. `stm32mp157c-dk2-frida-panel.dtsi` deletes the `panel-otm8009a@0` node, adds an NT35510 panel (`compatible = "frida,frd400b25025"` — the FRD397B25009 is electrically the same controller and reuses that compatible), re-points the `dsi_out` endpoint, and drops the now-dangling `panel` phandle from the ft6236 touch node. The v6.1 stock `panel-novatek-nt35510.c` lacks the FRIDA variant, so the recipe overwrites it with a v6.12 backport (`meta-isar/recipes-kernel/linux/files/panel-novatek-nt35510.c`). Sending the OTM8009A init sequence to this panel leaves it dark even though DRM/Weston come up.
- **DRM core must be built-in**: `multi_v7_defconfig` ships `CONFIG_DRM=m`, and Kconfig cannot keep a `=y` child on a `=m` core, so every `CONFIG_DRM_*=y` is silently demoted to `=m` unless `CONFIG_DRM=y` is set explicitly. As modules the LTDC/DSI stack loads too late, the panel never attaches and the display stays off. `stm32mp15x.cfg` therefore forces `CONFIG_DRM=y`, `CONFIG_DRM_KMS_HELPER=y`, plus the STM DSI host glue (`CONFIG_DRM_STM_DSI`, `CONFIG_DRM_DW_MIPI_DSI`) needed to bridge LTDC to the DSI panel.
- WiFi firmware and Bluetooth come from the Debian `firmware-brcm80211` package (`IMAGE_PREINSTALL`, non-free-firmware). Additionally, `firmware-brcm-dk2` (`meta-isar/recipes-bsp/firmware-brcm-dk2/`) is a `dpkg-raw` package that creates the board-specific NVRAM symlink the driver looks for first: `brcmfmac43430-sdio.st,stm32mp157c-dk2.txt → brcmfmac43430-sdio.MUR1DX.txt`.
- `initramfs-tee-ftpm-hook` is removed from `IMAGE_INSTALL` because `CFG_EARLY_TA` is not enabled in OP-TEE, so `/dev/tpmrm0` is absent at initramfs time and the hook would stall the rootfs mount.
- Weston and the on-screen keyboard (`wvkbd`) are configured via the `weston-dk2-config` dpkg-raw package (`meta-isar/recipes-graphics/weston-dk2-config/`), which installs systemd services for both and `weston.ini`. It also provides the multiarch symlink for `weston-desktop-shell` (needed on armhf).
- **DRM card ordering & rendering**: The Etnaviv GPU (GC400, present on all MP157 variants) probes first and claims `card0`; the LTDC display controller claims `card1`. `weston.service` passes `--drm-device=card1` and waits for `/dev/dri/card1` via a poll loop (`until [ -e /dev/dri/card1 ]`) rather than `Requires=dev-dri-card1.device` — the systemd device unit can time out before LTDC completes its deferred probe. `DRM_STM` (LTDC) and `DRM_ETNAVIV` (GPU) are both enabled. The branch is currently trying **hardware GL rendering** via a split render/scanout setup: Weston renders on the etnaviv GPU (`renderD128`) and scans out on the LTDC KMS device (`card1`), which is why `--use-pixman` was dropped from `ExecStart`. This requires the Mesa userspace stack in `IMAGE_PREINSTALL` (`libgl1-mesa-dri` for the etnaviv Gallium DRI driver, plus `libegl-mesa0`/`libgbm1` for EGL/GBM). If GL proves unreliable on this display-only LTDC path, the fallback is to re-add `--use-pixman` (CPU rendering straight to the LTDC framebuffer).
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
