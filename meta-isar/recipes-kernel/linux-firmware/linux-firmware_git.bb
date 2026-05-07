#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

inherit dpkg-raw

DESCRIPTION = "Firmware for CYW43438 WiFi (Murata 1DX on STM32MP157-DK2)"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"
LICENSE = "Firmware"

DEBIAN_ARCH = "all"

SRCREV = "56a13f987b301be984584bf3dab5c7e1cf05a5b3"
PV = "1.0+git${SRCPV}"
SRC_URI = "git://git.kernel.org/pub/scm/linux/kernel/git/firmware/linux-firmware.git;protocol=https;branch=main"
S = "${WORKDIR}/git"

do_prepare_build:append() {
    cat >> ${S}/debian/rules << 'EOF'

override_dh_auto_test:

EOF
}

do_install[cleandirs] = "${D}"

do_install() {
    install -d ${D}/lib/firmware/cypress
    install -d ${D}/lib/firmware/brcm

    # WiFi - CYW43438 via SDIO (cyfmac naming in current linux-firmware)
    install -m 0644 ${S}/cypress/cyfmac43430-sdio.bin       ${D}/lib/firmware/cypress/
    install -m 0644 ${S}/cypress/cyfmac43430-sdio.clm_blob  ${D}/lib/firmware/cypress/

    # NVRAM board-specific (aggiunto in linux-firmware da ST per il DK2)
    # ha la precedenza sul generico MUR1DX
    if [ -f ${S}/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.txt ]; then
        install -m 0644 ${S}/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.txt \
            ${D}/lib/firmware/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.txt
    else
        install -m 0644 ${S}/brcm/brcmfmac43430-sdio.MUR1DX.txt ${D}/lib/firmware/brcm/
        ln -sf brcmfmac43430-sdio.MUR1DX.txt \
            ${D}/lib/firmware/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.txt
    fi

    # Symlink generici: brcmfmac cerca i file in brcm/
    ln -sf ../cypress/cyfmac43430-sdio.bin      ${D}/lib/firmware/brcm/brcmfmac43430-sdio.bin
    ln -sf ../cypress/cyfmac43430-sdio.clm_blob ${D}/lib/firmware/brcm/brcmfmac43430-sdio.clm_blob
    ln -sf brcmfmac43430-sdio.st,stm32mp157c-dk2.txt \
                                                ${D}/lib/firmware/brcm/brcmfmac43430-sdio.txt

    # Symlink board-specific per bin e clm_blob
    ln -sf ../cypress/cyfmac43430-sdio.bin      ${D}/lib/firmware/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.bin
    ln -sf ../cypress/cyfmac43430-sdio.clm_blob ${D}/lib/firmware/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.clm_blob
}

COMPATIBLE_MACHINE = "^(stm32mp15x|stm32mp157f-dk2)$"
