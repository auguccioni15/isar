#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

# STM32MP157F-DK2: install Weston systemd service
dk2_setup_weston() {
    mkdir -p ${ROOTFSDIR}/etc/systemd/system/multi-user.target.wants
    printf '%s\n' \
        '[Unit]' \
        'Description=Weston Wayland Compositor' \
        'After=systemd-udevd.service' \
        'ConditionPathExists=/dev/dri/card0' \
        '' \
        '[Service]' \
        'ExecStart=/usr/bin/weston --backend=drm-backend.so --tty=2' \
        'Restart=on-failure' \
        'StandardInput=tty' \
        'TTYPath=/dev/tty2' \
        '' \
        '[Install]' \
        'WantedBy=multi-user.target' \
        > ${ROOTFSDIR}/etc/systemd/system/weston.service
    ln -sf /etc/systemd/system/weston.service \
        ${ROOTFSDIR}/etc/systemd/system/multi-user.target.wants/weston.service
}
