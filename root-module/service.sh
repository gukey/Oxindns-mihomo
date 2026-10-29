#!/system/bin/sh
MODDIR=${0%/*}
COUNT=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$COUNT" -lt 60 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
[ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ] || /system/bin/sh "$MODDIR/ctl.sh" start
