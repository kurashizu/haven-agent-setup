# 踩过的坑

- **重启 adbd 会杀掉 Shizuku**：`adb tcpip` / `adb usb` 之后要重新启动它。
- **读不到无线调试端口**：开发时的手机上 `getprop service.adb.tls.port` 是空的，只能从设置页面看（可用 `rish` 打开页面再截屏）。
- **`rish` 下 `uiautomator dump` 不工作**，而且可能读到旧文件。用截屏代替。
- **系统设置的部分页面会隐藏悬浮窗**，胶囊看不见也点不到。
- **Android 11+ 包可见性**：不在清单里声明 `<queries>`，`getLaunchIntentForPackage` 返回空，按钮点了没反应。
- **更新正在运行的二进制会静默失败**：先结束进程、删文件、再写，并核对校验值。
- **hook 误匹配**：写测试脚本的 heredoc 里出现点击指令，也会触发锁屏。把这类内容放进文件，不要写在命令行里。
- **proot 里不能跑真正的容器**，也没有 `/dev/kvm`。
- **Haven 换签名渠道要卸载重装**，会丢掉整个 Debian 环境。
