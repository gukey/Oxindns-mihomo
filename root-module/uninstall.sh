#!/system/bin/sh
MODDIR=${0%/*}
/system/bin/sh "$MODDIR/ctl.sh" stop
# 保留用户配置；用户可在确认不再需要后手动删除 /data/adb/netpanel。
