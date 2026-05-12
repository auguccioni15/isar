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
- **`kas/`** — Kconfig-based configuration fragments consumed by the `kas` tool: `machine/`, `distro/`, `image/`, `opt/`, `package/`.

### Key concepts

**Multiconfig builds**: Each target is identified as `mc:<machine>-<distro>`. Machine and distro names come from `meta-isar/conf/multiconfig/<machine>-<distro>.conf`. This lets a single `bitbake` invocation build for many machine/distro pairs simultaneously.

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

### Important variables (in `conf/local.conf` or multiconfig files)

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

## STM32MP157F-DK2 target

The `stm32mp157f-dk2` machine (`meta-isar/conf/machine/stm32mp157f-dk2.conf`) is the primary development target in this repo.

**Build command:**
```sh
kas-container --isar build kas/kas-stm32mp1-f-dk2.yaml
```

Key points:

- Extends `stm32mp15x.conf`; uses `stm32mp157c-dk2.dtb` (variant F has no dedicated DTB in TF-A 2.4).
- Boot chain: TF-A (`AARCH32_SP=optee`) → OP-TEE → U-Boot → kernel; WKS file `stm32mp15x-dk2.wks.in` creates a FAT32 bootfs + ext4 rootfs layout.
- OP-TEE (`meta-isar/recipes-bsp/optee-os/optee-os-stm32mp15x_3.21.0.inc`) embeds `stm32mp157c-dk2.dts` (not `ev1.dts` — the upstream default is for the evaluation board, not the DK2).
- U-Boot carries three local patches (`meta-isar/recipes-bsp/u-boot/files/`): `0001` restores `config.mk` for non-FIP stm32image generation; `0002` drops the hard-coded `CONFIG_DEFAULT_DEVICE_TREE="stm32mp157c-ev1"` (so the build system can inject the correct DTS) and enables early debug UART on UART4 (0x40010000, 64 MHz HSI clock); `0003` disables `pwr_regulators` in U-Boot's pre-reloc DT overlay — TF-A maps PWR registers (0x50001000) to the secure world, so probing that driver during pre-reloc crashes U-Boot silently before any console output.
- WiFi (CYW43438 via SDMMC2/SDIO): `CONFIG_BRCMFMAC=y` (built-in, not module) is required — as a module the driver loads too late and the chip's PLL fails to lock (HT Avail timeout). See `meta-isar/recipes-kernel/linux/files/stm32mp15x.cfg`.
- The kernel recipe (`meta-isar/recipes-kernel/linux/linux-stm32mp_6.1.bb`) patches `dwc3-stm32.c` to add a missing `bitfield.h` include and injects `stm32mp157f-dk2-wifi.dtsi` into `stm32mp157c-dk2.dts` to describe the SDIO WiFi node.
- WiFi firmware (`cyfmac43430-sdio`) and Bluetooth come from the Debian `firmware-brcm80211` package installed via `IMAGE_PREINSTALL` (non-free-firmware); no custom firmware recipe.
- `initramfs-tee-ftpm-hook` is removed from `IMAGE_INSTALL` because `CFG_EARLY_TA` is not enabled in OP-TEE, so `/dev/tpmrm0` is absent at initramfs time and the hook would stall the rootfs mount.
- Weston and the on-screen keyboard (`wvkbd`) are configured via the `weston-dk2-config` dpkg-raw package (`meta-isar/recipes-graphics/weston-dk2-config/`), which installs systemd services for both and `weston.ini`. It also provides the multiarch symlink for `weston-desktop-shell` (needed on armhf).
- `u-boot-script-stm32mp157f-dk2` (`meta-isar/recipes-bsp/u-boot-script-stm32mp157f-dk2/`) overrides the default `/etc/default/u-boot-script` config file using `DEBIAN_REPLACES = "u-boot-script"` and runs `update-u-boot-script` in its `postinst` to regenerate `boot.scr`.
- The multiconfig `meta-isar/conf/multiconfig/stm32mp157f-dk2-bookworm.conf` maps the kas machine/distro selection to the BitBake `mc:stm32mp157f-dk2-bookworm:*` target.

## Contributing

Patches go to the `isar-users@googlegroups.com` mailing list — GitHub PRs are not used for review. Base patches on the `next` branch. Every patch needs a `Signed-off-by` line. See `CONTRIBUTING.md` for full guidelines.

`master` is the main development branch. `next` is the CI/integration branch; changes are promoted from `next` to `master` roughly every two weeks.
