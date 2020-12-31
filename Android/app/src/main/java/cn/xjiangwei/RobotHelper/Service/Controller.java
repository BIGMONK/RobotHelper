package cn.xjiangwei.RobotHelper.Service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.lang.reflect.Method;

import cn.xjiangwei.RobotHelper.Accessibility.HttpServer;
import cn.xjiangwei.RobotHelper.GamePackage.Main;
import cn.xjiangwei.RobotHelper.MainApplication;
import cn.xjiangwei.RobotHelper.R;
import cn.xjiangwei.RobotHelper.Tools.MLog;
import cn.xjiangwei.RobotHelper.Tools.ScreenCaptureUtil;

import static android.os.SystemClock.sleep;


public class Controller extends Service {
    public final static String INTENT_BUTTONID_TAG = "ButtonId";
    public final static int START = 1;
    public final static int END = 2;
    public final static int EXIT = 5;
    public final static int START_HTTPSERVER = 3;
    public final static int END_HTTPSERVER = 4;
    private static final String CHANNEL_ID = "cn.xjiangwei.RobotHelper.channel";
    public final static String ACTION_BUTTON = "com.notification.intent.action.ButtonClick";

    public static HttpServer httpServer;

    public Controller() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public int onStartCommand(Intent intent, int flags, int startId) {
        //处理任务
        return START_STICKY;
    }


    @Override
    public void onCreate() {

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification);


        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null)
            return;

        NotificationChannel channel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            channel = new NotificationChannel(CHANNEL_ID, "xxx", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContent(remoteViews)
                .setSmallIcon(getApplicationInfo().icon)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();


        //注册广播
        ButtonBroadcastReceiver receiver = new ButtonBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BUTTON);
        this.registerReceiver(receiver, intentFilter);

        //设置点击的事件
        Intent startIntent = new Intent(ACTION_BUTTON);
        startIntent.putExtra(INTENT_BUTTONID_TAG, START);
        PendingIntent start = PendingIntent.getBroadcast(this, START, startIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.btn1, start);


        Intent endIntent = new Intent(ACTION_BUTTON);
        endIntent.putExtra(INTENT_BUTTONID_TAG, END);
        PendingIntent end = PendingIntent.getBroadcast(this, END, endIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.btn2, end);


        // 退出程序的点击
        Intent exitIntent = new Intent(ACTION_BUTTON);
        exitIntent.putExtra(INTENT_BUTTONID_TAG, EXIT);
        PendingIntent exit = PendingIntent.getBroadcast(this, EXIT, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.exit, exit);


        //开启http server的点击
        Intent startHttpserverIntent = new Intent(ACTION_BUTTON);
        startHttpserverIntent.putExtra(INTENT_BUTTONID_TAG, START_HTTPSERVER);
        PendingIntent startHttpserver = PendingIntent.getBroadcast(this, START_HTTPSERVER, startHttpserverIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.start_http, startHttpserver);

        //关闭http server的点击
        Intent endHttpServerIntent = new Intent(ACTION_BUTTON);
        endHttpServerIntent.putExtra(INTENT_BUTTONID_TAG, END_HTTPSERVER);
        PendingIntent endHttpServer = PendingIntent.getBroadcast(this, END_HTTPSERVER, endHttpServerIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.end_http, endHttpServer);


        startForeground(101, notification);

    }


    /**
     * 广播监听按钮点击事件
     */
    public class ButtonBroadcastReceiver extends BroadcastReceiver {

        private ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                runtime = new Messenger(iBinder);
                bound = true;

                Message msg = Message.obtain(null, RunTime.MSG_START, 0, 0);
                try {
                    runtime.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                runtime = null;
                bound = false;
            }
        };

        private Messenger runtime = null;

        private boolean bound = false;

        public void collapseStatusBar(Context context) {
            try {
                Object statusBarManager = context.getSystemService("statusbar");

                Method collapse;
                collapse = statusBarManager.getClass().getMethod("collapsePanels");

                collapse.invoke(statusBarManager);
            } catch (Exception localException) {
                localException.printStackTrace();
            }

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_BUTTON)) {
                //通过传递过来的ID判断按钮点击属性或者通过getResultCode()获得相应点击事件
                int buttonId = intent.getIntExtra(INTENT_BUTTONID_TAG, 0);
                switch (buttonId) {
                    case START:

                        collapseStatusBar(context);
                        Intent i = new Intent(MainApplication.getInstance(), RunTime.class);
                        MainApplication.getInstance().bindService(i, connection, Context.BIND_AUTO_CREATE);

                        break;
                    case END:
                        collapseStatusBar(context);
                        if (!bound) return;

                        Message msg = Message.obtain(null, RunTime.MSG_STOP, 0, 0);
                        try {
                            runtime.send(msg);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        MainApplication.getInstance().unbindService(connection);


                        break;
                    case START_HTTPSERVER:
                        if (httpServer == null) {
                            httpServer = new HttpServer();
                        } else {
                            httpServer.start();
                        }
                        cn.xjiangwei.RobotHelper.Tools.Toast.show("HttpServer Start!");

                        break;
                    case END_HTTPSERVER:
                        httpServer.stop();
                        cn.xjiangwei.RobotHelper.Tools.Toast.show("HttpServer Stop!");
                        break;

                    case EXIT:

                        try {
                            runtime.send(Message.obtain(null, RunTime.MSG_STOP, 0, 0));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        System.exit(9);
                        break;
                    default:
                        break;
                }
            }
        }

    }


}

