#!/system/bin/sh
SKIPUNZIP=0
[ "$ARCH" = "arm64" ] || abort "仅支持 arm64-v8a；请勿刷入其他架构。"
[ "$API" -ge 26 ] || abort "需要 Android 8.0 或更高版本。"
DATA=${NETPANEL_DATA_DIR:-/data/adb/netpanel}
case "$DATA" in /data/adb/netpanel|/data/local/tmp/netpanel-release-test/state) ;; *) abort "数据目录不在允许范围内。" ;; esac
umask 077
mkdir -p "$DATA/oxidns" "$DATA/mihomo/providers" "$DATA/logs" "$DATA/run"
chmod 700 "$DATA" "$DATA/oxidns" "$DATA/mihomo" "$DATA/mihomo/providers" "$DATA/logs" "$DATA/run"
secret() { od -An -N24 -tx1 /dev/urandom | tr -d ' \n'; }
if [ ! -f "$DATA/oxidns/config.yaml" ]; then
  DNS_PASSWORD=$(secret)
  [ "${#DNS_PASSWORD}" -eq 48 ] || abort "无法生成 DNS 密码。"
  sed "s/__DNS_PASSWORD__/$DNS_PASSWORD/g" "$MODPATH/config/oxidns.yaml" > "$DATA/oxidns/config.yaml" || abort "创建 DNS 配置失败。"
fi
if [ ! -f "$DATA/mihomo/config.yaml" ]; then
  MIHOMO_SECRET=$(secret)
  [ "${#MIHOMO_SECRET}" -eq 48 ] || abort "无法生成 Mihomo Secret。"
  sed "s/__MIHOMO_SECRET__/$MIHOMO_SECRET/g" "$MODPATH/config/mihomo.yaml" > "$DATA/mihomo/config.yaml" || abort "创建 Mihomo 配置失败。"
fi
# 配置文件是凭据的唯一来源，升级不生成另一份可能与用户修改不一致的凭据副本。
chmod 600 "$DATA/oxidns/config.yaml" "$DATA/mihomo/config.yaml"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/bin/oxidns" 0 0 0755
set_perm "$MODPATH/bin/mihomo" 0 0 0755
for SCRIPT in service.sh ctl.sh uninstall.sh; do set_perm "$MODPATH/$SCRIPT" 0 0 0755; done
ui_print "配置保留在 /data/adb/netpanel；旧配置不会覆盖。"
ui_print "API 仅监听本机，随机认证信息请在本机配置文件中查看。"
ui_print "默认不启用 TUN，不改系统代理、私人 DNS 或防火墙。"
ui_print "如已有同端口服务，请先按部署文档处理冲突，勿同时启动。"
