package com.android.wifiiperf;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "WifiActivity";
    private static final int IPERF_ERROR = 1;
    private static final int IPERF_SCCESS = 2;
    private TextView mAddress;
    private TextView mShroughput;
    private TextView mSignalStrength;
    private WifiManager mWm;
    private String iperfreply;
    /*** 1. 拷贝iperf到该目录下*/
    private static final String IPERF_PATH = "/data/data/com.android.wifiiperf/iperf";
    /*** 2. 在Android应用中执行iperf命令*/
    private static String iperfClientCmd = IPERF_PATH + " -c 172.19.0.179 -p 5001 -t 60 -P 1";
    private static final String iperfServiceCmd = IPERF_PATH + " -s &";
    private static String curIperfCmd = iperfServiceCmd;
    private boolean IPERF_OK = false;
    private Switch mSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        File file = new File(IPERF_PATH);
        Log.i(TAG,"file.exists(): "+file.exists());
        if(!file.exists()){
            copyiperf();
        }
    }

    private void initView() {
        findViewById(R.id.btn_wifi_address).setOnClickListener(this);
        findViewById(R.id.btn_wifi_throughput).setOnClickListener(this);
        findViewById(R.id.btn_wifi_signal_strength).setOnClickListener(this);

        mAddress = (TextView) findViewById(R.id.wifi_address);
        mShroughput = (TextView) findViewById(R.id.wifi_throughput_show);
        mSignalStrength = (TextView) findViewById(R.id.wifi_signal_strength);

        mWm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mSwitch = (Switch) findViewById(R.id.wifi_switch);
        mSwitch.setChecked(true);
        mSwitch.setOnCheckedChangeListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_wifi_address://获取wifi地址
                mAddress.setText(getLocalIpAddress());
                Log.i(TAG, "btn_wifi_address: " + getLocalIpAddress());
                break;
            case R.id.btn_wifi_throughput://获取wifi吞吐量
                iperfClientCmd = IPERF_PATH + " -c "+getLocalIpAddress()+" -p 5001 -t 60 -P 1";
                sercomfun(curIperfCmd);
                break;
            case R.id.btn_wifi_signal_strength://获取wifi信号强度
                mSignalStrength.setText(getSignalStrength());
                Log.i(TAG, "btn_wifi_signal_strength: " + getSignalStrength());
                break;
        }
    }


    /**
     * 获取wifi地址
     */
    private String getLocalIpAddress() {
        int paramInt = 0;
        WifiInfo info = mWm.getConnectionInfo();
        if (info.getBSSID() == null) {
            return "请连接再试";
        } else {
            paramInt = info.getIpAddress();
            Log.i(TAG, "paramInt: " + paramInt+"0xFF&paramInt"+(0xFF & paramInt));
            return new StringBuffer()
                    .append(0xFF & paramInt).append(".")
                    .append(0xFF & paramInt >> 8).append(".")
                    .append(0xFF & paramInt >> 16).append(".")
                    .append(0xFF & paramInt >> 24).toString();
        }
    }

    /**
     * Wifi的连接速度及信号强度
     */
    public String getSignalStrength() {
        int strength = 0;
        WifiInfo info = mWm.getConnectionInfo();
        if (info.getBSSID() != null) {
            // 链接信号强度，5为获取的信号强度值在5以内
            strength = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            // int speed = info.getLinkSpeed(); // 链接速度
            // String units = WifiInfo.LINK_SPEED_UNITS; // 链接速度单位
            // String ssid = info.getSSID(); // Wifi源名称
        }
        return strength + "";
    }


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case IPERF_ERROR:
                    mShroughput.setText((String)msg.obj);
                    break;
                case IPERF_SCCESS:
                    mShroughput.setText((String)msg.obj);
                    break;
            }
        }
    };

    /**
     * 1. 拷贝iperf到该目录下
     */
    public void copyiperf() {
        File localfile;
        Process p;
        try {
            localfile = new File(IPERF_PATH);
            p = Runtime.getRuntime().exec("chmod 777 " + localfile.getAbsolutePath());
            InputStream localInputStream = getAssets().open("iperf");
            Log.i(TAG,"chmod 777 " + localfile.getAbsolutePath());
            FileOutputStream localFileOutputStream = new FileOutputStream(localfile.getAbsolutePath());
            FileChannel fc = localFileOutputStream.getChannel();
            FileLock lock = fc.tryLock(); //给文件设置独占锁定
            if (lock == null) {
                Toast.makeText(this,"has been locked !",Toast.LENGTH_SHORT).show();
                return;
            } else {
                FileOutputStream fos = new FileOutputStream(new File(IPERF_PATH));
                byte[] buffer = new byte[1024];
                int byteCount = 0;
                while ((byteCount = localInputStream.read(buffer)) != -1) {// 循环从输入流读取
                    // buffer字节
                    fos.write(buffer, 0, byteCount);// 将读取的输入流写入到输出流
                    Log.i(TAG, "byteCount: "+byteCount);
                }
                fos.flush();// 刷新缓冲区
                localInputStream.close();
                fos.close();

            }
            //两次才能确保开启权限成功
            p = Runtime.getRuntime().exec("chmod 777 " + localfile.getAbsolutePath());
            lock.release();
            p.destroy();
            Log.i(TAG, "the iperf file is ready");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 2. 在Android应用中执行iperf命令
     */
    private void sercomfun(final String cmd) {
        Log.i(TAG, "sercomfun = " + cmd);
        Thread lthread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String errorreply = "";
                    CommandHelper.DEFAULT_TIMEOUT = 150000;
                    CommandResult result = CommandHelper.exec(cmd);
                    if (result != null) {
                        //start to connect the service
                        if (result.getError() != null) {
                            errorreply = result.getError();
                            Message m = new Message();
                            m.obj = errorreply;
                            m.what = IPERF_ERROR;
                            handler.sendMessage(m);
                            Log.i(TAG,"Error:" + errorreply);
                        }
                        if (result.getOutput() != null) {
                            iperfreply = getThroughput(result.getOutput());
                            IPERF_OK = true;
                            Message m = new Message();
                            m.obj = iperfreply;
                            m.what = IPERF_SCCESS;
                            handler.sendMessage(m);
                            Log.i(TAG,"Output:" + iperfreply);
                        }
                        Log.i(TAG,"result.getExitValue(): "+result.getExitValue());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        lthread.start();
    }

    /**
     * 从获取到的吞吐量信息中截取需要的信息，如：
     * 0.0-10.0 sec  27.5 MBytes  23.0 Mbits/sec
     */
    private String getThroughput(String str) {
        String regx = "0.0-.+?/sec";
        String result = "";
        Matcher matcher = Pattern.compile(regx).matcher(str);
        Log.i(TAG,"matcher regx : "+regx+" is "+matcher.matches());
        if(matcher.find()){
            Log.i(TAG,"group: "+matcher.group());
            result = matcher.group();
        }
        return result;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(buttonView.getId() == R.id.wifi_switch){
            curIperfCmd = (isChecked?iperfServiceCmd
                    :(IPERF_PATH + " -c "+getLocalIpAddress()+" -p 5001 -t 60 -P 1"));
            Log.i(TAG,"isChecked: "+isChecked+", curIperfCmd: "+curIperfCmd);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }
}
