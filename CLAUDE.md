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

**Package recipes**: Custom packages inherit `dpkg.bbclass` (or `dpkg-raw`, `dpkg-gbp`, `dpkg-prebuilt`, etc. from `meta/classes-recipe/`). Packages are built by `sbuild` inside the schroot and deposited into a local apt repo before being installed into the rootfs.

**Image types**: Controlled by `IMAGE_FSTYPES`; `wic` images use `.wks.in` files under `meta-isar/scripts/lib/wic/canned-wks/` and `meta/scripts/lib/wic/`.

**Cross-compilation**: Enabled globally with `ISAR_CROSS_COMPILE = "1"`. Opt individual recipes out with `ISAR_CROSS_COMPILE = "0"`.

### Important variables (in `conf/local.conf` or multiconfig files)

| Variable | Purpose |
|---|---|
| `MACHINE` | Target hardware (e.g. `qemuamd64`) |
| `DISTRO` | Debian distribution (e.g. `debian-bookworm`) |
| `DISTRO_ARCH` | Target architecture (e.g. `amd64`, `armhf`, `arm64`) |
| `IMAGE_INSTALL` | Space-separated list of packages to install into the image |
| `KERNEL_NAME` | Selects the kernel recipe (`linux-<name>`) |
| `IMAGE_FSTYPES` | Output image formats (`ext4`, `wic`, `oci-archive`, …) |
| `ISAR_CROSS_COMPILE` | `"1"` to enable cross-compilation |

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

## Contributing

Patches go to the `isar-users@googlegroups.com` mailing list — GitHub PRs are not used for review. Base patches on the `next` branch. Every patch needs a `Signed-off-by` line. See `CONTRIBUTING.md` for full guidelines.

`master` is the main development branch. `next` is the CI/integration branch; changes are promoted from `next` to `master` roughly every two weeks.
