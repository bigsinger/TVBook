package com.example.picturebook.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picturebook.R;
import com.example.picturebook.Settings;
import com.example.picturebook.TvBookStore;
import com.example.picturebook.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener, View.OnFocusChangeListener {
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String TAG = "MainActivity";
    private static final String PREF_USB_URI = "usb_uri";
    private static final String AUTO_PLAY_KEY = "isAutoPlay";
    private static final String DELAY_AUDIO_KEY = "delaySecondsPerAudio";

    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 200;

    protected SharedPreferences mSP;
    protected boolean isAutoPlayMode = true;
    private LinearLayout viewMenu;
    private LinearLayout viewSubMenu;
    private TextView txtSectionTitle;
    private TextView txtSectionCount;
    private TextView txtSystemVersion;
    private TextView txtUsbPath;
    private TextView txtConfigPath;
    private TextView txtResumeInfo;
    private TextView txtFavCount;
    private TextView txtDelaySecondsPerAudio;
    private Button btnPlayMode;
    private Button btnSyncCatalog;
    private long exitTime = 0;
    private boolean isCatalogSyncing = false;

    private final List<MenuEntry> menuEntries = new ArrayList<MenuEntry>();
    private MenuEntry currentMenuEntry;
    private boolean showingFavoriteList = false;
    private boolean suppressMenuFocusRender = false;

    private static class MenuEntry {
        final String title;
        final String type;
        final String path;
        final boolean favoriteRoot;
        final boolean h5;
        final List<String> subMenus = new ArrayList<String>();

        MenuEntry(String title, String type, String path, boolean favoriteRoot, boolean h5) {
            this.title = title;
            this.type = type;
            this.path = path;
            this.favoriteRoot = favoriteRoot;
            this.h5 = h5;
        }
    }

    private static class ContentEntry {
        final String title;
        final String subtitle;
        final String type;
        final String path;
        final String targetName;
        final boolean h5;
        final TvBookStore.FavoriteItem favoriteItem;
        final boolean favoriteGroup;
        final String favoriteScopeType;
        final String favoriteScopePath;

        ContentEntry(String title, String subtitle, String type, String path, String targetName, boolean h5,
                     TvBookStore.FavoriteItem favoriteItem, boolean favoriteGroup, String favoriteScopeType,
                     String favoriteScopePath) {
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.path = path;
            this.targetName = targetName;
            this.h5 = h5;
            this.favoriteItem = favoriteItem;
            this.favoriteGroup = favoriteGroup;
            this.favoriteScopeType = favoriteScopeType;
            this.favoriteScopePath = favoriteScopePath;
        }
    }

    public static void verifyStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mSP = getSharedPreferences("cache", Context.MODE_PRIVATE);

        btnPlayMode = findViewById(R.id.btnPlayMode);
        btnSyncCatalog = findViewById(R.id.btnSyncCatalog);
        txtDelaySecondsPerAudio = findViewById(R.id.txtDelaySecondsPerAudio);
        viewMenu = findViewById(R.id.viewMenu);
        viewSubMenu = findViewById(R.id.viewSubMenu);
        txtSectionTitle = findViewById(R.id.txtSectionTitle);
        txtSectionCount = findViewById(R.id.txtSectionCount);
        txtSystemVersion = findViewById(R.id.txtSystemVersion);
        txtUsbPath = findViewById(R.id.txtUsbPath);
        txtConfigPath = findViewById(R.id.txtConfigPath);
        txtResumeInfo = findViewById(R.id.txtResumeInfo);
        txtFavCount = findViewById(R.id.txtFavCount);

        styleStaticButtons();
        verifyStoragePermissions(this);
        restoreUsbPermission();

        isAutoPlayMode = TvBookStore.getBooleanSetting(this, AUTO_PLAY_KEY, mSP.getBoolean(AUTO_PLAY_KEY, true));
        Settings.delaySecondsPerAudio = TvBookStore.getIntSetting(this, DELAY_AUDIO_KEY, mSP.getInt(DELAY_AUDIO_KEY, 4));
        Settings.delaySecondsPerPage = Settings.delaySecondsPerAudio;
        refreshSettingsText();

        initMenu();
        refreshStatusText(currentMenuEntry);
    }

    private void initMenu() {
        menuEntries.clear();
        viewMenu.removeAllViews();
        viewSubMenu.removeAllViews();

        menuEntries.add(new MenuEntry("我的收藏", "favorite_root", "", true, false));

        String rootPath = Settings.getRootPath(this);
        if (rootPath == null) {
            Toast.makeText(MainActivity.this, "请插入包含 tvbooks 的U盘", Toast.LENGTH_LONG).show();
            menuEntries.add(new MenuEntry("H5 在线绘本", "h5", "", false, true));
            renderMenu();
            return;
        }

        try {
            File menuFile = new File(rootPath, Settings.MenuFileName);
            if (!menuFile.exists()) {
                Toast.makeText(MainActivity.this, "请配置菜单文件:" + menuFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                menuEntries.add(new MenuEntry("H5 在线绘本", "h5", "", false, true));
                renderMenu();
                return;
            }

            String menuJsonStr = utils.readToString(menuFile.getAbsolutePath());
            JSONObject jsonObject = new JSONObject(menuJsonStr);
            JSONArray menus = jsonObject.getJSONArray("menu");
            for (int i = 0; i < menus.length(); i++) {
                JSONObject menu = menus.getJSONObject(i);
                String name = menu.getString("name");
                String type = menu.getString("type");
                MenuEntry entry = new MenuEntry(name, type, name, false, false);

                JSONArray submenus = null;
                try {
                    submenus = menu.getJSONArray("submenu");
                } catch (Exception e) {
                    submenus = null;
                }
                if (submenus != null) {
                    for (int j = 0; j < submenus.length(); j++) {
                        JSONObject submenu = submenus.getJSONObject(j);
                        entry.subMenus.add(submenu.getString("name"));
                    }
                }
                menuEntries.add(entry);
            }
            menuEntries.add(new MenuEntry("H5 在线绘本", "h5", "", false, true));
            renderMenu();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, e.toString());
            menuEntries.add(new MenuEntry("H5 在线绘本", "h5", "", false, true));
            renderMenu();
        }
    }

    private void renderMenu() {
        viewMenu.removeAllViews();
        for (int i = 0; i < menuEntries.size(); i++) {
            MenuEntry entry = menuEntries.get(i);
            Button button = createMenuButton(entry.title);
            button.setTag(entry);
            button.setOnClickListener(this);
            button.setOnFocusChangeListener(this);
            viewMenu.addView(button);
            if (i == 0) {
                currentMenuEntry = entry;
                button.setSelected(true);
                button.setTextColor(Color.rgb(240, 192, 64));
                renderContent(entry);
                final Button firstButton = button;
                firstButton.post(new Runnable() {
                    @Override
                    public void run() {
                        firstButton.requestFocusFromTouch();
                    }
                });
            }
        }
    }

    private void renderContent(MenuEntry entry) {
        currentMenuEntry = entry;
        showingFavoriteList = false;
        clearSubMenuViews();

        List<ContentEntry> contentEntries = buildContentEntries(entry);
        txtSectionTitle.setText(entry == null ? "内容" : entry.title);
        txtSectionCount.setText(contentEntries.size() + " 项");
        refreshStatusText(entry);

        if (contentEntries.isEmpty()) {
            addEmptyContent(entry == null || !entry.favoriteRoot ? "暂无内容" : "暂无收藏");
            return;
        }

        for (ContentEntry contentEntry : contentEntries) {
            TextView view = createContentView(contentEntry);
            viewSubMenu.addView(view);
        }
    }

    private List<ContentEntry> buildContentEntries(MenuEntry entry) {
        List<ContentEntry> entries = new ArrayList<ContentEntry>();
        if (entry == null) return entries;

        if (entry.favoriteRoot) {
            List<TvBookStore.FavoriteItem> favorites = TvBookStore.getFavorites(this);
            for (TvBookStore.FavoriteItem item : favorites) {
                String label = TvBookStore.TYPE_AUDIO.equals(item.type) ? "音频" : "绘本";
                entries.add(new ContentEntry(item.title, label + " · " + item.path, item.type,
                        TvBookStore.parentPath(item.path), TvBookStore.fileName(item.path), false, item,
                        false, item.type, ""));
            }
            return entries;
        }

        if (entry.h5) {
            entries.add(new ContentEntry("打开 H5 在线绘本", "使用内置 WebView 播放在线绘本",
                    "h5", "", null, true, null, false, null, null));
            return entries;
        }

        addFavoriteGroup(entries, entry.type, entry.path);

        if (!entry.subMenus.isEmpty()) {
            for (String subMenu : entry.subMenus) {
                String path = entry.path + "/" + subMenu;
                String subtitle = buildFolderSubtitle(entry.type, path);
                entries.add(new ContentEntry(subMenu, subtitle, entry.type, path, null, false, null,
                        false, null, null));
            }
            return entries;
        }

        TvBookStore.CatalogSnapshot snapshot = TvBookStore.readCatalog(this, entry.type, entry.path);
        String subtitle = snapshot.exists
                ? buildFolderSubtitle(entry.type, entry.path)
                : "目录未同步，可按 Menu 或右侧同步按钮";
        entries.add(new ContentEntry("播放全部", subtitle, entry.type, entry.path, null, false, null,
                false, null, null));
        return entries;
    }

    private void addFavoriteGroup(List<ContentEntry> entries, String type, String scopePath) {
        List<TvBookStore.FavoriteItem> favorites = filterFavorites(type, scopePath);
        String label = TvBookStore.TYPE_AUDIO.equals(type) ? "音频收藏" : "绘本收藏";
        entries.add(new ContentEntry("我的收藏", label + " · " + favorites.size() + " 项",
                type, scopePath, null, false, null, true, type, scopePath));
    }

    private String buildFolderSubtitle(String type, String path) {
        TvBookStore.CatalogSnapshot snapshot = TvBookStore.readCatalog(this, type, path);
        if (TvBookStore.TYPE_AUDIO_IMAGE.equals(type)) {
            return snapshot.exists ? "绘本分类 · " + snapshot.count + " 本" : "绘本分类";
        }
        if (TvBookStore.TYPE_AUDIO.equals(type)) {
            return snapshot.exists ? "音频合集 · " + snapshot.count + " 首" : "音频合集";
        }
        return "内容 · " + path;
    }

    private int countCatalogItems(String type, String path) {
        File rootDir = Settings.getRootDir(this);
        if (rootDir == null) return 0;
        File dir = new File(rootDir, TvBookStore.normalizePath(path));
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (TvBookStore.TYPE_AUDIO_IMAGE.equals(type)) {
                if (file.isDirectory()) count++;
            } else if (TvBookStore.TYPE_AUDIO.equals(type)) {
                if (file.isFile()) count++;
            } else if (file.isFile() || file.isDirectory()) {
                count++;
            }
        }
        return count;
    }

    private void addEmptyContent(String text) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextColor(Color.rgb(136, 136, 136));
        empty.setTextSize(22);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(48), dp(12), dp(48));
        viewSubMenu.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnPlayMode) {
            isAutoPlayMode = !isAutoPlayMode;
            refreshSettingsText();
            saveSetting(AUTO_PLAY_KEY, Boolean.toString(isAutoPlayMode));
            return;
        }

        Object tag = v.getTag();
        if (tag instanceof MenuEntry) {
            selectMenuButton(v);
            renderContent((MenuEntry) tag);
            focusFirstContent();
            return;
        }
        if (tag instanceof ContentEntry) {
            openContentEntry((ContentEntry) tag);
        }
    }

    private void openContentEntry(ContentEntry entry) {
        if (entry == null) return;
        if (entry.h5) {
            startActivity(new Intent(MainActivity.this, PlayH5Activity.class));
            return;
        }
        if (entry.favoriteGroup) {
            renderFavoriteList(entry.favoriteScopeType, entry.favoriteScopePath);
            focusFirstContent();
            return;
        }
        if (entry.favoriteItem != null) {
            startFavorite(entry);
            return;
        }
        TvBookStore.CatalogSnapshot snapshot = TvBookStore.readCatalog(this, entry.type, entry.path);
        if (entry.targetName == null && snapshot.exists && snapshot.count == 0) {
            Toast.makeText(this, "暂无可播放内容", Toast.LENGTH_SHORT).show();
            return;
        }
        startContent(entry.type, entry.path, entry.targetName);
    }

    public void onDecDelaySeconds(View v) {
        int value = Settings.delaySecondsPerAudio;
        int id = v.getId();
        if (id == R.id.btnDec) {
            if (value > 0) value--;
        } else if (id == R.id.btnInc) {
            if (value < 60) value++;
        }

        if (value != Settings.delaySecondsPerAudio) {
            Settings.delaySecondsPerAudio = value;
            Settings.delaySecondsPerPage = value;
            refreshSettingsText();
            saveSetting(DELAY_AUDIO_KEY, Integer.toString(value));
        }
    }

    public void onSyncCatalog(View v) {
        if (isCatalogSyncing) {
            Toast.makeText(this, "目录正在同步", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Settings.getRootDir(this) == null) {
            Toast.makeText(this, "请插入包含 tvbooks 的U盘", Toast.LENGTH_SHORT).show();
            return;
        }

        isCatalogSyncing = true;
        if (btnSyncCatalog != null) {
            btnSyncCatalog.setEnabled(false);
            btnSyncCatalog.setText("同步中");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int syncedCount = syncCatalogInBackground();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isCatalogSyncing = false;
                        if (btnSyncCatalog != null) {
                            btnSyncCatalog.setEnabled(true);
                            btnSyncCatalog.setText("同步");
                        }
                        renderContent(currentMenuEntry);
                        Toast.makeText(MainActivity.this, "目录同步完成: " + syncedCount + " 个目录", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private int syncCatalogInBackground() {
        int count = 0;
        for (MenuEntry entry : menuEntries) {
            if (entry == null || entry.favoriteRoot || entry.h5) continue;
            if (!entry.subMenus.isEmpty()) {
                for (String subMenu : entry.subMenus) {
                    count += syncOneCatalogFolder(entry.type, entry.path + "/" + subMenu);
                }
            } else {
                count += syncOneCatalogFolder(entry.type, entry.path);
            }
        }
        return count;
    }

    private int syncOneCatalogFolder(String type, String path) {
        TvBookStore.saveCatalogCount(this, type, path, countCatalogItems(type, path));
        return 1;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        Object tag = v.getTag();
        if (tag instanceof MenuEntry) {
            if (suppressMenuFocusRender) {
                return;
            }
            Button button = (Button) v;
            if (hasFocus) {
                selectMenuButton(v);
                renderContent((MenuEntry) tag);
            } else if (!button.isSelected()) {
                button.setTextColor(Color.rgb(170, 170, 170));
            }
        } else if (tag instanceof ContentEntry) {
            TextView textView = (TextView) v;
            textView.setTextColor(hasFocus ? Color.WHITE : Color.rgb(210, 210, 210));
        }
    }

    private void clearSubMenuViews() {
        suppressMenuFocusRender = true;
        try {
            viewSubMenu.removeAllViews();
        } finally {
            suppressMenuFocusRender = false;
        }
    }

    private void selectMenuButton(View selectedView) {
        for (int i = 0; i < viewMenu.getChildCount(); i++) {
            View child = viewMenu.getChildAt(i);
            boolean selected = child == selectedView;
            child.setSelected(selected);
            if (child instanceof Button) {
                ((Button) child).setTextColor(selected ? Color.rgb(240, 192, 64) : Color.rgb(170, 170, 170));
            }
        }
    }

    private boolean focusFirstContent() {
        if (viewSubMenu == null) return false;
        for (int i = 0; i < viewSubMenu.getChildCount(); i++) {
            View child = viewSubMenu.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                return true;
            }
        }
        return false;
    }

    private boolean focusSelectedMenu() {
        if (viewMenu == null) return false;
        for (int i = 0; i < viewMenu.getChildCount(); i++) {
            View child = viewMenu.getChildAt(i);
            if (child.isSelected()) {
                child.requestFocus();
                return true;
            }
        }
        if (viewMenu.getChildCount() > 0) {
            viewMenu.getChildAt(0).requestFocus();
            return true;
        }
        return false;
    }

    private void renderFavoriteList(String type, String scopePath) {
        showingFavoriteList = true;
        clearSubMenuViews();

        String safeScope = TvBookStore.normalizePath(scopePath);
        List<TvBookStore.FavoriteItem> favorites = filterFavorites(type, safeScope);
        txtSectionTitle.setText(safeScope.length() == 0
                ? "我的收藏"
                : TvBookStore.fileName(safeScope) + " · 我的收藏");
        txtSectionCount.setText(favorites.size() + " 项");
        refreshStatusText(currentMenuEntry);

        if (favorites.isEmpty()) {
            addEmptyContent("暂无收藏");
            return;
        }

        for (TvBookStore.FavoriteItem item : favorites) {
            String label = TvBookStore.TYPE_AUDIO.equals(item.type) ? "音频" : "绘本";
            ContentEntry contentEntry = new ContentEntry(item.title, label + " · " + item.path,
                    item.type, TvBookStore.parentPath(item.path), TvBookStore.fileName(item.path),
                    false, item, false, type, safeScope);
            viewSubMenu.addView(createContentView(contentEntry));
        }
    }

    private void startFavorite(ContentEntry entry) {
        TvBookStore.FavoriteItem item = entry.favoriteItem;
        String type = entry.favoriteScopeType == null || entry.favoriteScopeType.length() == 0
                ? item.type
                : entry.favoriteScopeType;
        String scopePath = entry.favoriteScopePath == null ? "" : entry.favoriteScopePath;
        startFavoritePlaylist(type, scopePath, item.path);
    }

    private void startFavoritePlaylist(String type, String scopePath, String targetPath) {
        List<TvBookStore.FavoriteItem> favorites = filterFavorites(type, scopePath);
        if (favorites.size() == 0) {
            Toast.makeText(this, "暂无收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] paths = new String[favorites.size()];
        for (int i = 0; i < favorites.size(); i++) {
            paths[i] = favorites.get(i).path;
        }

        String safeScope = TvBookStore.normalizePath(scopePath);
        String playlistKey = safeScope.length() == 0
                ? "favorites:" + type + ":global"
                : "favorites:" + type + ":" + safeScope;
        Intent intent;
        if (TvBookStore.TYPE_AUDIO_IMAGE.equals(type)) {
            intent = new Intent(MainActivity.this, PlayHuibenActivity.class).setType(safeScope);
            intent.putExtra(PlayHuibenActivity.EXTRA_PLAYLIST_PATHS, paths);
            intent.putExtra(PlayHuibenActivity.EXTRA_PLAYLIST_KEY, playlistKey);
            if (targetPath != null) {
                intent.putExtra(PlayHuibenActivity.EXTRA_BOOK_NAME, TvBookStore.normalizePath(targetPath));
            }
            startActivity(intent);
        } else if (TvBookStore.TYPE_AUDIO.equals(type)) {
            intent = new Intent(MainActivity.this, PlayAudioActivity.class).setType(safeScope);
            intent.putExtra(PlayAudioActivity.EXTRA_PLAYLIST_PATHS, paths);
            intent.putExtra(PlayAudioActivity.EXTRA_PLAYLIST_KEY, playlistKey);
            if (targetPath != null) {
                intent.putExtra(PlayAudioActivity.EXTRA_AUDIO_NAME, TvBookStore.normalizePath(targetPath));
            }
            startActivity(intent);
        }
    }

    private List<TvBookStore.FavoriteItem> filterFavorites(String type, String scopePath) {
        List<TvBookStore.FavoriteItem> filtered = new ArrayList<TvBookStore.FavoriteItem>();
        String safeScope = TvBookStore.normalizePath(scopePath);
        List<TvBookStore.FavoriteItem> favorites = TvBookStore.getFavorites(this);
        for (TvBookStore.FavoriteItem item : favorites) {
            if (!type.equals(item.type)) continue;
            if (safeScope.length() == 0
                    || item.path.equals(safeScope)
                    || item.path.startsWith(safeScope + "/")) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void startContent(String type, String path, String targetName) {
        Intent intent;
        if (TvBookStore.TYPE_AUDIO_IMAGE.equals(type)) {
            intent = new Intent(MainActivity.this, PlayHuibenActivity.class).setType(path);
            if (targetName != null) {
                intent.putExtra(PlayHuibenActivity.EXTRA_BOOK_NAME, targetName);
            }
            startActivity(intent);
        } else if (TvBookStore.TYPE_AUDIO.equals(type)) {
            intent = new Intent(MainActivity.this, PlayAudioActivity.class).setType(path);
            if (targetName != null) {
                intent.putExtra(PlayAudioActivity.EXTRA_AUDIO_NAME, targetName);
            }
            startActivity(intent);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            View focus = getCurrentFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (focus == null || focus.getTag() instanceof MenuEntry) {
                    return focusFirstContent();
                }
                if (focus.getTag() instanceof ContentEntry && btnSyncCatalog != null) {
                    btnSyncCatalog.requestFocus();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (focus != null && focus.getTag() instanceof ContentEntry) {
                    return focusSelectedMenu();
                }
                if (focus == btnSyncCatalog) {
                    return focusFirstContent();
                }
            } else if (keyCode == KeyEvent.KEYCODE_MENU) {
                onSyncCatalog(btnSyncCatalog);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (showingFavoriteList) {
                renderContent(currentMenuEntry);
                focusFirstContent();
                return true;
            }
            if ((System.currentTimeMillis() - exitTime) > 1000) {
                Toast.makeText(getApplicationContext(), "再按一次退出程序", Toast.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_HOME) {
            exitApp(null);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            View focus = getCurrentFocus();
            if (focus == null || focus.getTag() instanceof MenuEntry) {
                return focusFirstContent();
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View focus = getCurrentFocus();
            if (focus != null && focus.getTag() instanceof ContentEntry) {
                return focusSelectedMenu();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private String getAndroidSDKVersion() {
        String version = "Android ";
        try {
            version += android.os.Build.VERSION.RELEASE;
            version += " / API " + android.os.Build.VERSION.SDK_INT;
        } catch (Exception e) {
            version += "unknown";
        }
        return version;
    }

    public void openDocumentTree(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
        } else {
            Toast.makeText(this, "当前系统不支持目录授权，请使用U盘 tvbooks 目录", Toast.LENGTH_SHORT).show();
        }
    }

    public void exitApp(View view) {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "存储权限已授权", Toast.LENGTH_SHORT).show();
                initMenu();
            } else {
                Toast.makeText(this, "存储权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == RESULT_OK) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    getContentResolver().takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                }
                SharedPreferences.Editor editor = mSP.edit();
                editor.putString(PREF_USB_URI, treeUri.toString());
                editor.apply();
                Toast.makeText(this, "外部存储授权成功: " + treeUri, Toast.LENGTH_SHORT).show();
                initMenu();
            }
        }
    }

    private void restoreUsbPermission() {
        String usbUriString = mSP.getString(PREF_USB_URI, null);
        if (usbUriString != null) {
            Uri usbUri = Uri.parse(usbUriString);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    getContentResolver().takePersistableUriPermission(
                            usbUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "恢复 USB 存储权限失败: " + e.getMessage());
            }
        }
    }

    private void refreshSettingsText() {
        btnPlayMode.setText(isAutoPlayMode ? "自动" : "手动");
        txtDelaySecondsPerAudio.setText(String.valueOf(Settings.delaySecondsPerAudio));
    }

    private void refreshStatusText(MenuEntry entry) {
        txtSystemVersion.setText(getAndroidSDKVersion());

        String usbPath = Settings.getUSBPath(this);
        txtUsbPath.setText(usbPath == null ? "未找到 tvbooks" : usbPath);
        txtUsbPath.setTextColor(usbPath == null ? Color.rgb(220, 80, 80) : Color.rgb(92, 184, 92));

        File configDir = Settings.getConfigDir(this);
        txtConfigPath.setText(configDir == null ? "未找到" : configDir.getAbsolutePath());

        List<TvBookStore.FavoriteItem> favorites = TvBookStore.getFavorites(this);
        txtFavCount.setText(countFavoritesForEntry(favorites, entry) + " / " + favorites.size());
        txtResumeInfo.setText(buildResumeText(entry));
    }

    private int countFavoritesForEntry(List<TvBookStore.FavoriteItem> favorites, MenuEntry entry) {
        if (entry == null || entry.favoriteRoot) return favorites.size();
        int count = 0;
        String pathPrefix = TvBookStore.normalizePath(entry.path);
        for (TvBookStore.FavoriteItem item : favorites) {
            if (!entry.type.equals(item.type)) continue;
            if (pathPrefix.length() == 0 || item.path.equals(pathPrefix) || item.path.startsWith(pathPrefix + "/")) {
                count++;
            }
        }
        return count;
    }

    private String buildResumeText(MenuEntry entry) {
        if (entry == null || entry.favoriteRoot || entry.h5) return "-";
        String key;
        if (TvBookStore.TYPE_AUDIO_IMAGE.equals(entry.type)) {
            key = "book:" + entry.path;
        } else if (TvBookStore.TYPE_AUDIO.equals(entry.type)) {
            key = "audio:" + entry.path;
        } else {
            return "-";
        }
        TvBookStore.PlaybackState state = TvBookStore.readPlaybackState(this, key);
        if (!state.exists) return "-";
        if (TvBookStore.TYPE_AUDIO_IMAGE.equals(entry.type)) {
            return "第 " + (state.resIndex + 1) + " 本 / 第 " + state.pageIndex + " 页";
        }
        return "第 " + (state.resIndex + 1) + " 首";
    }

    private void saveSetting(String key, String value) {
        if (!TvBookStore.putSetting(this, key, value)) {
            SharedPreferences.Editor editor = mSP.edit();
            if (AUTO_PLAY_KEY.equals(key)) {
                editor.putBoolean(key, Boolean.parseBoolean(value));
            } else if (DELAY_AUDIO_KEY.equals(key)) {
                try {
                    editor.putInt(key, Integer.parseInt(value));
                } catch (Exception e) {
                    editor.putString(key, value);
                }
            } else {
                editor.putString(key, value);
            }
            editor.apply();
        }
    }

    private Button createMenuButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTextColor(Color.rgb(170, 170, 170));
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setFocusable(true);
        button.setClickable(true);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(180));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setBackgroundResource(R.drawable.tv_button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private TextView createContentView(ContentEntry entry) {
        TextView textView = new TextView(this);
        textView.setText(entry.subtitle == null || entry.subtitle.length() == 0
                ? entry.title
                : entry.title + "\n" + entry.subtitle);
        textView.setTextColor(Color.rgb(210, 210, 210));
        textView.setTextSize(18);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textView.setMinHeight(dp(64));
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(dp(20), dp(10), dp(20), dp(10));
        textView.setFocusable(true);
        textView.setClickable(true);
        textView.setNextFocusRightId(R.id.btnSyncCatalog);
        textView.setBackgroundResource(R.drawable.tv_content_item);
        textView.setTag(entry);
        textView.setOnClickListener(this);
        textView.setOnFocusChangeListener(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        textView.setLayoutParams(params);
        return textView;
    }

    private void styleStaticButtons() {
        int[] buttonIds = new int[]{
                R.id.btnPlayMode,
                R.id.btnDec,
                R.id.btnInc,
                R.id.btnSyncCatalog,
                R.id.btn_open_document_tree,
                R.id.btn_exit
        };
        for (int id : buttonIds) {
            View view = findViewById(id);
            if (view instanceof Button) {
                Button button = (Button) view;
                button.setTextColor(Color.WHITE);
                button.setTextSize(14);
                button.setAllCaps(false);
                button.setSingleLine(true);
                button.setEllipsize(TextUtils.TruncateAt.END);
                button.setMinHeight(dp(42));
                if (id == R.id.btnDec || id == R.id.btnInc) {
                    button.setBackgroundResource(R.drawable.tv_small_button);
                } else {
                    button.setBackgroundResource(R.drawable.tv_button);
                }
            }
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
