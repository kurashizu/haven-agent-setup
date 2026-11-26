# Pitfalls

- **Restarting adbd kills Shizuku**: start it again after `adb tcpip` / `adb usb`.
- **The wireless debugging port may not be readable**: on the development phone `getprop service.adb.tls.port` was empty. Read it from the settings page (open the page with `rish` and take a screenshot).
- **`uiautomator dump` does not work under `rish`**, and you may end up reading a stale file. Use screenshots.
- **Some system settings pages hide overlays**: the pill is neither visible nor tappable there.
- **Android 11+ package visibility**: without `<queries>` in the manifest, `getLaunchIntentForPackage` returns null and the button silently does nothing.
- **Overwriting a running binary fails silently**: stop the process, delete the file, write it again and verify the checksum.
- **Hook false positives**: a heredoc that merely contains tap commands also triggers the touch lock. Keep such content in files, not on the command line.
- **No real containers in proot**, and no `/dev/kvm`.
- **Switching Haven to a differently signed build requires uninstalling it**, which destroys the whole Debian environment.
