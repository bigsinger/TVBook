# TVBook

TVBook 是一个面向 Android 电视设备的离线绘本/音频播放器。把内容和配置放到 U 盘根目录的 `tvbooks` 文件夹，插入电视后即可通过遥控器播放。

## 兼容版本

- 最低支持：Android 4.2 / API 17
- 当前工程：`minSdkVersion 17`，`targetSdkVersion 28`
- 本地 SDK：`D:\Android\Sdk`

## U 盘目录

```text
tvbooks/
  menu.txt
  config/
    settings.properties
    playback.properties
    favorites.properties
  huiben/
    chitang/
      1.jpg
      1.mp3
      2.jpg
      2.mp3
  gushi/
    001.mp3
```

`config` 目录由应用自动创建，用于保存播放设置、断点续播和收藏记录。拔下 U 盘后，这些记录仍保留在 U 盘的 `tvbooks/config` 下。

## 菜单配置

`tvbooks/menu.txt` 使用 UTF-8 JSON：

```json
{
  "menu": [
    { "name": "huiben", "type": "audio_image" },
    {
      "name": "gushi",
      "type": "audio",
      "submenu": [
        { "name": "100shou" }
      ]
    }
  ]
}
```

- `audio_image`：绘本目录，页面文件按 `1.jpg`、`1.mp3` 这种同名序号配对。
- `audio`：音频目录，自动扫描目录下的音频文件。
- 分类目录可放 `index.txt` 固定绘本顺序，一行一个子目录名。

## 遥控器操作

主界面采用三栏电视布局：左侧分类，中间内容，右侧状态。方向键移动焦点，`Right` 从分类进入内容列表，`Left` 返回分类，`OK/Enter` 打开当前内容。

绘本播放：

- `Left` / `Right`：上一页 / 下一页
- `Up` / `Down`：上一本 / 下一本
- `OK`：暂停或继续
- `Menu`：显示或隐藏底部轻量控制栏
- `Bookmark` / `*`：收藏或取消收藏当前绘本

音频播放：

- `Left` / `Up`：上一首
- `Right` / `Down`：下一首
- `OK`：暂停或继续
- `Menu`：显示或隐藏底部轻量控制栏
- `Bookmark` / `*`：收藏或取消收藏当前音频

主界面会自动显示“我的收藏”，可直接打开已收藏的绘本或音频。空目录会留在列表中显示数量，但不会进入黑屏播放页。

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
D:\Android\platform-tools\adb.exe push doc\menu.txt /data/local/tmp/tvbooks/menu.txt
D:\Android\platform-tools\adb.exe push doc\test\huiben\chitang /data/local/tmp/tvbooks/huiben/chitang
D:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\Android\platform-tools\adb.exe shell am start -n com.example.picturebook/.view.MainActivity
```

开发规范见 [doc/TVBook_SPEC.md](doc/TVBook_SPEC.md)。
