package com.example.picturebook;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class TvBookStore {
    public static final String TYPE_AUDIO_IMAGE = "audio_image";
    public static final String TYPE_AUDIO = "audio";

    private static final String SETTINGS_FILE = "settings.properties";
    private static final String PLAYBACK_FILE = "playback.properties";
    private static final String FAVORITES_FILE = "favorites.properties";

    public static class PlaybackState {
        public final boolean exists;
        public final int resIndex;
        public final int pageIndex;
        public final int mediaPos;

        public PlaybackState(boolean exists, int resIndex, int pageIndex, int mediaPos) {
            this.exists = exists;
            this.resIndex = resIndex;
            this.pageIndex = pageIndex;
            this.mediaPos = mediaPos;
        }
    }

    public static class FavoriteItem {
        public final String type;
        public final String path;
        public final String title;

        public FavoriteItem(String type, String path, String title) {
            this.type = type;
            this.path = path;
            this.title = title;
        }
    }

    public static boolean getBooleanSetting(Context context, String key, boolean defaultValue) {
        String value = loadProperties(context, SETTINGS_FILE).getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static int getIntSetting(Context context, String key, int defaultValue) {
        String value = loadProperties(context, SETTINGS_FILE).getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean putSetting(Context context, String key, String value) {
        Properties props = loadProperties(context, SETTINGS_FILE);
        props.setProperty(key, value);
        return storeProperties(context, SETTINGS_FILE, props, "TVBook settings");
    }

    public static PlaybackState readPlaybackState(Context context, String contentKey) {
        String value = loadProperties(context, PLAYBACK_FILE).getProperty(buildKey("state", contentKey));
        if (value == null) {
            return new PlaybackState(false, 0, 1, 0);
        }

        String[] parts = value.split(",", -1);
        return new PlaybackState(true,
                parseIntPart(parts, 0, 0),
                parseIntPart(parts, 1, 1),
                parseIntPart(parts, 2, 0));
    }

    public static boolean savePlaybackState(Context context, String contentKey, int resIndex, int pageIndex, int mediaPos) {
        Properties props = loadProperties(context, PLAYBACK_FILE);
        props.setProperty(buildKey("state", contentKey), resIndex + "," + pageIndex + "," + Math.max(0, mediaPos));
        return storeProperties(context, PLAYBACK_FILE, props, "TVBook playback");
    }

    public static boolean isFavorite(Context context, String type, String relativePath) {
        Properties props = loadProperties(context, FAVORITES_FILE);
        return props.containsKey(buildKey("fav", type + "\n" + normalizePath(relativePath)));
    }

    public static boolean setFavorite(Context context, String type, String relativePath, String title, boolean favorite) {
        Properties props = loadProperties(context, FAVORITES_FILE);
        String path = normalizePath(relativePath);
        String key = buildKey("fav", type + "\n" + path);
        if (favorite) {
            props.setProperty(key, safe(type) + "\t" + safe(path) + "\t" + safe(title));
        } else {
            props.remove(key);
        }
        return storeProperties(context, FAVORITES_FILE, props, "TVBook favorites");
    }

    public static List<FavoriteItem> getFavorites(Context context) {
        Properties props = loadProperties(context, FAVORITES_FILE);
        List<FavoriteItem> items = new ArrayList<FavoriteItem>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("fav.")) continue;
            String[] parts = props.getProperty(key, "").split("\t", -1);
            if (parts.length < 3) continue;
            items.add(new FavoriteItem(parts[0], normalizePath(parts[1]), parts[2]));
        }
        Collections.sort(items, new Comparator<FavoriteItem>() {
            @Override
            public int compare(FavoriteItem left, FavoriteItem right) {
                return left.title.compareToIgnoreCase(right.title);
            }
        });
        return items;
    }

    public static String normalizePath(String path) {
        if (path == null) return "";
        path = path.replace("\\", "/").trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public static String parentPath(String path) {
        path = normalizePath(path);
        int index = path.lastIndexOf('/');
        return index <= 0 ? "" : path.substring(0, index);
    }

    public static String fileName(String path) {
        path = normalizePath(path);
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private static int parseIntPart(String[] parts, int index, int defaultValue) {
        if (parts == null || parts.length <= index) return defaultValue;
        try {
            return Integer.parseInt(parts[index]);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private static synchronized Properties loadProperties(Context context, String fileName) {
        Properties props = new Properties();
        File file = getConfigFile(context, fileName, false);
        if (file == null || !file.exists()) {
            return props;
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            props.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return props;
    }

    private static synchronized boolean storeProperties(Context context, String fileName, Properties props, String comment) {
        File file = getConfigFile(context, fileName, true);
        if (file == null) {
            return false;
        }
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            props.store(outputStream, comment);
            file.setReadable(true, false);
            file.setWritable(true, false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static File getConfigFile(Context context, String fileName, boolean createDir) {
        File configDir = Settings.getConfigDir(context);
        if (configDir == null) {
            return null;
        }
        if (createDir && !configDir.exists() && !configDir.mkdirs()) {
            return null;
        }
        if (createDir) {
            configDir.setReadable(true, false);
            configDir.setWritable(true, false);
            configDir.setExecutable(true, false);
        }
        return new File(configDir, fileName);
    }

    private static String buildKey(String prefix, String raw) {
        return prefix + "." + toHex(raw == null ? "" : raw);
    }

    private static String toHex(String value) {
        try {
            byte[] bytes = value.getBytes("UTF-8");
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int v = b & 0xff;
                if (v < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(v));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
