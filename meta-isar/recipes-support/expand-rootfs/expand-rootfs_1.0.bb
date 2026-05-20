#
# SPDX-License-Identifier: MIT

DESCRIPTION = "Expand rootfs partition and filesystem to fill the SD card at first boot"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

# growpart is provided by cloud-guest-utils; resize2fs by e2fsprogs (already
# pulled in by Isar's base image).  python3 is required by growpart.
DEBIAN_DEPENDS = "cloud-guest-utils, python3"

SRC_URI = "file://expand-rootfs.sh \
           file://expand-rootfs.service"

COMPATIBLE_MACHINE = "^(stm32mp157c-dk2)$"

do_install() {
    install -d ${D}/usr/local/sbin
    install -m 0755 ${WORKDIR}/expand-rootfs.sh ${D}/usr/local/sbin/expand-rootfs.sh

    install -d ${D}/lib/systemd/system
    install -d ${D}/lib/systemd/system/multi-user.target.wants
    install -m 0644 ${WORKDIR}/expand-rootfs.service \
        ${D}/lib/systemd/system/expand-rootfs.service
    ln -sf /lib/systemd/system/expand-rootfs.service \
        ${D}/lib/systemd/system/multi-user.target.wants/expand-rootfs.service
}
