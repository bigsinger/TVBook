# TVBook

TVBook 是一个面向 Android 电视设备的离线绘本/音频播放器。把内容和配置放到 U 盘根目录的 `tvbooks` 文件夹，插入电视后即可通过遥控器播放。

## 兼容版本

- 最低支持：Android 4.2 / API 17
- 当前工程：`minSdkVersion 17`，`targetSdkVersion 28`
- 当前版本：`versionName 1.8`，`versionCode 9`
- 本地 SDK：`D:\Android\Sdk`

## U 盘目录

```text
tvbooks/
  config/
    settings.properties
    playback.properties
    favorites.properties
    catalog.properties
  huiben/
    chitang/
      1.jpg
      1.mp3
      2.jpg
      2.mp3
  gushi/
    001.mp3
```

`config` 目录由应用自动创建，用于保存播放设置、断点续播、收藏记录和目录缓存。拔下 U 盘后，这些记录仍保留在 U 盘的 `tvbooks/config` 下。

## 自动目录菜单

主菜单不再依赖 `menu.txt`。点击“同步目录”后，应用读取 `tvbooks` 下最多三级的目录关系并保存到配置；启动和菜单导航只读取这份配置。

“同步目录”会在后台一次性缓存一级、二级、三级目录关系，不递归进入第四级，也不统计或缓存媒体文件数量。到达可播放目录时才快速判断绘本或音频类型，并继续使用原有播放器。

## 遥控器操作

主界面保持 1.5 的固定三栏布局：左侧一级菜单，中间显示二级菜单或三级菜单，右侧显示状态和设置。二级目录有子目录时，中间区域切换为三级列表；没有子目录时直接播放。按 `Left` 或 `Back` 返回二级列表。

主界面右侧的“同步目录”按钮会后台缓存三级目录树，也可以在主界面按 `Menu` 触发同步。同步完成后，菜单树一次性加载到内存，导航期间不访问移动硬盘或反复读取配置。“自动间隔(秒)”用于控制自动播放等待时间。

绘本播放：

- `Left` / `Right`：上一页 / 下一页
- `Up` / `Down`：上一本 / 下一本
- `OK`：暂停或继续
- `Menu`：显示或隐藏底部轻量控制栏
- 底部控制栏显示后，10 秒无操作会自动隐藏
- 控制栏显示后：`Left` / `Right` 在按钮间移动焦点，`OK/Enter` 执行当前按钮，`Back` 隐藏控制栏
- `Bookmark` / `*`：收藏或取消收藏当前绘本

自动播放时，绘本会等待“自动间隔”和当前页音频播放完成二者中更晚的时间点再翻页；最后一页会在到点后切到下一本。手动翻页会立即废弃旧页面音频请求，避免旧音频完成回调影响新页面。

音频播放：

- `Left` / `Right`：快退 / 快进
- `Up` / `Down`：上一首 / 下一首
- `OK`：暂停或继续
- `Menu`：显示或隐藏底部轻量控制栏
- 底部控制栏显示后，10 秒无操作会自动隐藏
- 音频只在 MediaPlayer 确认播放完成后切换下一首，不使用绘本自动间隔或时长兜底定时器
- 普通播放时按 `Left` / `Right` 会显示底部控制栏和进度条；进度条获得焦点后，继续按 `Left` / `Right` 调整进度
- 控制栏显示后：`Up` 可聚焦进度条，`Down` 返回按钮区，`Left` / `Right` 在按钮间移动焦点，`OK/Enter` 执行当前按钮，`Back` 隐藏控制栏
- `Bookmark` / `*`：收藏或取消收藏当前音频

播放页普通状态下按 `Back` 会先提示，再按一次才退出当前播放页，避免误触。

主界面会自动显示全局“我的收藏”，各分类内容列表顶部也会显示本分类的“我的收藏”。收藏只保存媒体索引，不复制文件。进入分类收藏后会先列出该分类下的收藏明细；打开全局或分类收藏中的任意项后，会从该项开始按当前收藏列表顺序连续播放，不会跳回原目录播放列表。空目录会留在列表中显示数量，但不会进入黑屏播放页。

## 本地构建与测试

构建 Debug 包：

```powershell
$env:JAVA_HOME='D:\Android\openjdk\jdk-17.0.12'
.\gradlew.bat assembleDebug --no-daemon
```

构建已签名 Release 包（一行命令）：

```powershell
$env:JAVA_HOME='D:\Android\openjdk\jdk-17.0.12'; .\gradlew.bat assembleRelease --no-daemon
```

模拟器测试数据可放到 `/data/local/tmp/tvbooks`：

```powershell
D:\Android\platform-tools\adb.exe shell "rm -rf /data/local/tmp/tvbooks && mkdir -p /data/local/tmp/tvbooks/huiben"
D:\Android\platform-tools\adb.exe push doc\test\huiben\chitang /data/local/tmp/tvbooks/huiben/chitang
D:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\Android\platform-tools\adb.exe shell am start -n com.example.picturebook/.view.MainActivity
```

开发规范见 [doc/TVBook_SPEC.md](doc/TVBook_SPEC.md)。
