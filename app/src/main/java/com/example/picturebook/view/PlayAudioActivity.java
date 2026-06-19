package com.example.picturebook.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picturebook.R;
import com.example.picturebook.Settings;
import com.example.picturebook.TvBookStore;
import com.example.picturebook.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayAudioActivity extends PlayBaseActivity implements View.OnClickListener {
    public static final String EXTRA_AUDIO_NAME = "audio_name";

    private List<String> audioFiles = new ArrayList<String>();

    private TextView txtAudioName;
    private TextView txtAudioMeta;
    private TextView txtAudioStatus;
    private TextView txtAudioCover;
    private TextView txtProgressCurrent;
    private TextView txtProgressTotal;
    private LinearLayout viewPlayerControls;
    private Button btnPlayPause;
    private Button btnFavorite;
    private Button btnChangePlayMode;
    private String thisFolderPath;
    private String currentFolderPath;
    private String targetAudioName;
    private Runnable pendingAutoNextRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_audio);
        txtAudioName = findViewById(R.id.txtAudioName);
        txtAudioMeta = findViewById(R.id.txtAudioMeta);
        txtAudioStatus = findViewById(R.id.txtAudioStatus);
        txtAudioCover = findViewById(R.id.txtAudioCover);
        txtProgressCurrent = findViewById(R.id.txtProgressCurrent);
        txtProgressTotal = findViewById(R.id.txtProgressTotal);
        viewPlayerControls = findViewById(R.id.viewPlayerControls);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnChangePlayMode = findViewById(R.id.btnChangePlayMode);
        progressBar = findViewById(R.id.progressBar);
        setControlsVisible(false);

        String rootPath = Settings.getRootPath(this);
        if (rootPath == null) {
            Toast.makeText(PlayAudioActivity.this, "请插入包含 tvbooks 的U盘", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        currentFolderPath = TvBookStore.normalizePath(intent.getType());
        targetAudioName = intent.getStringExtra(EXTRA_AUDIO_NAME);
        File fileDir = new File(rootPath, currentFolderPath);
        thisFolderPath = fileDir.getAbsolutePath();

        bindButtons();

        updateUI = new Runnable() {
            @Override
            public void run() {
                handler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
                handlerUpdateUI.postDelayed(updateUI, 1000);
            }
        };

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_FILES_FOUND_OK) {
                    restoreStartPosition();
                    handlerUpdateUI.postDelayed(updateUI, 1000);
                } else if (msg.what == MSG_PLAY_NEXT) {
                    turnNextRes(false);
                } else if (msg.what == MSG_UPDATE_PROGRESS) {
                    if (mediaPlayer != null) {
                        try {
                            currentPlayMediaPos = mediaPlayer.getCurrentPosition();
                            progressBar.setProgress(currentPlayMediaPos);
                            updateProgressText();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        final File scanDir = fileDir;
        new Thread(new Runnable() {
            public void run() {
                audioFiles = utils.getAllFilesOfDir(scanDir, false);
                handler.sendEmptyMessage(MSG_FILES_FOUND_OK);
            }
        }).start();
    }

    private void bindButtons() {
        int[] buttonIds = new int[]{
                R.id.btnPlayPause,
                R.id.btnPrePage,
                R.id.btnNextPage,
                R.id.btnFavorite,
                R.id.btnChangePlayMode
        };
        for (int id : buttonIds) {
            View view = findViewById(id);
            if (view != null) {
                view.setOnClickListener(this);
            }
        }
    }

    private void restoreStartPosition() {
        if (audioFiles == null || audioFiles.size() == 0) {
            Toast.makeText(PlayAudioActivity.this, "没有找到音频", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int mediaPos = 0;
        if (targetAudioName != null && targetAudioName.length() > 0) {
            int index = audioFiles.indexOf(targetAudioName);
            currentPlayResIndex = index >= 0 ? index : 0;
            TvBookStore.PlaybackState state = TvBookStore.readPlaybackState(this, playbackAudioKey(targetAudioName));
            if (state.exists) {
                mediaPos = state.mediaPos;
            }
        } else {
            TvBookStore.PlaybackState state = TvBookStore.readPlaybackState(this, playbackFolderKey());
            if (state.exists) {
                currentPlayResIndex = state.resIndex;
                mediaPos = state.mediaPos;
            } else {
                String legacyKey = "audioLastIndex_" + currentFolderPath;
                currentPlayResIndex = mSP.getInt(legacyKey, 0);
            }
        }
        playThePage(mediaPos);
    }

    @Override
    protected void onAudioPlayCompletion() {
        if (isAutoPlayMode) {
            cancelPendingAutoNext();
            final int completedIndex = currentPlayResIndex;
            pendingAutoNextRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isAppPaused && isAutoPlayMode && currentPlayResIndex == completedIndex) {
                        handler.sendEmptyMessage(MSG_PLAY_NEXT);
                    }
                }
            };
            handlerUpdateUI.postDelayed(pendingAutoNextRunnable, Settings.getDelaySecondsPerAudio() * 1000L);
        }
    }

    @Override
    protected void turnNextRes(boolean isManualClick) {
        currentPlayResIndex++;
        playThePage(0);
    }

    @Override
    protected synchronized void turnNextPage(boolean isManualClick) {
        turnNextRes(isManualClick);
    }

    @Override
    protected void onKeyDownLeftKey() {
        currentPlayResIndex -= 2;
        turnNextRes(true);
    }

    @Override
    protected void onKeyDownRightKey() {
        turnNextRes(true);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnPlayPause) {
            pausePlay();
        } else if (id == R.id.btnPrePage) {
            currentPlayResIndex -= 2;
            turnNextRes(true);
        } else if (id == R.id.btnNextPage) {
            turnNextRes(true);
        } else if (id == R.id.btnFavorite) {
            toggleFavorite();
        } else if (id == R.id.btnChangePlayMode) {
            isAutoPlayMode = !isAutoPlayMode;
            TvBookStore.putSetting(this, "isAutoPlay", Boolean.toString(isAutoPlayMode));
            showPageInfo();
        }
    }

    private void playThePage(int mediaPos) {
        if (audioFiles == null || audioFiles.size() == 0) {
            return;
        }
        cancelPendingAutoNext();
        if (currentPlayResIndex < 0) {
            currentPlayResIndex = audioFiles.size() - 1;
        }
        if (currentPlayResIndex >= audioFiles.size()) {
            currentPlayResIndex = 0;
        }
        currentPlayMediaPos = Math.max(0, mediaPos);
        if (progressBar != null) {
            progressBar.setProgress(currentPlayMediaPos);
        }
        String audioFileName = audioFiles.get(currentPlayResIndex);
        this.playAudio(new File(this.thisFolderPath, audioFileName).getAbsolutePath());
        showPageInfo();
        saveCurrentPlaybackState();
    }

    @Override
    protected void saveCurrentPlaybackState() {
        if (audioFiles == null || audioFiles.size() == 0 || currentFolderPath == null) return;
        if (currentPlayResIndex < 0 || currentPlayResIndex >= audioFiles.size()) return;
        if (mediaPlayer != null) {
            try {
                currentPlayMediaPos = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                currentPlayMediaPos = 0;
            }
        }
        String audioFileName = audioFiles.get(currentPlayResIndex);
        TvBookStore.savePlaybackState(this, playbackFolderKey(), currentPlayResIndex, 1, currentPlayMediaPos);
        TvBookStore.savePlaybackState(this, playbackAudioKey(audioFileName), currentPlayResIndex, 1, currentPlayMediaPos);
    }

    @Override
    protected boolean toggleFavorite() {
        if (audioFiles == null || audioFiles.size() == 0 || currentPlayResIndex >= audioFiles.size()) return false;
        String audioFileName = audioFiles.get(currentPlayResIndex);
        String path = currentFolderPath + "/" + audioFileName;
        boolean favorite = !TvBookStore.isFavorite(this, TvBookStore.TYPE_AUDIO, path);
        TvBookStore.setFavorite(this, TvBookStore.TYPE_AUDIO, path, audioFileName, favorite);
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
                R.id.btnPlayPause,
                R.id.btnFavorite,
                R.id.btnChangePlayMode
        };
    }

    @Override
    protected void focusPlaybackArea() {
        if (txtAudioCover != null) {
            txtAudioCover.setFocusableInTouchMode(true);
            txtAudioCover.requestFocus();
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
        cancelPendingAutoNext();
        super.release();
    }

    private void showPageInfo() {
        if (audioFiles == null || audioFiles.size() == 0 || currentPlayResIndex < 0 || currentPlayResIndex >= audioFiles.size()) {
            return;
        }
        String audioFileName = audioFiles.get(currentPlayResIndex);
        boolean favorite = TvBookStore.isFavorite(this, TvBookStore.TYPE_AUDIO, currentFolderPath + "/" + audioFileName);
        txtAudioName.setText(audioFileName);
        String meta = (currentPlayResIndex + 1) + " / " + audioFiles.size()
                + "   " + (favorite ? "已收藏" : "未收藏")
                + "   " + (isAutoPlayMode ? "自动" : "手动");
        txtAudioMeta.setText(meta);
        txtAudioStatus.setText(audioFileName + "   " + meta);
        if (btnFavorite != null) {
            btnFavorite.setText(favorite ? "已收藏" : "收藏");
        }
        if (btnPlayPause != null) {
            btnPlayPause.setText(isAppPaused ? "继续" : "暂停");
        }
        if (btnChangePlayMode != null) {
            btnChangePlayMode.setText(isAutoPlayMode ? "自动" : "手动");
        }
        updateProgressText();
    }

    private String playbackFolderKey() {
        return "audio:" + currentFolderPath;
    }

    private String playbackAudioKey(String audioFileName) {
        return "audio-item:" + currentFolderPath + "/" + audioFileName;
    }

    private void setControlsVisible(boolean visible) {
        if (viewPlayerControls == null) return;
        viewPlayerControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && btnPlayPause != null) {
            updateProgressText();
            btnPlayPause.requestFocus();
        }
    }

    private void cancelPendingAutoNext() {
        if (pendingAutoNextRunnable != null) {
            handlerUpdateUI.removeCallbacks(pendingAutoNextRunnable);
            pendingAutoNextRunnable = null;
        }
    }

    private void updateProgressText() {
        int duration = 0;
        if (mediaPlayer != null) {
            try {
                duration = mediaPlayer.getDuration();
            } catch (Exception e) {
                duration = progressBar == null ? 0 : progressBar.getMax();
            }
        } else if (progressBar != null) {
            duration = progressBar.getMax();
        }
        if (txtProgressCurrent != null) {
            txtProgressCurrent.setText(formatDuration(currentPlayMediaPos));
        }
        if (txtProgressTotal != null) {
            txtProgressTotal.setText(formatDuration(duration));
        }
    }

    private String formatDuration(int millis) {
        int seconds = Math.max(0, millis / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }
}
