PocketNAS-Pro 内核二进制说明
================================

自 v0.16.0 起，内核二进制改以 native library 形式打包：

    位置: app/src/main/jniLibs/arm64-v8a/libopenlist.so
    来源: OpenList 内核 arm64 静态 ELF 交叉编译产物

为什么放 jniLibs 而不是 assets：
- MIUI / 高版本 Android 的 SELinux 策略拒绝 app 进程直接 exec
  应用私有目录（app_data_file）下的 ELF（实测 error=13 EACCES）
- jniLibs + useLegacyPackaging=true 会让系统在安装时解压到
  nativeLibraryDir（标准 native lib 路径，app 可直接执行），绕开限制

二进制特性：
- 纯静态 ELF（CGO_ENABLED=0），无 glibc 依赖，Android arm64 可直接执行
- 默认只监听 Unix Domain Socket（data/openlist.sock），不监听任何 TCP 端口
- 已移除 Web 前端（无网页后台），保留全部 /api/* 业务接口
- 新增 /api/nas/status 扩展接口

重新编译方式（在仓库根目录）：

    chmod +x scripts/build-arm64-static.sh
    VERSION=v0.1.0 scripts/build-arm64-static.sh

产物输出到 dist/openlist-linux-arm64，复制替换 libopenlist.so 即可。
