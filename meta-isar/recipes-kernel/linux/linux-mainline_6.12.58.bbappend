SRC_URI:append:stm32mp15x = " file://stm32mp157c-dk2-wifi.dtsi"

do_prepare_build:append:stm32mp15x() {
    # STM32 DTS moved to arch/arm/boot/dts/st/ in kernel 6.x
    DTS_DIR="${S}/arch/arm/boot/dts/st"
    [ -d "${DTS_DIR}" ] || DTS_DIR="${S}/arch/arm/boot/dts"

    install -m 0644 ${WORKDIR}/stm32mp157c-dk2-wifi.dtsi \
        "${DTS_DIR}/stm32mp157c-dk2-wifi.dtsi"

    if ! grep -q "stm32mp157c-dk2-wifi" "${DTS_DIR}/stm32mp157c-dk2.dts" 2>/dev/null; then
        echo '#include "stm32mp157c-dk2-wifi.dtsi"' >> \
            "${DTS_DIR}/stm32mp157c-dk2.dts"
    fi
}
