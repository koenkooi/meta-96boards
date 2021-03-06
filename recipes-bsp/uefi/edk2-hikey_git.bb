require edk2_git.bb

COMPATIBLE_MACHINE = "hikey"

DEPENDS_append = " dosfstools-native mtools-native grub optee-os"

SRCREV_edk2 = "76c7cfcc22c7448638acb6f904088b2ff3f79f63"
SRCREV_atf = "bdec62eeb8f3153a4647770e08aafd56a0bcd42b"
SRCREV_openplatformpkg = "db64042266ed377f4a6748232497de8e05d36e35"

SRC_URI = "git://github.com/96boards-hikey/edk2.git;name=edk2;branch=hikey-aosp \
           git://github.com/96boards-hikey/arm-trusted-firmware.git;name=atf;branch=hikey;destsuffix=git/atf \
           git://github.com/96boards-hikey/OpenPlatformPkg.git;name=openplatformpkg;branch=hikey-aosp;destsuffix=git/OpenPlatformPkg \
           file://grub.cfg.in \
          "

OPTEE_OS_ARG = "-s ${EDK2_DIR}/optee_os"

# We need the secure payload (Trusted OS) built from OP-TEE Trusted OS (tee.bin)
# but we have already built tee.bin from optee-os recipe and
# uefi-build.sh script has a few assumptions...
# Copy tee.bin and create dummy files to make uefi-build.sh script happy
do_compile_prepend() {
    install -D -p -m0644 \
      ${STAGING_DIR_HOST}/lib/firmware/tee.bin \
      ${EDK2_DIR}/optee_os/out/arm-plat-hikey/core/tee.bin

    mkdir -p ${EDK2_DIR}/optee_os/documentation
    touch ${EDK2_DIR}/optee_os/documentation/optee_design.md

    printf "all:\n"  > ${EDK2_DIR}/optee_os/Makefile
    printf "\ttrue" >> ${EDK2_DIR}/optee_os/Makefile
}

do_install() {
    install -D -p -m0644 ${EDK2_DIR}/Build/HiKey/RELEASE_GCC49/AARCH64/AndroidFastbootApp.efi ${D}/boot/EFI/BOOT/fastboot.efi
    install -D -p -m0644 ${EDK2_DIR}/atf/build/${UEFIMACHINE}/release/bl1.bin ${D}${libdir}/edk2/bl1.bin

    # Install grub configuration
    sed -e "s|@DISTRO|${DISTRO}|" \
        -e "s|@KERNEL_IMAGETYPE|${KERNEL_IMAGETYPE}|" \
        -e "s|@CMDLINE|${CMDLINE}|" \
        < ${WORKDIR}/grub.cfg.in \
        > ${WORKDIR}/grub.cfg
    install -D -p -m0644 ${WORKDIR}/grub.cfg ${D}/boot/grub/grub.cfg
}

# Create a 64M boot image. block size is 1024. (64*1024=65536)
BOOT_IMAGE_SIZE = "65536"
BOOT_IMAGE_BASE_NAME = "boot-${PKGV}-${PKGR}-${MACHINE}-${DATETIME}"
BOOT_IMAGE_BASE_NAME[vardepsexclude] = "DATETIME"

# HiKey boot image requires fastboot and grub EFI
# ensure we deploy grubaa64.efi before we try to create the boot image.
do_deploy[depends] += "grub:do_deploy"
do_deploy() {
    install -D -p -m0644 ${EDK2_DIR}/atf/build/${UEFIMACHINE}/release/fip.bin ${DEPLOY_DIR_IMAGE}/fip.bin

    # Ship nvme.img with UEFI binaries for convenience
    dd if=/dev/zero of=${DEPLOY_DIR_IMAGE}/nvme.img bs=128 count=1024

    # Create boot image
    mkfs.vfat -F32 -n "boot" -C ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img ${BOOT_IMAGE_SIZE}
    mmd -i ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img ::EFI
    mmd -i ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img ::EFI/BOOT
    mcopy -i ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img ${EDK2_DIR}/Build/HiKey/RELEASE_GCC49/AARCH64/AndroidFastbootApp.efi ::EFI/BOOT/fastboot.efi
    mcopy -i ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img ${DEPLOY_DIR_IMAGE}/grubaa64.efi ::EFI/BOOT/grubaa64.efi
    chmod 644 ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_BASE_NAME}.uefi.img
    rm -f ${DEPLOY_DIR_IMAGE}/grubaa64.efi

    (cd ${DEPLOY_DIR_IMAGE} && ln -sf ${BOOT_IMAGE_BASE_NAME}.uefi.img boot-${MACHINE}.uefi.img)
}
