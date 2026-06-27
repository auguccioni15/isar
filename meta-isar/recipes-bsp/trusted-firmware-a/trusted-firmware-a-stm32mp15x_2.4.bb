#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

inherit trusted-firmware-a

MAINTAINER = "isar-users <isar-users@googlegroups.com>"

TF_A_NAME = "stm32mp15x"

SRC_URI += "https://github.com/ARM-software/arm-trusted-firmware/archive/v${PV}.tar.gz;downloadfilename=arm-trusted-firmware-${PV}.tar.gz \
            file://0001-fix-maybe-uninitialized-result-in-stm32image_partition_read.patch \
            file://0002-plat-st-stm32mp1-add-GNU-stack-note-to-stm32mp1_hel.patch \
            file://0003-Makefile-suppress-execstack-warning-with-binutils-2-.patch"
SRC_URI[sha256sum] = "4bfda9fdbe5022f2e88ad3344165f7d38a8ae4a0e2d91d44d9a1603425cc642d"

S = "${WORKDIR}/arm-trusted-firmware-${PV}"

DEBIAN_BUILD_DEPENDS += ", device-tree-compiler"

TF_A_PLATFORM = "stm32mp1"
TF_A_EXTRA_BUILDARGS = " \
    ARCH=aarch32 ARM_ARCH_MAJOR=7 AARCH32_SP=optee \
    STM32MP_SDMMC=1 STM32MP_EMMC=1 \
    STM32MP_RAW_NAND=1 STM32MP_SPI_NAND=1 STM32MP_SPI_NOR=1 \
    DTB_FILE_NAME=stm32mp157c-ev1.dtb"
TF_A_BINARIES = "release/tf-a-stm32mp157c-ev1.stm32"

COMPATIBLE_MACHINE = "^(stm32mp15x)$"
