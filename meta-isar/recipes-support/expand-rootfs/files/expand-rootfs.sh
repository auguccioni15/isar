#!/bin/sh
# Expand the rootfs partition and filesystem to fill the SD card.
#
# growpart exits 0 if it grew the partition, non-zero (NOCHANGE) if the
# partition already fills the disk.  Only run resize2fs when the partition
# was actually extended.
set -e

DISK=/dev/mmcblk0
PART=8
PART_DEV="${DISK}p${PART}"

growpart "${DISK}" "${PART}" || exit 0
resize2fs "${PART_DEV}"
