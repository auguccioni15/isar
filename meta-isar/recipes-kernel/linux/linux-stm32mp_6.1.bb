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

    # OTM8009A DSI panel: backport two fixes from the v6.6-stm32mp driver that
    # are missing at this v6.1 SRCREV and leave the DK2 panel dark even though
    # the LTDC/DSI pipeline is fully up (CRTC active, correct 480x800@29.7 mode,
    # zero FIFO underrun, panel driver bound, rails on). Verified by diffing
    # against the known-good OpenSTLinux 6.6 driver that lights the same panel.
    #   1. Add MIPI_DSI_MODE_NO_EOT_PACKET: without it the DSI emits EoT packets
    #      and the OTM8009A never locks onto the video stream -> black.
    #   2. Issue a real reset pulse (assert 20ms, de-assert 100ms) on resume.
    #      The v6.1 driver only de-asserts reset, so the panel controller is
    #      never reset before its DCS init sequence runs.
    PANEL="${S}/drivers/gpu/drm/panel/panel-orisetech-otm8009a.c"
    if [ -f "${PANEL}" ] && ! grep -q "MIPI_DSI_MODE_NO_EOT_PACKET" "${PANEL}"; then
        sed -i 's@MIPI_DSI_CLOCK_NON_CONTINUOUS;@MIPI_DSI_CLOCK_NON_CONTINUOUS |\n\t\t\t  MIPI_DSI_MODE_NO_EOT_PACKET;@' "${PANEL}"
        sed -i 's@\tgpiod_set_value_cansleep(ctx->reset_gpio, 0);@\tgpiod_set_value_cansleep(ctx->reset_gpio, 1);\n\tmsleep(20);\n\tgpiod_set_value_cansleep(ctx->reset_gpio, 0);@' "${PANEL}"
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
    # LTDC stuck in deferred probe. DSI is the only output used.
    install -m 0644 ${WORKDIR}/stm32mp157c-dk2-no-hdmi.dtsi \
        "${DTS_DIR}/stm32mp157c-dk2-no-hdmi.dtsi"
    if ! grep -q "stm32mp157c-dk2-no-hdmi" "${DTS_DIR}/stm32mp157c-dk2.dts" 2>/dev/null; then
        echo '#include "stm32mp157c-dk2-no-hdmi.dtsi"' >> \
            "${DTS_DIR}/stm32mp157c-dk2.dts"
    fi
}

COMPATIBLE_MACHINE = "^(stm32mp15x)$"
