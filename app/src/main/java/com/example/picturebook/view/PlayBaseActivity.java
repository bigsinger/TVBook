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
import android.view.KeyEvent;
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
    private static final String AUTO_PLAY_KEY = "isAutoPlay";
    protected SharedPreferences mSP;

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
        if (updateUI != null) {
            handlerUpdateUI.removeCallbacks(updateUI);
        }
        if (mediaPlayer != null) {
            try {
                currentPlayMediaPos = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                currentPlayMediaPos = 0;
            }
            saveCurrentPlaybackState();
            try {
                mediaPlayer.stop();
            } catch (Exception e) {
                e.printStackTrace();
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

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME: {
                Toast.makeText(PlayBaseActivity.this, "返回", Toast.LENGTH_SHORT).show();
                this.release();
                this.finish();
                return true;
            }
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                //确定键enter
                pausePlay();
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

    protected void pausePlay() {
        isAppPaused = !isAppPaused;
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isAutoPlayMode = false;
            } else {
                mediaPlayer.start();
            }
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
        if (!new File(audioFilePath).exists()) {
            isAudioExistThisPage = false;
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        } else {
            isAudioExistThisPage = true;
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .build());
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                }

                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        if (currentPlayMediaPos > 0 && currentPlayMediaPos < mp.getDuration()) {
                            mp.seekTo(currentPlayMediaPos);
                        }
                        mp.start();
                        if (progressBar != null) {
                            progressBar.setMax(mp.getDuration());
                        }
                    }
                });

                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        currentPlayMediaPos = 0;
                        onAudioPlayCompletion();
                    }
                });
            } else {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                try {
                    mediaPlayer.reset();
                } catch (Exception e) {
                    Toast.makeText(PlayBaseActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                }

            }
            try {
                mediaPlayer.setDataSource(audioFilePath);
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Toast.makeText(PlayBaseActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                //例如音频存在但是音频为空文件，就会有异常，也默认是播放完毕了
                onAudioPlayCompletion();
            }
        } catch (Exception e) {
            Toast.makeText(PlayBaseActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

}
