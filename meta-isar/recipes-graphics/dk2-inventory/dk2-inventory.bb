#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

DESCRIPTION = "Warehouse stock tracker (SQLite + GTK) for STM32MP157 DK2"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

# python3-gi + gir1.2-gtk-3.0 for the GTK UI; sqlite3 ships with python3
# (libsqlite3-0 listed explicitly to be safe). librsvg2-common gives
# gdk-pixbuf its SVG loader (the CARICO/SCARICO radio buttons render SVG
# theme assets) and shared-mime-info the MIME database to find it. The
# Magazzino tile only appears in dk2-launcher's grid when this package is
# installed.
DEBIAN_DEPENDS = "python3-gi, gir1.2-gtk-3.0, libsqlite3-0, librsvg2-common, shared-mime-info"

SRC_URI = "file://dk2-inventory \
           file://magazzino.desktop"

COMPATIBLE_MACHINE = "^(stm32mp15x)$"

do_install() {
    install -d ${D}/usr/bin
    install -m 0755 ${WORKDIR}/dk2-inventory ${D}/usr/bin/dk2-inventory

    install -d ${D}/etc/dk2-launcher/apps
    install -m 0644 ${WORKDIR}/magazzino.desktop \
        ${D}/etc/dk2-launcher/apps/magazzino.desktop
}
