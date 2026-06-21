package com.example.picturebook;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.List;

public class Settings {
    // 放在U盘根目录的目录名
    public static final String RootName = "tvbooks";
    public static final String ConfigDirName = "config";
    // 每个分类目录下放置一个目录索引，内容为：每行是一本书的名称，utf-8格式编码，以支持中文
    public static String BookIndexFileName = "index.txt";
    // 如果自动播放图片，默认多少秒切换
    public static int delaySecondsPerPage = 10;
    // 音频播放完成后延迟的秒数，然后再自动播放下一个
    public static int delaySecondsPerAudio = 4;

    private static final String DevUsbPath = "/data/local/tmp";
    private static String USBPath = null;
    private static String RootPath = null;

    private static String cacheUSBPath(String path) {
        USBPath = path;
        RootPath = path == null ? null : new File(path, RootName).getAbsolutePath();
        return USBPath;
    }

    // 获取U盘路径
    public static String getUSBPath(Context context) {
        synchronized (Settings.class) { // 线程安全
            if (USBPath != null && new File(USBPath, RootName).exists()) {
                return USBPath;
            }

            cacheUSBPath(null);

            // 模拟器和 adb 测试目录。正式设备不存在该目录时不会命中。
            if (new File(DevUsbPath, RootName).exists()) {
                return cacheUSBPath(DevUsbPath);
            }

            List<String> usbPaths = UsbUtil.getStorageList();
            for (String path : usbPaths) {
                if (new File(path, RootName).exists()) {
                    return cacheUSBPath(path);
                }
            }

            if (context != null) {
                String[] sdcardPaths = UsbUtil.getVolumePaths(context);
                if (sdcardPaths != null) {
                    for (String path : sdcardPaths) {
                        if (path.startsWith("/storage/emulated")) continue; // 跳过内部存储
                        if (new File(path, RootName).exists()) {
                            return cacheUSBPath(path);
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
                    File[] externalDirs = context.getExternalFilesDirs(null);
                    if (externalDirs != null) {
                        for (File dir : externalDirs) {
                            if (dir == null) continue;
                            String path = dir.getAbsolutePath().replaceAll("/Android/data/.*", "");
                            if (new File(path, RootName).exists()) {
                                return cacheUSBPath(path);
                            }
                        }
                    }
                }
            }

            File externalDir = Environment.getExternalStorageDirectory();
            if (externalDir != null && new File(externalDir, RootName).exists()) {
                return cacheUSBPath(externalDir.getAbsolutePath());
            }

            String[] commonPaths = new String[]{
                    "/mnt/usb_storage",
                    "/mnt/usbhost1",
                    "/storage/usb0",
                    "/storage/usb1",
                    "/mnt/media_rw/usb0",
                    "/mnt/media_rw/usb1"
            };
            for (String path : commonPaths) {
                File dir = new File(path);
                if (!dir.exists()) continue;
                if (new File(dir, RootName).exists()) {
                    return cacheUSBPath(path);
                }
                File[] children = dir.listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (child.isDirectory() && new File(child, RootName).exists()) {
                        return cacheUSBPath(child.getAbsolutePath());
                    }
                }
            }

            return USBPath;
        }
    }

    public static String getRootPath(Context context) {
        if (RootPath == null) {
            String usbPath = getUSBPath(context);
            if (usbPath == null) {
                return null;
            }
            RootPath = new File(usbPath, RootName).getAbsolutePath();
        }
        return RootPath;
    }

    public static File getRootDir(Context context) {
        String rootPath = getRootPath(context);
        return rootPath == null ? null : new File(rootPath);
    }

    public static File getConfigDir(Context context) {
        File rootDir = getRootDir(context);
        return rootDir == null ? null : new File(rootDir, ConfigDirName);
    }

    public static int getDelaySecondsPerPage() {
        return Settings.delaySecondsPerPage;
    }

    public static void setDelaySecondsPerPage(int seconds) {
        Settings.delaySecondsPerPage = seconds;
    }

    public static int getDelaySecondsPerAudio() {
        return Settings.delaySecondsPerAudio;
    }

    public static void setDelaySecondsPerAudio(int seconds) {
        Settings.delaySecondsPerAudio = seconds;
    }
}
