#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

DESCRIPTION = "Fullscreen GTK launcher with large app icons for STM32MP157 DK2"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

# python3-gi + gir1.2-gtk-3.0 for the GTK UI; adwaita-icon-theme supplies
# the launcher tile icons; weston provides weston-terminal.
DEBIAN_DEPENDS = "python3-gi, gir1.2-gtk-3.0, adwaita-icon-theme, weston"

SRC_URI = "file://dk2-launcher \
           file://dk2-launcher.service \
           file://terminal.desktop"

COMPATIBLE_MACHINE = "^(stm32mp15x)$"

do_install() {
    install -d ${D}/usr/bin
    install -m 0755 ${WORKDIR}/dk2-launcher ${D}/usr/bin/dk2-launcher

    # App tiles: one .desktop per app. Other packages (e.g. dk2-inventory)
    # drop their own .desktop files into this same directory.
    install -d ${D}/etc/dk2-launcher/apps
    install -m 0644 ${WORKDIR}/terminal.desktop \
        ${D}/etc/dk2-launcher/apps/terminal.desktop

    install -d ${D}/lib/systemd/system
    install -d ${D}/lib/systemd/system/multi-user.target.wants
    install -m 0644 ${WORKDIR}/dk2-launcher.service \
        ${D}/lib/systemd/system/dk2-launcher.service
    ln -sf /lib/systemd/system/dk2-launcher.service \
        ${D}/lib/systemd/system/multi-user.target.wants/dk2-launcher.service
}
