# SPDX-License-Identifier: MIT

DESCRIPTION = "CYW43438 NVRAM symlink for STM32MP157C-DK2"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

DEBIAN_DEPENDS = "firmware-brcm80211"

COMPATIBLE_MACHINE = "^(stm32mp15x)$"

do_install() {
    install -d ${D}/lib/firmware/brcm
    # firmware-brcm80211 ships the Murata 1DX NVRAM as MUR1DX.txt;
    # the driver looks for the board-specific name first.
    ln -s brcmfmac43430-sdio.MUR1DX.txt \
        ${D}/lib/firmware/brcm/brcmfmac43430-sdio.st,stm32mp157c-dk2.txt
}
