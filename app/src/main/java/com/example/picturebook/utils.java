package com.example.picturebook;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.picturebook.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class utils {
    public static String readToString(String fileName) {
        String encoding = "UTF-8";
        File file = new File(fileName);
        Long filelength = file.length();
        byte[] filecontent = new byte[filelength.intValue()];
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            int offset = 0;
            while (offset < filecontent.length) {
                int read = in.read(filecontent, offset, filecontent.length - offset);
                if (read < 0) break;
                offset += read;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            return new String(filecontent, encoding);
        } catch (UnsupportedEncodingException e) {
            System.err.println("The OS does not support " + encoding);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 加载本地图片
     * http://bbs.3gstdy.com
     *
     * @param url
     * @return
     */
    public static Bitmap getLoacalBitmap(String url) {
        try {
            FileInputStream fis = new FileInputStream(url);
            return BitmapFactory.decodeStream(fis);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 遍历所有文件列表，不递归，
     * @return
     */

    /**
     * 遍历所有文件列表，不递归，只获取文件或只获取文件夹
     *
     * @param isNeedFolder
     * @return
     */
    public static List<String> getAllFilesOfDir(File dirFile, boolean isNeedFolder) {
        List<String> files = new ArrayList<>();
        File indexFile = new File(dirFile.getAbsoluteFile(), Settings.BookIndexFileName);
        boolean useIndexFile = indexFile.exists();
        if (!useIndexFile) {
            File[] subFile = dirFile.listFiles();
            if (subFile != null) {
                for (int i = 0; i < subFile.length; i++) {
                    if (isNeedFolder) {
                        if (subFile[i].isDirectory()) {
                            files.add(subFile[i].getName());
                        }
                    } else {
                        // 判断是否为文件夹
                        if (!subFile[i].isDirectory()) {
                            files.add(subFile[i].getName());
                        }
                    }
                }
            }
        } else {
            // 按照索引逐行读取
            String text = utils.readToString(indexFile.getAbsolutePath());
            if (text != null && text.trim().length() > 0) {
                files = new ArrayList<>(Arrays.asList(text.trim().split("\\r?\\n")));
            }
        }

        if (!useIndexFile) {
            Collections.sort(files, new Comparator<String>() {
                @Override
                public int compare(String left, String right) {
                    return compareNatural(left, right);
                }
            });
        }
        return files;
    }

    private static int compareNatural(String left, String right) {
        int leftLength = left == null ? 0 : left.length();
        int rightLength = right == null ? 0 : right.length();
        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < leftLength && rightIndex < rightLength) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                long leftNumber = 0;
                while (leftIndex < leftLength && Character.isDigit(left.charAt(leftIndex))) {
                    leftNumber = leftNumber * 10 + left.charAt(leftIndex) - '0';
                    leftIndex++;
                }
                long rightNumber = 0;
                while (rightIndex < rightLength && Character.isDigit(right.charAt(rightIndex))) {
                    rightNumber = rightNumber * 10 + right.charAt(rightIndex) - '0';
                    rightIndex++;
                }
                if (leftNumber != rightNumber) {
                    return leftNumber < rightNumber ? -1 : 1;
                }
                continue;
            }

            int diff = Character.toLowerCase(leftChar) - Character.toLowerCase(rightChar);
            if (diff != 0) {
                return diff;
            }
            leftIndex++;
            rightIndex++;
        }

        return leftLength - rightLength;
    }
}
