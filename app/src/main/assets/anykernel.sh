#!/sbin/sh
# AnyKernel3 Ramdisk Mod Script
# osm0sis @ xda-developers

## AnyKernel setup
# Begin properties
properties() { '
kernel.string=Fallback AnyKernel3 script - HorizonRevamped
do.devicecheck=0
do.modules=0
do.systemless=1
do.cleanup=1
do.cleanuponabort=0
device.name1=
device.name2=
device.name3=
device.name4=
device.name5=
supported.versions=
'; }
# End properties

# Shell variables
block=/dev/block/bootdevice/by-name/boot;
is_slot_device=auto;
ramdisk_compression=auto;
patch_vbmeta_flag=auto;

## AnyKernel methods (DO NOT CHANGE)
. tools/ak3-core.sh;

## AnyKernel boot install
dump_boot;

# Begin ramdisk changes

# End ramdisk changes

# Begin kernel image changes
if [ -f Image.gz ]; then
  ui_print "- Found Image.gz"
  rd_info "- Found Image.gz"
  write_boot;
elif [ -f Image ]; then
  ui_print "- Found Image"
  rd_info "- Found Image"
  write_boot;
elif [ -f zImage ]; then
  ui_print "- Found zImage"
  rd_info "- Found zImage"
  write_boot;
elif [ -f kernel ]; then
  ui_print "- Found kernel"
  rd_info "- Found kernel"
  write_boot;
else
  ui_print "! No kernel image found!"
  rd_info "! No kernel image found!"
  abort "! No kernel image found in the provided package!";
fi;

## End installation 