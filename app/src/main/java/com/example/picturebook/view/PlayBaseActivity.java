package com.example.picturebook.view;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.picturebook.TvBookStore;

import java.io.File;

public abstract class PlayBaseActivity extends Activity {
    public static final String TAG = PlayBaseActivity.class.getSimpleName();
    public static final int MSG_FILES_FOUND_OK = 0;
    public static final int MSG_PLAY_NEXT = 1;
    public static final int MSG_UPDATE_PROGRESS = 2;
    private static final long BACK_CONFIRM_INTERVAL_MS = 2000L;
    private static final int MIN_RESUME_REMAINING_MS = 5000;
    private static final int COMPLETION_TOLERANCE_MS = 1500;
    private static final String AUTO_PLAY_KEY = "isAutoPlay";
    protected SharedPreferences mSP;
    private long lastBackPressedAt = 0;
    private int audioRequestSerial = 0;
    private int activeAudioRequestSerial = 0;
    private boolean activeAudioCompletionDelivered = false;
    private int activeAudioDurationMs = 0;
    private int activeAudioStartPositionMs = 0;
    private long activeAudioStartedAtMs = 0;
    private Runnable audioCompletionWatchdog = null;
    private boolean audioPrepared = false;

    // App当前状态
    protected  boolean isAppPaused = false;

    protected ProgressBar progressBar;
    protected MediaPlayer mediaPlayer = null;

    // 是否自动播放
    protected boolean isAutoPlayMode = true;

    // 当前播放的资源序号
    protected int currentPlayResIndex = 0;

    // 当前播放的页码序号
    protected int currentPlayPageIndex = 0;

    // 当前播放的音频位置
    protected int currentPlayMediaPos = 0;

    // 记录上一次切换的时间
    protected long lastChangeTime = 0;

    // 本页有无音频
    protected boolean isAudioExistThisPage = true;

    //更新UI
    protected Runnable updateUI = null;

    //主线程创建handler，在子线程中通过handler的post(Runnable)方法更新UI信息。
    protected Handler handlerUpdateUI = new Handler();

    protected Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //应用运行时，保持屏幕高亮，不锁屏
        mSP = getSharedPreferences("cache", Context.MODE_PRIVATE);
        isAutoPlayMode = TvBookStore.getBooleanSetting(this, AUTO_PLAY_KEY, mSP.getBoolean(AUTO_PLAY_KEY, true));
        isAppPaused = false;
    }

    abstract protected void onAudioPlayCompletion();

    abstract protected void turnNextRes(boolean isManualClick);

    abstract protected void turnNextPage(boolean isManualClick);

    protected void saveCurrentPlaybackState() {
    }

    protected boolean toggleFavorite() {
        return false;
    }

    protected boolean toggleControlsVisibility() {
        return false;
    }

    protected boolean showControls() {
        return toggleControlsVisibility();
    }

    protected boolean hideControls() {
        return false;
    }

    protected View getControlsView() {
        return null;
    }

    protected View getDefaultControlFocusView() {
        return null;
    }

    protected int[] getControlButtonIds() {
        return new int[0];
    }

    protected boolean onFocusedControlKey(int keyCode, View focus) {
        return false;
    }

    protected boolean useAudioCompletionWatchdog() {
        return true;
    }

    protected boolean isAudioPrepared() {
        return audioPrepared;
    }

    protected void focusPlaybackArea() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setFocusableInTouchMode(true);
            decorView.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppPaused = false;
    }

    //释放资源
    @Override
    protected void onDestroy() {
        this.release();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        this.release();
        super.onPause();
    }

    protected void release() {
        isAppPaused = true;
        boolean playerWasPrepared = audioPrepared;
        if (mediaPlayer != null && playerWasPrepared) {
            try {
                currentPlayMediaPos = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                currentPlayMediaPos = 0;
            }
        }
        invalidateAudioRequest();
        if (updateUI != null) {
            handlerUpdateUI.removeCallbacks(updateUI);
        }
        if (mediaPlayer != null) {
            saveCurrentPlaybackState();
            if (playerWasPrepared) {
                try {
                    mediaPlayer.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            mediaPlayer.release();
            mediaPlayer = null;
        } else {
            saveCurrentPlaybackState();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        return handleKeyEvent(action, keyCode) || super.dispatchKeyEvent(event);
    }

    private boolean handleKeyEvent(int action, int keyCode) {
        if (action != KeyEvent.ACTION_DOWN)
            return false;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBackKey();
        }
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            this.release();
            this.finish();
            return true;
        }
        if (areControlsVisible()) {
            return handleControlKey(keyCode);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                //确定键enter
                pausePlay();
                showControls();
                return true;
            case KeyEvent.KEYCODE_MENU:
                return toggleControlsVisibility();
            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_PROG_RED:
                return toggleFavorite();
            case KeyEvent.KEYCODE_DPAD_DOWN:
                //向下键
                onKeyDownDownKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                //向上键
                onKeyDownUpKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                //向左键
                onKeyDownLeftKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                //向右键
                onKeyDownRightKey();
                return true;
            default:
                break;
        }
        return false;
    }

    private boolean handleBackKey() {
        if (areControlsVisible()) {
            hideControls();
            focusPlaybackArea();
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < BACK_CONFIRM_INTERVAL_MS) {
            this.release();
            this.finish();
        } else {
            lastBackPressedAt = now;
            Toast.makeText(PlayBaseActivity.this, "再按一次返回退出播放", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean handleControlKey(int keyCode) {
        View focus = getCurrentFocus();
        if (onFocusedControlKey(keyCode, focus)) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                hideControls();
                focusPlaybackArea();
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!isFocusInsideControls(focus)) {
                    focusDefaultControl();
                    focus = getCurrentFocus();
                }
                if (focus != null && isFocusInsideControls(focus)) {
                    focus.performClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return moveControlFocus(-1);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return moveControlFocus(1);
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focusDefaultControl();
                return true;
            default:
                return false;
        }
    }

    private boolean areControlsVisible() {
        View controls = getControlsView();
        return controls != null && controls.getVisibility() == View.VISIBLE;
    }

    protected boolean focusDefaultControl() {
        View defaultView = getDefaultControlFocusView();
        if (defaultView != null) {
            defaultView.requestFocus();
            return true;
        }
        return moveControlFocus(1);
    }

    private boolean moveControlFocus(int direction) {
        int[] ids = getControlButtonIds();
        if (ids == null || ids.length == 0) {
            return false;
        }
        int currentIndex = -1;
        View current = getCurrentFocus();
        if (current != null) {
            int focusedId = current.getId();
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == focusedId) {
                    currentIndex = i;
                    break;
                }
            }
        }
        int nextIndex = currentIndex < 0
                ? 0
                : (currentIndex + direction + ids.length) % ids.length;
        View next = findViewById(ids[nextIndex]);
        if (next != null) {
            next.requestFocus();
            return true;
        }
        return false;
    }

    private boolean isFocusInsideControls(View focus) {
        View controls = getControlsView();
        if (focus == null || controls == null) return false;
        if (focus == controls) return true;
        ViewParent parent = focus.getParent();
        while (parent instanceof View) {
            if (parent == controls) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    protected void pausePlay() {
        if (mediaPlayer != null && audioPrepared) {
            if (mediaPlayer.isPlaying()) {
                try {
                    currentPlayMediaPos = mediaPlayer.getCurrentPosition();
                } catch (Exception e) {
                    currentPlayMediaPos = 0;
                }
                mediaPlayer.pause();
                isAppPaused = true;
                markAudioPosition(currentPlayMediaPos);
                cancelAudioCompletionWatchdog();
            } else if (isAppPaused) {
                mediaPlayer.start();
                isAppPaused = false;
                markAudioPosition(currentPlayMediaPos);
                scheduleAudioCompletionWatchdog(activeAudioRequestSerial);
            } else {
                isAppPaused = true;
                cancelAudioCompletionWatchdog();
            }
        } else {
            isAppPaused = !isAppPaused;
        }
    }

    protected void onKeyDownUpKey() {
        currentPlayResIndex -= 2;
        turnNextRes(true);
    }

    protected void onKeyDownDownKey() {
        turnNextRes(true);
    }

    protected void onKeyDownLeftKey() {
        currentPlayPageIndex -= 2;
        turnNextPage(true);
    }

    protected void onKeyDownRightKey() {
        turnNextPage(true);
    }


    protected void playAudio(String audioFilePath) {
        final int requestSerial = ++audioRequestSerial;
        audioPrepared = false;
        activeAudioRequestSerial = requestSerial;
        activeAudioCompletionDelivered = false;
        activeAudioDurationMs = 0;
        activeAudioStartPositionMs = Math.max(0, currentPlayMediaPos);
        activeAudioStartedAtMs = 0;
        cancelAudioCompletionWatchdog();
        stopAndReleaseMediaPlayer();

        if (!new File(audioFilePath).exists()) {
            isAudioExistThisPage = false;
            return;
        } else {
            isAudioExistThisPage = true;
        }

        try {
            final MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build());
            } else {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (!isCurrentAudioRequest(requestSerial, mp)) {
                        return;
                    }
                    audioPrepared = true;
                    int duration = mp.getDuration();
                    if (currentPlayMediaPos > 0 && currentPlayMediaPos < duration - MIN_RESUME_REMAINING_MS) {
                        mp.seekTo(currentPlayMediaPos);
                    } else if (currentPlayMediaPos > 0) {
                        currentPlayMediaPos = 0;
                    }
                    activeAudioDurationMs = Math.max(0, duration);
                    markAudioPosition(currentPlayMediaPos);
                    mp.start();
                    isAppPaused = false;
                    if (progressBar != null) {
                        progressBar.setMax(duration);
                    }
                    scheduleAudioCompletionWatchdog(requestSerial);
                }
            });

            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    handleAudioCompletion(requestSerial, mp);
                }
            });

            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(final MediaPlayer mp, int what, int extra) {
                    if (!isCurrentAudioRequest(requestSerial, mp)) {
                        return true;
                    }
                    audioPrepared = false;
                    handlerUpdateUI.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isCurrentAudioRequest(requestSerial, mp)) {
                                return;
                            }
                            stopAndReleaseMediaPlayer();
                            Toast.makeText(PlayBaseActivity.this, "音频无法播放，已跳过", Toast.LENGTH_SHORT).show();
                            deliverAudioCompletion(requestSerial);
                        }
                    });
                    return true;
                }
            });

            player.setDataSource(audioFilePath);
            player.prepareAsync();
        } catch (Exception e) {
            if (requestSerial == activeAudioRequestSerial) {
                stopAndReleaseMediaPlayer();
            }
            Toast.makeText(PlayBaseActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
            deliverAudioCompletion(requestSerial);
        }
    }

    protected void notifyAudioPositionChanged(int positionMs) {
        currentPlayMediaPos = Math.max(0, positionMs);
        markAudioPosition(currentPlayMediaPos);
        scheduleAudioCompletionWatchdog(activeAudioRequestSerial);
    }

    private boolean isCurrentAudioRequest(int requestSerial, MediaPlayer player) {
        return requestSerial == activeAudioRequestSerial && player != null && player == mediaPlayer;
    }

    private void handleAudioCompletion(int requestSerial, MediaPlayer player) {
        if (!isCurrentAudioRequest(requestSerial, player) || activeAudioCompletionDelivered) {
            return;
        }
        if (!useAudioCompletionWatchdog()) {
            deliverAudioCompletion(requestSerial);
            return;
        }
        if (!hasReachedExpectedAudioEnd()) {
            scheduleAudioCompletionWatchdog(requestSerial);
            return;
        }
        deliverAudioCompletion(requestSerial);
    }

    private boolean hasReachedExpectedAudioEnd() {
        if (activeAudioDurationMs <= 0 || activeAudioDurationMs <= 4000 || activeAudioStartedAtMs <= 0) {
            return true;
        }
        long playedMs = activeAudioStartPositionMs + (SystemClock.elapsedRealtime() - activeAudioStartedAtMs);
        return playedMs >= activeAudioDurationMs - COMPLETION_TOLERANCE_MS;
    }

    private void deliverAudioCompletion(int requestSerial) {
        if (requestSerial != activeAudioRequestSerial || activeAudioCompletionDelivered) {
            return;
        }
        activeAudioCompletionDelivered = true;
        cancelAudioCompletionWatchdog();
        currentPlayMediaPos = 0;
        onAudioPlayCompletion();
    }

    private void scheduleAudioCompletionWatchdog(final int requestSerial) {
        cancelAudioCompletionWatchdog();
        if (!useAudioCompletionWatchdog()) {
            return;
        }
        if (requestSerial != activeAudioRequestSerial || activeAudioCompletionDelivered || isAppPaused || activeAudioDurationMs <= 0) {
            return;
        }
        long playedMs = activeAudioStartPositionMs;
        if (activeAudioStartedAtMs > 0) {
            playedMs += SystemClock.elapsedRealtime() - activeAudioStartedAtMs;
        }
        long delayMs = activeAudioDurationMs - playedMs + COMPLETION_TOLERANCE_MS;
        if (delayMs < 500) {
            delayMs = 500;
        }
        audioCompletionWatchdog = new Runnable() {
            @Override
            public void run() {
                audioCompletionWatchdog = null;
                if (requestSerial != activeAudioRequestSerial || activeAudioCompletionDelivered || isAppPaused) {
                    return;
                }
                deliverAudioCompletion(requestSerial);
            }
        };
        handlerUpdateUI.postDelayed(audioCompletionWatchdog, delayMs);
    }

    private void cancelAudioCompletionWatchdog() {
        if (audioCompletionWatchdog != null) {
            handlerUpdateUI.removeCallbacks(audioCompletionWatchdog);
            audioCompletionWatchdog = null;
        }
    }

    private void markAudioPosition(int positionMs) {
        activeAudioStartPositionMs = Math.max(0, positionMs);
        activeAudioStartedAtMs = SystemClock.elapsedRealtime();
    }

    private void invalidateAudioRequest() {
        activeAudioRequestSerial = ++audioRequestSerial;
        audioPrepared = false;
        activeAudioCompletionDelivered = true;
        activeAudioDurationMs = 0;
        activeAudioStartPositionMs = 0;
        activeAudioStartedAtMs = 0;
        cancelAudioCompletionWatchdog();
    }

    private void stopAndReleaseMediaPlayer() {
        MediaPlayer player = mediaPlayer;
        boolean playerWasPrepared = audioPrepared;
        audioPrepared = false;
        mediaPlayer = null;
        if (player == null) {
            return;
        }
        try {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            if (playerWasPrepared && player.isPlaying()) {
                player.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            player.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
