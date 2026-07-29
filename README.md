# TVBook

TVBook 是一个面向 Android 电视设备的离线绘本/音频播放器。把内容和配置放到 U 盘根目录的 `tvbooks` 文件夹，插入电视后即可通过遥控器播放。

## 兼容版本

- 最低支持：Android 4.2 / API 17
- 当前工程：`minSdkVersion 17`，`targetSdkVersion 28`
- 当前版本：`versionName 1.10`，`versionCode 11`
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

主菜单不再依赖 `menu.txt`，也不再依赖同步后的目录树。应用会直接读取 `tvbooks` 下真实存在的目录作为导航入口。

左侧显示 `tvbooks` 的一级目录；右侧显示当前目录的直接子目录。按 `OK/Enter` 打开目录时，应用在后台读取该目录的直接下级：有子目录就继续进入，没有子目录就按当前目录启动绘本或音频播放器，因此支持任意层级嵌套。

“同步目录”只用于后台递归缓存每个目录的数量信息，包括直接子目录数量、直接音频数量和直接绘本数量。未同步时仍可正常导航和播放，只是不显示缓存数量。

## 遥控器操作

主界面保持 1.5 的固定三栏布局：左侧一级目录菜单，中间显示当前目录的直接下级，右侧显示状态和设置。目录可以逐级进入任意深度；按 `Left` 或 `Back` 返回上一级目录，回到根层后再返回左侧菜单。

主界面右侧的“同步目录”按钮会后台缓存目录数量，也可以在主界面按 `Menu` 触发同步。导航始终以真实文件目录为准，目录新增、修改、删除后会在重新进入相应目录时体现。“自动间隔(秒)”用于控制自动播放等待时间。

主界面使用方向键移动菜单和控件焦点，只有 `OK/Enter` 才会打开目录或开始播放；`Right` 不会直接启动播放。

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

- 中央音乐图标下方常驻显示当前音频文件名
- `Left` / `Right`：快退 / 快进
- `Up` / `Down`：上一首 / 下一首
- `OK`：暂停或继续
- `Menu`：显示或隐藏底部轻量控制栏
- 底部控制栏显示后，10 秒无操作会自动隐藏
- 音频以 MediaPlayer 完成事件为主，并使用当前曲目时长进行完成兜底；异步准备期间不会读取进度或误判完成
- 播放列表只包含支持的音频文件，非音频文件不会占用曲目序号或被当作曲目跳过
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
