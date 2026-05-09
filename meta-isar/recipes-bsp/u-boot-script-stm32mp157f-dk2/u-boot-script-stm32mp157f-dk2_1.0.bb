#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

DESCRIPTION = "U-Boot boot script config for STM32MP157F-DK2"
MAINTAINER = "isar-users <isar-users@googlegroups.com>"

inherit dpkg-raw

# Override the config file installed by u-boot-script.
# Replaces lets dpkg install /etc/default/u-boot-script over the one from
# u-boot-script (a regular file, not a conffile, so no Breaks needed).
DEBIAN_DEPENDS = "u-boot-script"
DEBIAN_REPLACES = "u-boot-script"

SRC_URI = "file://u-boot-script"

COMPATIBLE_MACHINE = "^stm32mp157f-dk2$"

do_install() {
    install -d ${D}/etc/default
    install -m 0644 ${WORKDIR}/u-boot-script ${D}/etc/default/u-boot-script
}

# Regenerate boot.scr with our KERNEL_ARGS regardless of whether the kernel
# postinst hook ran before our package was installed.
do_prepare_build:append() {
    cat > ${S}/debian/${PN}.postinst << 'EOF'
#!/bin/sh
set -e
if [ -x /usr/sbin/update-u-boot-script ]; then
    update-u-boot-script || true
fi
EOF
    chmod 0755 ${S}/debian/${PN}.postinst
}
