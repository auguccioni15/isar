#
# Copyright (c) Siemens AG, 2018-2025
#
# SPDX-License-Identifier: MIT

inherit u-boot

SRC_URI += " \
    https://ftp.denx.de/pub/u-boot/u-boot-${PV}.tar.bz2 \
    "
SRC_URI[sha256sum] = "e00e6c6f014e046101739d08d06f328811cebcf5ae101348f409cbbd55ce6900"

S = "${WORKDIR}/u-boot-${PV}"
