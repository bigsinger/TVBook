package com.example.picturebook.view;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picturebook.R;
import com.example.picturebook.Settings;
import com.example.picturebook.TvBookStore;
import com.example.picturebook.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayHuibenActivity extends PlayBaseActivity implements View.OnClickListener {
    public static final String EXTRA_BOOK_NAME = "book_name";
    public static final String EXTRA_PLAYLIST_PATHS = "playlist_paths";
    public static final String EXTRA_PLAYLIST_KEY = "playlist_key";

    private static final String TAG = "PlayHuibenActivity";
    private ImageView imageView;
    private TextView txtViewInfo;
    private LinearLayout viewPlayerControls;
    private ProgressBar pageProgressBar;
    private TextView txtProgressCurrent;
    private TextView txtProgressTotal;
    private Button btnPlayPause;
    private Button btnFavorite;
    private Button btnChangePlayMode;
    private Runnable pendingAutoPageRunnable;
    private int autoPageToken = 0;
    private long currentPageStartedAt = 0;
    private boolean currentPageAudioFinished = true;

    private String currentBookType = null;
    private String currentBookName = null;
    private String targetBookName = null;
    private String rootPath = null;
    private String playlistKey = null;
    private boolean playlistMode = false;
    private List<String> bookNames = new ArrayList<String>();
    private int currentBookPageCount = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_huiben);

        rootPath = Settings.getRootPath(this);
        if (rootPath == null) {
            Toast.makeText(PlayHuibenActivity.this, "请插入包含 tvbooks 的U盘", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imageView = findViewById(R.id.imageView);
        txtViewInfo = findViewById(R.id.txtViewInfo);
        viewPlayerControls = findViewById(R.id.viewPlayerControls);
        pageProgressBar = findViewById(R.id.pageProgressBar);
        txtProgressCurrent = findViewById(R.id.txtProgressCurrent);
        txtProgressTotal = findViewById(R.id.txtProgressTotal);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnChangePlayMode = findViewById(R.id.btnChangePlayMode);
        txtViewInfo.setVisibility(View.GONE);
        setControlsVisible(false);

        Intent intent = getIntent();
        currentBookType = TvBookStore.normalizePath(intent.getType());
        targetBookName = intent.getStringExtra(EXTRA_BOOK_NAME);
        String[] playlistPaths = intent.getStringArrayExtra(EXTRA_PLAYLIST_PATHS);
        playlistMode = playlistPaths != null && playlistPaths.length > 0;
        playlistKey = intent.getStringExtra(EXTRA_PLAYLIST_KEY);
        if (playlistKey == null || playlistKey.length() == 0) {
            playlistKey = "book:" + currentBookType;
        }

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_FILES_FOUND_OK) {
                    restoreStartPosition();
                }
            }
        };

        if (playlistMode) {
            bookNames = buildBookPlaylist(playlistPaths);
            handler.sendEmptyMessage(MSG_FILES_FOUND_OK);
        } else {
            final File bookTypeDir = new File(rootPath, currentBookType);
            new Thread(new Runnable() {
                public void run() {
                    bookNames = utils.getAllFilesOfDir(bookTypeDir, true);
                    handler.sendEmptyMessage(MSG_FILES_FOUND_OK);
                }
            }).start();
        }
    }

    private List<String> buildBookPlaylist(String[] playlistPaths) {
        List<String> paths = new ArrayList<String>();
        if (playlistPaths == null) return paths;
        for (String item : playlistPaths) {
            String path = TvBookStore.normalizePath(item);
            if (path.length() == 0) continue;
            File dir = new File(rootPath, path);
            if (dir.isDirectory()) {
                paths.add(path);
            }
        }
        return paths;
    }

    private void restoreStartPosition() {
        if (bookNames == null || bookNames.size() == 0) {
            Toast.makeText(PlayHuibenActivity.this, "没有找到绘本", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int startPage = 1;
        int startMediaPos = 0;
        if (targetBookName != null && targetBookName.length() > 0) {
            String target = playlistMode ? TvBookStore.normalizePath(targetBookName) : targetBookName;
            int index = bookNames.indexOf(target);
            currentPlayResIndex = index >= 0 ? index : 0;
            TvBookStore.PlaybackState state = TvBookStore.readPlaybackState(this, playbackBookKeyFromPath(bookPathForItem(target)));
            if (state.exists) {
                startPage = Math.max(1, state.pageIndex);
                startMediaPos = state.mediaPos;
            }
        } else {
            TvBookStore.PlaybackState state = TvBookStore.readPlaybackState(this, playbackCategoryKey());
            if (state.exists) {
                currentPlayResIndex = state.resIndex;
                startPage = Math.max(1, state.pageIndex);
                startMediaPos = state.mediaPos;
            } else {
                String legacyKey = "resLastIndex_" + new File(currentBookType).getName();
                currentPlayResIndex = mSP.getInt(legacyKey, 0);
            }
        }

        changeBook(startPage, startMediaPos);
    }

    private void changeBook(int pageIndex, int mediaPos) {
        if (bookNames == null || bookNames.size() == 0) {
            Toast.makeText(PlayHuibenActivity.this, "没有找到绘本", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (currentPlayResIndex < 0 || currentPlayResIndex >= bookNames.size()) {
            currentPlayResIndex = 0;
        }
        String item = bookNames.get(currentPlayResIndex).trim();
        if (playlistMode) {
            String bookPath = TvBookStore.normalizePath(item);
            currentBookType = TvBookStore.parentPath(bookPath);
            currentBookName = TvBookStore.fileName(bookPath);
        } else {
            currentBookName = item;
        }
        currentPlayPageIndex = Math.max(1, pageIndex);
        currentBookPageCount = Math.max(1, countPagesForBook(currentBookName));
        playCurrentPage(mediaPos);
    }

    private void playCurrentPage(int mediaPos) {
        cancelPendingAutoPage();
        final int pageToken = ++autoPageToken;
        currentPageStartedAt = System.currentTimeMillis();
        currentPlayMediaPos = Math.max(0, mediaPos);
        File pageImageFile = new File(rootPath, currentBookType + "/" + currentBookName + "/" + currentPlayPageIndex + ".jpg");
        File pageAudioFile = new File(rootPath, currentBookType + "/" + currentBookName + "/" + currentPlayPageIndex + ".mp3");
        currentPageAudioFinished = !pageAudioFile.exists();
        showPageImage(pageImageFile.getAbsolutePath());
        showPageInfo();
        playAudio(pageAudioFile.getAbsolutePath());
        if (!isAudioExistThisPage) {
            currentPageAudioFinished = true;
        }
        lastChangeTime = System.currentTimeMillis();
        saveCurrentPlaybackState();
        scheduleAutoPageCheck(pageToken, autoDelayMillis());
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.imageView || id == R.id.btnPlayPause) {
            pausePlay();
        } else if (id == R.id.btnFavorite) {
            toggleFavorite();
        } else if (id == R.id.btnNextPage) {
            turnNextPage(true);
        } else if (id == R.id.btnPrePage) {
            turnPrevPage();
        } else if (id == R.id.btnNextBook) {
            turnNextBook();
        } else if (id == R.id.btnPrevBook) {
            currentPlayResIndex -= 2;
            turnNextBook();
        } else if (id == R.id.btnChangePlayMode) {
            isAutoPlayMode = !isAutoPlayMode;
            TvBookStore.putSetting(this, "isAutoPlay", Boolean.toString(isAutoPlayMode));
            if (isAutoPlayMode) {
                maybeAutoAdvance(autoPageToken);
            } else {
                cancelPendingAutoPage();
            }
            showPageInfo();
        }
    }

    private void turnNextBook() {
        currentPlayResIndex++;
        if (currentPlayResIndex >= bookNames.size()) {
            currentPlayResIndex = 0;
        }
        changeBook(1, 0);
    }

    private void turnPrevPage() {
        currentPlayPageIndex--;
        if (currentPlayPageIndex <= 0) {
            currentPlayPageIndex = 1;
            Toast.makeText(PlayHuibenActivity.this, "已经是首页", Toast.LENGTH_SHORT).show();
        } else {
            playCurrentPage(0);
        }
    }

    @Override
    protected void onAudioPlayCompletion() {
        currentPageAudioFinished = true;
        maybeAutoAdvance(autoPageToken);
    }

    @Override
    protected void turnNextRes(boolean isManualClick) {
        turnNextBook();
    }

    @Override
    protected synchronized void turnNextPage(boolean isManualClick) {
        currentPlayPageIndex++;
        File pageImageFile = new File(rootPath, currentBookType + "/" + currentBookName + "/" + currentPlayPageIndex + ".jpg");
        File pageAudioFile = new File(rootPath, currentBookType + "/" + currentBookName + "/" + currentPlayPageIndex + ".mp3");
        if (!pageImageFile.exists() && !pageAudioFile.exists()) {
            currentPlayPageIndex--;
            if (isManualClick) {
                Toast.makeText(PlayHuibenActivity.this, "已经播放完毕", Toast.LENGTH_SHORT).show();
            } else {
                turnNextBook();
            }
        } else {
            playCurrentPage(0);
        }
    }

    @Override
    protected void saveCurrentPlaybackState() {
        if (currentBookType == null || currentBookName == null) return;
        if (mediaPlayer != null) {
            try {
                currentPlayMediaPos = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                currentPlayMediaPos = 0;
            }
        }
        TvBookStore.savePlaybackState(this, playbackCategoryKey(), currentPlayResIndex, currentPlayPageIndex, currentPlayMediaPos);
        TvBookStore.savePlaybackState(this, playbackBookKey(currentBookName), currentPlayResIndex, currentPlayPageIndex, currentPlayMediaPos);
    }

    @Override
    protected boolean toggleFavorite() {
        if (currentBookType == null || currentBookName == null) return false;
        String path = currentBookType + "/" + currentBookName;
        boolean favorite = !TvBookStore.isFavorite(this, TvBookStore.TYPE_AUDIO_IMAGE, path);
        TvBookStore.setFavorite(this, TvBookStore.TYPE_AUDIO_IMAGE, path, currentBookName, favorite);
        showPageInfo();
        Toast.makeText(this, favorite ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    protected boolean toggleControlsVisibility() {
        if (viewPlayerControls == null) return false;
        setControlsVisible(viewPlayerControls.getVisibility() != View.VISIBLE);
        return true;
    }

    @Override
    protected boolean hideControls() {
        setControlsVisible(false);
        return true;
    }

    @Override
    protected View getControlsView() {
        return viewPlayerControls;
    }

    @Override
    protected View getDefaultControlFocusView() {
        return btnPlayPause;
    }

    @Override
    protected int[] getControlButtonIds() {
        return new int[]{
                R.id.btnPrePage,
                R.id.btnNextPage,
                R.id.btnPrevBook,
                R.id.btnNextBook,
                R.id.btnPlayPause,
                R.id.btnFavorite,
                R.id.btnChangePlayMode
        };
    }

    @Override
    protected void focusPlaybackArea() {
        if (imageView != null) {
            imageView.setFocusableInTouchMode(true);
            imageView.requestFocus();
        } else {
            super.focusPlaybackArea();
        }
    }

    @Override
    protected boolean showControls() {
        setControlsVisible(true);
        return true;
    }

    @Override
    protected void pausePlay() {
        super.pausePlay();
        showPageInfo();
    }

    @Override
    protected void release() {
        cancelPendingAutoPage();
        super.release();
    }

    private String playbackCategoryKey() {
        return playlistMode ? "book-list:" + playlistKey : "book:" + currentBookType;
    }

    private String playbackBookKey(String bookName) {
        return playbackBookKeyFromPath(currentBookType + "/" + bookName);
    }

    private String playbackBookKeyFromPath(String bookPath) {
        return "book-item:" + TvBookStore.normalizePath(bookPath);
    }

    private String bookPathForItem(String item) {
        String safe = TvBookStore.normalizePath(item);
        return playlistMode ? safe : currentBookType + "/" + safe;
    }

    private void showPageImage(String imageFilePath) {
        Drawable drawable = Drawable.createFromPath(imageFilePath);
        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        }
    }

    private void showPageInfo() {
        boolean favorite = TvBookStore.isFavorite(this, TvBookStore.TYPE_AUDIO_IMAGE, currentBookType + "/" + currentBookName);
        txtViewInfo.setText(String.format("%s   第 %d/%d 页   %s   %s",
                currentBookName,
                currentPlayPageIndex,
                currentBookPageCount,
                favorite ? "已收藏" : "未收藏",
                isAutoPlayMode ? "自动" : "手动"));
        updatePageProgress();
        if (btnFavorite != null) {
            btnFavorite.setText(favorite ? "已收藏" : "收藏");
        }
        if (btnPlayPause != null) {
            btnPlayPause.setText(isAppPaused ? "继续" : "暂停");
        }
        if (btnChangePlayMode != null) {
            btnChangePlayMode.setText(isAutoPlayMode ? "自动" : "手动");
        }
    }

    private void setControlsVisible(boolean visible) {
        if (viewPlayerControls == null) return;
        viewPlayerControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (txtViewInfo != null) {
            txtViewInfo.setVisibility(View.GONE);
        }
        if (visible && btnPlayPause != null) {
            btnPlayPause.requestFocus();
        }
    }

    private void cancelPendingAutoPage() {
        if (pendingAutoPageRunnable != null) {
            handlerUpdateUI.removeCallbacks(pendingAutoPageRunnable);
            pendingAutoPageRunnable = null;
        }
    }

    private long autoDelayMillis() {
        return Math.max(0, Settings.getDelaySecondsPerAudio()) * 1000L;
    }

    private void scheduleAutoPageCheck(final int pageToken, long delayMillis) {
        cancelPendingAutoPage();
        if (!isAutoPlayMode || isAppPaused) {
            return;
        }
        pendingAutoPageRunnable = new Runnable() {
            @Override
            public void run() {
                pendingAutoPageRunnable = null;
                maybeAutoAdvance(pageToken);
            }
        };
        handlerUpdateUI.postDelayed(pendingAutoPageRunnable, Math.max(0, delayMillis));
    }

    private void maybeAutoAdvance(int pageToken) {
        if (pageToken != autoPageToken || !isAutoPlayMode || isAppPaused) {
            return;
        }
        long intervalMillis = autoDelayMillis();
        long elapsedMillis = System.currentTimeMillis() - currentPageStartedAt;
        if (elapsedMillis < intervalMillis) {
            scheduleAutoPageCheck(pageToken, intervalMillis - elapsedMillis);
            return;
        }
        if (!currentPageAudioFinished) {
            return;
        }
        turnNextPage(false);
    }

    private void updatePageProgress() {
        int pageCount = Math.max(1, currentBookPageCount);
        int pageIndex = Math.max(1, Math.min(currentPlayPageIndex, pageCount));
        if (pageProgressBar != null) {
            pageProgressBar.setMax(pageCount);
            pageProgressBar.setProgress(pageIndex);
        }
        if (txtProgressCurrent != null) {
            txtProgressCurrent.setText(pageIndex + " / " + pageCount);
        }
        if (txtProgressTotal != null) {
            txtProgressTotal.setText(pageCount + " 页");
        }
    }

    private int countPagesForBook(String bookName) {
        File rootDir = Settings.getRootDir(this);
        if (rootDir == null || bookName == null) return 1;
        File bookDir = new File(rootDir, currentBookType + "/" + bookName);
        File[] files = bookDir.listFiles();
        if (files == null) return 1;
        int count = 0;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
