package com.example.picturebook;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * usb工具类
 *
 * @author zony
 * @time 8 Apr 2016 11:59:47
 */
public class UsbUtil {
    private static final String TAG = "UsbUtil";

    /**
     * 获取usb list
     *
     * @return
     * @author zony
     * @time 8 Apr 2016 11:59:07
     */
    public static List<String> getStorageList() {
        List<String> storageList = new ArrayList<String>();
        ArrayList<String> exStorageMountPath = getExternalStorageMountPath();
        File sdcardFile = Environment.getExternalStorageDirectory();
        String sdcardPath = "";
        if (sdcardFile != null && sdcardFile.exists()) {
            sdcardPath = sdcardFile.getAbsolutePath();
        }

        if (!exStorageMountPath.contains(sdcardPath)) {
            exStorageMountPath.add(sdcardPath);
        }

        for (int i = exStorageMountPath.size() - 1; i >= 0; i--) {
            // 符号连接不扫描
            if (isSymlink(new File(exStorageMountPath.get(i)))) {
                continue;
            }
            storageList.add(exStorageMountPath.get(i));
        }

        return storageList;
    }

    /**
     * 是否为符号链接
     *
     * @param file
     * @return
     * @author zony
     * @time 8 Apr 2016 11:58:42
     */
    private static boolean isSymlink(File file) {
        if (file == null) {
            return false;
        }
        File canon = null;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = null;
            try {
                canonDir = file.getParentFile().getCanonicalFile();
                canon = new File(canonDir, file.getName());
                return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * 获取外部存储挂载路径
     *
     * @return
     * @author zony
     * @time 8 Apr 2016 11:58:32
     */
    private static ArrayList<String> getExternalStorageMountPath() {
        ArrayList<String> exStorageMountPath = new ArrayList<String>();
        try {
            File mountFile = new File("/proc/mounts");
            if (mountFile.exists()) {
                Scanner scanner = new Scanner(mountFile);
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (line.contains("secure"))
                        continue;
                    if (line.contains("asec"))
                        continue;
                    if (line.startsWith("/dev/block/vold/")) {
                        String[] columns = line.split(" ");
                        if (columns != null && columns.length > 1) {
                            String element = columns[1];
                            Log.i(TAG, "/dev/block/vold/ element:" + element);
                            exStorageMountPath.add(element);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return exStorageMountPath;
    }

    /**
     * 获取sdcard paths
     *
     * @param context
     * @return
     * @author zony
     * @time 8 Apr 2016 11:47:01
     */
    public static String[] getVolumePaths(Context context) {
        String[] paths = null;
        try {
            StorageManager mStorageManager = (StorageManager) context.getSystemService(Activity.STORAGE_SERVICE);
            Method mMethodGetPaths = mStorageManager.getClass().getMethod("getVolumePaths");
            paths = (String[]) mMethodGetPaths.invoke(mStorageManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return paths;
    }
}
