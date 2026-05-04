#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

MAINTAINER = "isar-users <isar-users@googlegroups.com>"

UBOOT_NAME = "stm32mp15x"

require u-boot-${PV}.inc

SRC_URI += "file://0001-stm32mp-restore-config.mk-for-non-FIP-stm32image-gen.patch"

DEBIAN_BUILD_DEPENDS .= ", python3-setuptools, swig, python3-dev:native, libssl-dev:native, libssl-dev"

COMPATIBLE_MACHINE = "^(stm32mp15x)$"
