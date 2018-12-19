package com.xmamiga.btrecord;

import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.xxhander.BaseHandlerOperate;
import com.xxhander.BaseHandlerUpDate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String TAG = "BluetoothRecord";

    private static final String DIR_NAME = "xmamiga_bluerecord";

    private static final int HANDLER_SCO_CHANGE = 1;
    private static final int HANDLER_START_RECORDING = 2;

    private String mFileName;
    private AudioManager mAudioManager = null;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private BluetoothReceiver bluetoothVolumeReceiver;
    private TextView mTvRecordTip;
    private StringBuilder mTipBuilder;
    private ScrollView mScrollView;
    private SCOReceiver mSCOReceiver;
    private BluetoothAdapter mBtAdapter;
    private DeviceConnectReceiver mDeviceConnectReceiver;
    TextView mTvRecordFileAddr;
    private TitleBarImpl titleBarUtil;
    TextView mTvPlayRecording;
    TextView mTvStartRecording;
    private String mFileDir;

    private int mSamplingRate = 8000;

    // 要申请的权限
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {

            if (HANDLER_START_RECORDING == msg.what) {
                toggleStartRecording();
            } else if (HANDLER_SCO_CHANGE == msg.what) {
                if (mAudioManager != null) {
//                    startBluetoothSCO();
                }
            }
        }
    };
    private Button mBtn8000;
    private Button mBtn16000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BaseHandlerOperate.getBaseHandlerOperate().addKeyHandler(MainActivity.class, mBaseHandlerUpDate);
        getTopTitleBar().setTitle(R.string.main_title);

        initPermission();

        initView();
        initData();

    }

    private void initView() {

        mTvPlayRecording = (TextView) findViewById(R.id.tv_play_recording);
        mTvPlayRecording.setOnClickListener(new PlayRecordingListener());
        mTvStartRecording = (TextView) findViewById(R.id.tv_start_recording);
        mTvStartRecording.setOnClickListener(new StartRecordingListener());

        mScrollView = (ScrollView) findViewById(R.id.scrollview);
        mTvRecordTip = (TextView) findViewById(R.id.tv_record_tip);
        mTvRecordFileAddr = (TextView) findViewById(R.id.tv_path);

        mBtn8000 = (Button) findViewById(R.id.btn_8000);
        mBtn8000.setTextColor(Color.RED);
        mBtn16000 = (Button) findViewById(R.id.btn_16000);
        mBtn8000.setOnClickListener(this);
        mBtn16000.setOnClickListener(this);
    }

    private void initData() {

        mBtAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        mTipBuilder = new StringBuilder();
        mFileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + DIR_NAME;
        File fileDir = new File(mFileDir);
        if (!fileDir.exists()) {
            fileDir.mkdir();
        }

        //第一次进入成功的时候，得到目录下最早的文件进行播放
        String[] list = fileDir.list();

        if (list == null || list.length == 0) {
            mTvRecordFileAddr.setText(getString(R.string.tip_record_file_address_1, DIR_NAME));
        } else {
            mFileName = mFileDir + "/" + list[list.length - 1];
            mTvRecordFileAddr.setText(getString(R.string.tip_record_file_address, mFileName, DIR_NAME, list[list.length - 1]));
        }
    }

    private void initPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS};
            List<String> denyPermissions = new ArrayList<>();
            for (String permission : permissions) {
                if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission)) {
                    denyPermissions.add(permission);
                    //进入到这里代表没有权限.
                }
            }

            if (!denyPermissions.isEmpty()) {
                requestPermissions(denyPermissions.toArray(new String[denyPermissions.size()]), 101);
            }
        }
    }

    private void createRecordFile() {
        String fileName = System.currentTimeMillis() + ".3gp";
        mFileName = mFileDir + "/" + fileName;
        mTvRecordFileAddr.setText(getString(R.string.tip_record_file_address, mFileName, DIR_NAME, fileName));
    }

    @Override
    protected void onResume() {
        super.onResume();

        //判断是否打开了蓝牙
        if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
            setTipText(R.string.tip_please_connect_to_bluetooth_headset);
        }

        registerConnectStateReceiver();
        requestProfileConnectionState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        unRegisterConnectStateReceiver();

        stopRecording();

        stopPlaySoundRecording();
        setStartRecordingStatus();
    }

    private void setTipText(int strId) {
        String newStr = getString(strId);
        int start = mTipBuilder.length();
        int end = start + newStr.length();
        mTipBuilder.append(newStr);

        SpannableStringBuilder builder = new SpannableStringBuilder(mTipBuilder.toString());
        //ForegroundColorSpan 为文字前景色，BackgroundColorSpan为文字背景色
        ForegroundColorSpan redSpan = new ForegroundColorSpan(0xffff3b30);

        builder.setSpan(redSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        mTvRecordTip.setText(builder);

        mTvRecordTip.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void unRegisterVolumeReceiver() {
        if (bluetoothVolumeReceiver != null) {
            MainActivity.this.unregisterReceiver(bluetoothVolumeReceiver);
            bluetoothVolumeReceiver = null;
        }
    }

    private class DeviceConnectReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int a2dpState = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
                if (BluetoothA2dp.STATE_CONNECTED == a2dpState) {
                    setTipText(R.string.tip_bluetooth_connected);
                    checkSCOEnable();
                } else if (BluetoothA2dp.STATE_DISCONNECTED == a2dpState) {//正在断开连接
                    setTipText(R.string.tip_bluetooth_disconnected);
                    stopBluetoothSCO();
                }
            }
        }
    }

    private void registerConnectStateReceiver() {
        if (mDeviceConnectReceiver == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            mDeviceConnectReceiver = new DeviceConnectReceiver();
            registerReceiver(mDeviceConnectReceiver, filter);
        }
    }

    private void unRegisterConnectStateReceiver() {
        if (mDeviceConnectReceiver != null) {
            unregisterReceiver(mDeviceConnectReceiver);
            mDeviceConnectReceiver = null;
        }
    }

    private void requestProfileConnectionState() {

        int a2dp = mBtAdapter.getProfileConnectionState(BluetoothProfile.A2DP);
        int headset = mBtAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
        int health = mBtAdapter.getProfileConnectionState(BluetoothProfile.HEALTH);
        //据是否有连接获取已连接的设备
        int flag = -1;
        if (a2dp == BluetoothProfile.A2DP) {
            flag = a2dp;
        } else if (headset == BluetoothProfile.HEADSET) {
            flag = headset;
        } else if (health == BluetoothProfile.HEALTH) {
            flag = health;
        }

        if (flag != -1) {
            ProxyListener mProxyListener = new ProxyListener();
            mBtAdapter.getProfileProxy(this, mProxyListener, BluetoothProfile.A2DP);
        }
    }

    private class ProxyListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {

            if (proxy != null) {
                List<BluetoothDevice> cd = proxy.getConnectedDevices();
                if (cd != null) {
                    for (int i = 0; i < cd.size(); i++) {
                        BluetoothDevice device = cd.get(i);
                        int connectState = device.getBondState();
                        if (BluetoothDevice.BOND_NONE == connectState) {// 未配对
                        } else if (BluetoothDevice.BOND_BONDED == connectState) {//配对
                            checkSCOEnable();
                            break;
                        }
                    }
                }
                mBtAdapter.closeProfileProxy(profile, proxy);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
        }

    }

    private synchronized void checkSCOEnable() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            // Start listening for button presses
            mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), RemoteControlReceiver.class.getName()));

            if (!mAudioManager.isBluetoothScoAvailableOffCall()) {
                setTipText(R.string.tip_sys_nosupport_bluetooth_recording);
                return;
            }
            setTipText(R.string.tip_sys_support_bluetooth_recording);
        }

        startBluetoothSCO();
    }

    private void startBluetoothSCO() {
        if (mAudioManager != null) {
            if (mAudioManager.isBluetoothScoOn()) {
                mAudioManager.stopBluetoothSco();
            }
//            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.startBluetoothSco();

            if (mSCOReceiver == null) {
                mSCOReceiver = new SCOReceiver();
                IntentFilter filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);

                registerReceiver(mSCOReceiver, filter);
            }
        }
    }

    private void stopBluetoothSCO() {
        if (mAudioManager != null && mAudioManager.isBluetoothScoOn()) {
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.stopBluetoothSco();
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
        }

        if (mSCOReceiver != null) {
            unregisterReceiver(mSCOReceiver);
            mSCOReceiver = null;
        }
    }

    private class SCOReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {

                mAudioManager.setBluetoothScoOn(true);
                unregisterReceiver(this);
                mSCOReceiver = null;

            } else if (AudioManager.SCO_AUDIO_STATE_DISCONNECTED == state) {
                if (!mHandler.hasMessages(HANDLER_SCO_CHANGE)) {
                    mHandler.sendEmptyMessageDelayed(HANDLER_SCO_CHANGE, 1000);
                }
            }
        }
    }

    private class BluetoothReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {

            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                if (!mHandler.hasMessages(HANDLER_START_RECORDING)) {
                    mHandler.sendEmptyMessageDelayed(HANDLER_START_RECORDING, 500);
                }
            }
        }
    }

    //record
    private void startRecording() {
        if (mRecorder != null) {
            setTipText(R.string.tip_cannot_start_recording);
            return;
        }

        createRecordFile();

        setTipText(R.string.tip_start_recording);

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioSamplingRate(mSamplingRate);
        try {
            mRecorder.prepare();
        } catch (Exception e) {
            // TODO: handle exception
            Log.i(TAG, "prepare() failed!");
        }

        mRecorder.start();//开始录音
    }

    private void stopRecording() {
        if (mRecorder == null) {
            setTipText(R.string.tip_cannot_stop_recording);
            return;
        }
        setTipText(R.string.tip_stop_recording);
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
    }

    /**
     * 播放按钮状态改变
     */
    private void setPlayRecordingStatus() {
        if (mPlayer != null) {
            Drawable releaseRecording = getResources().getDrawable(R.drawable.ic_release_recording);
            releaseRecording.setBounds(0, 0, releaseRecording.getMinimumWidth(), releaseRecording.getMinimumHeight());
            mTvPlayRecording.setCompoundDrawables(null, releaseRecording, null, null);
            mTvPlayRecording.setText(R.string.txt_release_sound_recording);
        } else {
            Drawable playRecording = getResources().getDrawable(R.drawable.ic_play_recording);
            playRecording.setBounds(0, 0, playRecording.getMinimumWidth(), playRecording.getMinimumHeight());
            mTvPlayRecording.setCompoundDrawables(null, playRecording, null, null);
            mTvPlayRecording.setText(R.string.txt_play_sound_recording);
        }
    }

    //录音按钮状态改变
    private void setStartRecordingStatus() {
        if (mRecorder != null) {
            Drawable releaseRecording = getResources().getDrawable(R.drawable.ic_stop_recording);
            releaseRecording.setBounds(0, 0, releaseRecording.getMinimumWidth(), releaseRecording.getMinimumHeight());
            mTvStartRecording.setCompoundDrawables(null, releaseRecording, null, null);
            mTvStartRecording.setText(R.string.txt_stop_recording);
        } else {
            Drawable playRecording = getResources().getDrawable(R.drawable.ic_start_recording);
            playRecording.setBounds(0, 0, playRecording.getMinimumWidth(), playRecording.getMinimumHeight());
            mTvStartRecording.setCompoundDrawables(null, playRecording, null, null);
            mTvStartRecording.setText(R.string.txt_start_recording);
        }
    }

    class PlayRecordingListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            togglePlayRecording();
        }
    }

    private void togglePlayRecording() {

        if (TextUtils.isEmpty(mFileName)) {
            setTipText(R.string.tip_no_file_to_play);
            return;
        }

        if (mRecorder != null) {
            setTipText(R.string.tip_cannot_start_recording);
            return;
        }
        if (mPlayer == null) {
            stopBluetoothSCO();
            playRecording();
        } else {
            startBluetoothSCO();
            releaseRecording();
        }
        setPlayRecordingStatus();
    }

    class StartRecordingListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            toggleStartRecording();
        }
    }

    private void toggleStartRecording() {
        if (mPlayer != null) {
            setTipText(R.string.tip_cannot_start_sound_recording);
            return;
        }

        if (mRecorder == null) {
            startBluetoothSCO();
            stopPlaySoundRecording();
            startRecording();
        } else {
            stopRecording();
        }
        setStartRecordingStatus();
    }

    private void playRecording() {
        if (mPlayer != null) {
            setTipText(R.string.tip_cannot_start_sound_recording);
            return;
        }
        setTipText(R.string.tip_start_sound_recording);
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                setTipText(R.string.tip_complete_sound_recording);
                stopPlaySoundRecording();
            }
        });
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (NullPointerException e) {
            setTipText(R.string.tip_no_file_to_play);
        } catch (IOException e) {
            Log.e(TAG, "播放失败");
        }
    }

    private void releaseRecording() {
        if (mPlayer == null) {
            setTipText(R.string.tip_cannot_stop_sound_recording);
            return;
        }
        setTipText(R.string.tip_stop_sound_recording);
        stopPlaySoundRecording();
    }

    private void stopPlaySoundRecording() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        setPlayRecordingStatus();
    }

    @Override
    public void onClick(View view) {
        if (R.id.btn_8000 == view.getId()) {
            mSamplingRate = 8000;
            mBtn8000.setTextColor(Color.RED);
            mBtn16000.setTextColor(Color.BLACK);
        } else if (R.id.btn_16000 == view.getId()) {
            mSamplingRate = 16000;
            mBtn8000.setTextColor(Color.BLACK);
            mBtn16000.setTextColor(Color.RED);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegisterVolumeReceiver();
        stopBluetoothSCO();
        unRegisterConnectStateReceiver();

        BaseHandlerOperate.getBaseHandlerOperate().removeKeyData(MainActivity.class);
    }

    public TitleBar getTopTitleBar() {
        if (titleBarUtil == null) {
            titleBarUtil = new TitleBarImpl(this);
        }
        return titleBarUtil.getTopTitleBar();
    }

    BaseHandlerUpDate mBaseHandlerUpDate = new BaseHandlerUpDate() {
        @Override
        public void handleMessage(Message msg) {
            if (!mHandler.hasMessages(HANDLER_START_RECORDING)) {
                mHandler.sendEmptyMessageDelayed(HANDLER_START_RECORDING, 500);
            }
        }
    };
}
