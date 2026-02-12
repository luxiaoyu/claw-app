  #!/bin/bash
  # check_openclaw.sh

  echo "=== OpenClaw Gateway Status ==="

  # 方法1: 检查进程
  PID=$(adb shell "pgrep -f 'openclaw.*gateway' 2>/dev/null" | tr -d '\r')
  if [ -n "$PID" ]; then
      echo "✓ Process found (PID: $PID)"
      adb shell "ps -p $PID -o pid,etime,args 2>/dev/null"
  else
      echo "✗ Process not found"
  fi

  # 方法2: 检查 PID 文件
  echo ""
  echo "=== PID File Check ==="
  adb shell '
    PID_FILE="/data/data/com.termux/files/home/.openclaw/gateway.pid"
    if [ -f "$PID_FILE" ]; then
      echo "PID file exists: $(cat $PID_FILE)"
    else
      echo "PID file not found"
    fi
  '

  # 方法3: 查看日志
  echo ""
  echo "=== Last 10 lines of gateway.log ==="
  adb shell "tail -n 10 /data/data/com.termux/files/home/.openclaw/gateway.log 2>/dev/null || echo 'No log file'"