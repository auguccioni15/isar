#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

DESCRIPTION = "Weston compositor systemd service for STM32MP157 DK2"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

DEBIAN_DEPENDS = "weston, weston-terminal"

SRC_URI = "file://weston.service \
           file://wvkbd.service \
           file://weston.ini"

COMPATIBLE_MACHINE = "^(stm32mp157c-dk2|stm32mp157f-dk2)$"

do_install() {
    install -d ${D}/lib/systemd/system
    install -d ${D}/lib/systemd/system/multi-user.target.wants
    install -m 0644 ${WORKDIR}/weston.service ${D}/lib/systemd/system/weston.service
    ln -sf /lib/systemd/system/weston.service \
        ${D}/lib/systemd/system/multi-user.target.wants/weston.service
    install -m 0644 ${WORKDIR}/wvkbd.service ${D}/lib/systemd/system/wvkbd.service
    ln -sf /lib/systemd/system/wvkbd.service \
        ${D}/lib/systemd/system/multi-user.target.wants/wvkbd.service
    install -d ${D}/etc/xdg/weston
    install -m 0644 ${WORKDIR}/weston.ini ${D}/etc/xdg/weston/weston.ini
}
