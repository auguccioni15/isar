#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

# DK2 boards: STPMIC1 is on I2C4 (0x5c002000); patch OP-TEE to allow NS access via ETZPC
SRC_URI:append:stm32mp157c-dk2 = " file://0001-stm32mp1-etzpc-i2c4-ns-rw-stpmic1.patch"
SRC_URI:append:stm32mp157f-dk2 = " file://0001-stm32mp1-etzpc-i2c4-ns-rw-stpmic1.patch"

# DK2 boards: stm32mp157c-dk2.dts include STPMIC1 (stpmic@33) via stm32mp15xx-dkx.dtsi
OPTEE_EXTRA_BUILDARGS:stm32mp157c-dk2 = " \
    TEE_IMPL_VERSION=${PV} \
    ARCH=arm CFG_EMBED_DTB_SOURCE_FILE=stm32mp157c-dk2.dts \
    CFG_TEE_CORE_LOG_LEVEL=2"

OPTEE_EXTRA_BUILDARGS:stm32mp157f-dk2 = " \
    TEE_IMPL_VERSION=${PV} \
    ARCH=arm CFG_EMBED_DTB_SOURCE_FILE=stm32mp157c-dk2.dts \
    CFG_TEE_CORE_LOG_LEVEL=2"
