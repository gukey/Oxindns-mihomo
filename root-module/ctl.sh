#!/system/bin/sh
# 仅管理本模块记录且 /proc/PID/exe 与自身二进制一致的进程。
MODDIR=$(cd "${0%/*}" && pwd -P) || exit 1
DATA=${NETPANEL_DATA_DIR:-/data/adb/netpanel}
case "$DATA" in /data/adb/netpanel|/data/local/tmp/netpanel-release-test/state) ;; *) echo "拒绝非预期的数据目录"; exit 1 ;; esac
[ "$(id -u)" = 0 ] || { echo "需要 Root"; exit 1; }
umask 077
mkdir -p "$DATA/run" "$DATA/logs"
owned() {
  [ -f "$DATA/run/$1.pid" ] || return 1
  PID=$(cat "$DATA/run/$1.pid")
  case "$PID" in ''|*[!0-9]*) return 1 ;; esac
  [ "$(readlink "/proc/$PID/exe" 2>/dev/null)" = "$MODDIR/bin/$1" ] && kill -0 "$PID" 2>/dev/null
}
prepare() {
  [ -f "$DATA/oxidns/config.yaml" ] && [ -f "$DATA/mihomo/config.yaml" ] || { echo "缺少配置，请先安装模块"; return 1; }
  for PAIR in oxidns:webui:oxidns mihomo:ui:mihomo; do
    SUB=${PAIR%%:*}; REST=${PAIR#*:}; LINK=${REST%%:*}; WEB=${REST#*:}
    if [ ! -e "$DATA/$SUB/$LINK" ]; then
      ln -sfn "$MODDIR/web/$WEB" "$DATA/$SUB/$LINK" || return 1
    fi
  done
}
check() {
  prepare || return 1
  "$MODDIR/bin/oxidns" check -d "$DATA/oxidns" -c "$DATA/oxidns/config.yaml" || return 1
  "$MODDIR/bin/mihomo" -t -d "$DATA/mihomo" -f "$DATA/mihomo/config.yaml"
}
stop_one() {
  NAME=$1
  if owned "$NAME"; then
    kill -TERM "$PID" || return 1
    N=0
    while owned "$NAME" && [ "$N" -lt 10 ]; do sleep 1; N=$((N + 1)); done
    if owned "$NAME"; then kill -KILL "$PID" || return 1; fi
  elif [ -f "$DATA/run/$NAME.pid" ]; then
    OLD=$(cat "$DATA/run/$NAME.pid")
    case "$OLD" in ''|*[!0-9]*) ;; *)
      if kill -0 "$OLD" 2>/dev/null; then echo "$NAME PID 不属于本模块，拒绝终止"; return 1; fi ;;
    esac
  fi
  rm -f "$DATA/run/$NAME.pid"
}
start_one() {
  NAME=$1
  owned "$NAME" && return 0
  [ ! -f "$DATA/logs/$NAME.log" ] || mv -f "$DATA/logs/$NAME.log" "$DATA/logs/$NAME.previous.log"
  if [ "$NAME" = oxidns ]; then
    nohup "$MODDIR/bin/oxidns" start -d "$DATA/oxidns" -c "$DATA/oxidns/config.yaml" > "$DATA/logs/$NAME.log" 2>&1 < /dev/null &
  else
    nohup "$MODDIR/bin/mihomo" -d "$DATA/mihomo" -f "$DATA/mihomo/config.yaml" > "$DATA/logs/$NAME.log" 2>&1 < /dev/null &
  fi
  echo "$!" > "$DATA/run/$NAME.pid"
  sleep 1
  owned "$NAME" || { echo "$NAME 未成功启动，请在本机检查私有日志"; return 1; }
}
start() {
  # 通用模块不接管现有部署；隔离测试目录使用完全不同的端口。
  if [ "$DATA" = /data/adb/netpanel ]; then
    for NAME in oxidns mihomo; do
      for OTHER in $(pidof "$NAME" 2>/dev/null); do
        if ! owned "$NAME" || [ "$OTHER" != "$PID" ]; then
          echo "检测到其他 $NAME 实例，拒绝重复启动；请先处理旧部署"; return 1
        fi
      done
    done
  fi
  check || return 1
  OWN_DNS=0
  owned oxidns && OWN_DNS=1
  start_one oxidns || return 1
  if ! start_one mihomo; then
    [ "$OWN_DNS" = 1 ] || stop_one oxidns
    return 1
  fi
  echo "服务已启动；默认仅提供本机 DNS 和显式代理入口"
}
case "${1:-status}" in
  status)
    for NAME in oxidns mihomo; do
      if owned "$NAME"; then echo "$NAME: running ($PID)"; else echo "$NAME: stopped / untracked"; fi
    done
    exit 0 ;;
  start|stop|restart|check) ;;
  *) echo "用法: sh ctl.sh {start|stop|restart|status|check}"; exit 2 ;;
esac
mkdir "$DATA/run/ctl.lock" 2>/dev/null || { echo "另一管理命令正在运行；若上次被中断，请确认后移除 run/ctl.lock 空目录"; exit 1; }
trap 'rmdir "$DATA/run/ctl.lock" 2>/dev/null' EXIT
trap 'exit 130' INT TERM
case "$1" in
  check) check ;;
  start) start ;;
  stop) stop_one mihomo && stop_one oxidns ;;
  restart) stop_one mihomo && stop_one oxidns && start ;;
esac
