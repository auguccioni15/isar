#
# ST patched kernel for STM32MP1 (CYW43438 WiFi / SDIO fixes)
#
# SPDX-License-Identifier: MIT

inherit linux-kernel

SRCREV = "c3e95fcd0038c21b414a80b77fb09a2063e51c9a"
PV = "6.1+git${SRCPV}"

BB_GIT_SHALLOW = "1"
BB_GIT_SHALLOW_DEPTH = "1"

SRC_URI += " \
    git://github.com/STMicroelectronics/linux.git;protocol=https;branch=v6.1-stm32mp \
    file://stm32mp15x.cfg;apply=no \
    file://stm32mp157c-dk2-wifi.dtsi \
    file://stm32mp157c-dk2-no-hdmi.dtsi"

S = "${WORKDIR}/git"

KERNEL_DEFCONFIG:stm32mp15x = "multi_v7_defconfig"

LINUX_VERSION_EXTENSION = "-stm32mp"

KERNEL_CONFIG_FRAGMENTS:stm32mp15x = "stm32mp15x.cfg"

do_prepare_build:append:stm32mp15x() {
    # dwc3-stm32.c uses FIELD_PREP but misses the bitfield.h include
    DWC3="${S}/drivers/usb/dwc3/dwc3-stm32.c"
    if [ -f "${DWC3}" ] && ! grep -q "bitfield.h" "${DWC3}"; then
        sed -i 's|#include <linux/clk.h>|#include <linux/bitfield.h>\n#include <linux/clk.h>|' "${DWC3}"
    fi

    # v6.1 keeps STM32 DTS directly under arch/arm/boot/dts/
    DTS_DIR="${S}/arch/arm/boot/dts/st"
    [ -d "${DTS_DIR}" ] || DTS_DIR="${S}/arch/arm/boot/dts"

    # Only inject our WiFi DTSI if the upstream DTS doesn't already have it
    if ! grep -q "brcm,bcm4329-fmac" "${DTS_DIR}/stm32mp157c-dk2.dts" 2>/dev/null; then
        install -m 0644 ${WORKDIR}/stm32mp157c-dk2-wifi.dtsi \
            "${DTS_DIR}/stm32mp157c-dk2-wifi.dtsi"
        if ! grep -q "stm32mp157c-dk2-wifi" "${DTS_DIR}/stm32mp157c-dk2.dts" 2>/dev/null; then
            echo '#include "stm32mp157c-dk2-wifi.dtsi"' >> \
                "${DTS_DIR}/stm32mp157c-dk2.dts"
        fi
    fi

    # Disable SII902X HDMI bridge: it does not respond on I2C and keeps
    # LTDC stuck in deferred probe. DSI (OTM8009A) is the only output used.
    install -m 0644 ${WORKDIR}/stm32mp157c-dk2-no-hdmi.dtsi \
        "${DTS_DIR}/stm32mp157c-dk2-no-hdmi.dtsi"
    if ! grep -q "stm32mp157c-dk2-no-hdmi" "${DTS_DIR}/stm32mp157c-dk2.dts" 2>/dev/null; then
        echo '#include "stm32mp157c-dk2-no-hdmi.dtsi"' >> \
            "${DTS_DIR}/stm32mp157c-dk2.dts"
    fi
}

COMPATIBLE_MACHINE = "^(stm32mp15x)$"
