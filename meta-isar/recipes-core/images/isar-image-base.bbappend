#
# Copyright (c) Siemens AG, 2020-2025
#
# SPDX-License-Identifier: MIT

# STM32MP157F-DK2: install Weston systemd service
dk2_setup_weston() {
    mkdir -p ${ROOTFSDIR}/etc/systemd/system/multi-user.target.wants
    F="${ROOTFSDIR}/etc/systemd/system/weston.service"
    echo '[Unit]'                                            > "$F"
    echo 'Description=Weston Wayland Compositor'           >> "$F"
    echo 'After=systemd-udevd.service'                     >> "$F"
    echo 'ConditionPathExists=/dev/dri/card0'              >> "$F"
    echo ''                                                >> "$F"
    echo '[Service]'                                       >> "$F"
    echo 'ExecStart=/usr/bin/weston --backend=drm-backend.so --tty=2' >> "$F"
    echo 'Restart=on-failure'                              >> "$F"
    echo 'StandardInput=tty'                               >> "$F"
    echo 'TTYPath=/dev/tty2'                               >> "$F"
    echo ''                                                >> "$F"
    echo '[Install]'                                       >> "$F"
    echo 'WantedBy=multi-user.target'                      >> "$F"
    ln -sf /etc/systemd/system/weston.service "${ROOTFSDIR}/etc/systemd/system/multi-user.target.wants/weston.service"
}
