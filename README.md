# PocketNAS-Pro

把你的安卓手机变成一台随身 NAS。基于 OpenList 内核，完整本地化 UI，无需 Root。

## 功能

- **OpenList 内核**：多网盘挂载（阿里云盘 / 夸克 / OneDrive / 本地存储等），二进制以 jniLibs 打包，安装即用
- **文件管理**：浏览 / 上传 / 下载 / 新建 / 重命名 / 删除，支持断点续传
- **媒体库**：图片 / 视频 / 音乐自动归类，Media3 ExoPlayer 流式播放（含 MKV / H.265 / FLAC）
- **远程访问**：FTP（5221）/ SFTP（5222）/ WebDAV（5244）开关，局域网直连
- **三级保活**：前台服务 + 独立进程守护 + WorkManager 周期拉起
- **开机自启**：重启手机后自动恢复服务
- **N2N 组网**：预留 Hin2n 公网穿透配置

## 默认登录

首次启动自动初始化管理员：

- 用户名：`admin`
- 密码：`admin123456`

## 技术栈

- Kotlin + Jetpack Compose (Material3)
- OpenList 内核（Go 静态 ELF，arm64-v8a）
- Unix Domain Socket 与内核通信
- Media3 ExoPlayer 媒体播放
- WorkManager 保活

## 构建

push 到 `main` 分支后 GitHub Actions 自动编译 Debug + Release APK，在 Actions 页面下载。

## 许可

- OpenList 内核：AGPLv3
- APP 源码：AGPLv3
