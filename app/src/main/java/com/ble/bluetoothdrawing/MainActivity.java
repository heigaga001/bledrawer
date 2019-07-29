package com.ble.bluetoothdrawing;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private int SCREEN_W;
    private int SCREEN_H;
    private int Pen = 1;
    private int Eraser = 2;

    private FrameLayout canvasView;

    private MyView myView;
    private TextView rubber;
    private TextView right_action;
    private TextView upView;
    private TextView downView;
    private TextView colorView;
    private TextView connect;
    private TextView pressView;
    private TextView batteryView;
    private TextView receiveView;


    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "UartServiceActivity";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;

    private int write = 0;

    private int[] colors = new int[]{
            Color.WHITE,
            Color.GREEN
    };
    private int [] colorIndex = new int[]{
            0,
            0,
            0,
            0,
            0
    };

    private String msg = "receive:";
    private List<String> msgs = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        canvasView = findViewById(R.id.canvas);
        right_action = findViewById(R.id.right_action);
        upView = findViewById(R.id.up);
        downView = findViewById(R.id.down);
        colorView= findViewById(R.id.change);
        connect = findViewById(R.id.btn_select);
        connect.setTag(new Integer(1));
        receiveView = findViewById(R.id.receivemsg);

        receiveView.setText(msg);
        findViewById(R.id.clear_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myView.clearAll();
            }
        });
        receiveView.setVisibility(View.GONE);
        pressView = findViewById(R.id.value);
        batteryView = findViewById(R.id.battery);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Integer tag = (Integer)v.getTag();
                if(tag == 1){
                    if (!mBtAdapter.isEnabled()) {
                        Log.i(TAG, "onClick - BT not enabled yet");
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                        return;
                    }
                    Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                    startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                }else if(tag == 2){
                    if (mDevice!=null){
                        mService.disconnect();
                    }
                }else{

                }
            }
        });
        service_init();

        rubber = findViewById(R.id.rubber);
//        rubber.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(myView.getMode()==Pen){
//                    rubber.setBackgroundColor(Color.GREEN);
//                    myView.setMode(Eraser);
//                }else{
//                    rubber.setBackgroundResource(R.drawable.shape_label_orange);
//                    myView.setMode(Pen);
//                }
//
//            }
//        });
        //view加载完成时回调
        canvasView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // TODO Auto-generated method stub
                if(myView==null){
                    SCREEN_W = canvasView.getMeasuredWidth();
                    SCREEN_H = canvasView.getMeasuredHeight();
                    myView = new MyView(MainActivity.this);
                    canvasView.addView(myView,SCREEN_W,SCREEN_H);
                }
            }
        });

        mBtAdapter=BluetoothAdapter.getDefaultAdapter();
        boolean enabled =  mBtAdapter.isEnabled();
        if(!enabled){
            mBtAdapter.enable();
        }


        boolean grant = PermissionHelper.isPermissionGranted(this, Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION);
        if(!grant){
            PermissionHelper.runOnPermissionGranted(this, new Runnable() {
                @Override
                public void run() {
                // 权限通过
                Toast.makeText(MainActivity.this, "已通过", Toast.LENGTH_SHORT).show();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    // 权限不通过
                Toast.makeText(MainActivity.this, "未通过", Toast.LENGTH_SHORT).show();
                }
            },Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION);
        }else{
//            Toast.makeText(this, "蓝牙权限" + (grant ? "已允许" : "未允许"), Toast.LENGTH_SHORT).show();
        }


    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String address = "";
                        if(mDevice!=null){
                            address = mDevice.getAddress();
                        }
                        connect.setText("connected to "+address);
                        connect.setTag(new Integer(2));
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        connect.setText("Connect one device");
                        connect.setTag(new Integer(1));
                        mState = UART_PROFILE_DISCONNECTED;
//                        mService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                final byte[] battery =  intent.getByteArrayExtra(UartService.EXTRA_DATA_BATTERY);
//                if(txValue!=null){
//                    String receive = "";
//                    for(int i=0;i<txValue.length;i++){
//                        int v = txValue[i] & 0xFF;
//                        String hv = Integer.toHexString(v);
//                        receive = receive+hv;
//                        if(i!=txValue.length-1){
//                            receive = receive+"-";
//                        }
//                    }
//                    msgs.add(receive);
//                    if(msgs.size()>2){
//                        msgs.remove(0);
//                    }
//                    msg = "";
//                    for(int i=0;i<msgs.size();i++){
//                        msg = msg+msgs.get(i);
//                        msg = msg+"   -->>   ";
//                    }
////                    msg = msg+"\n"+receive;
//
////                    Toast.makeText(getApplicationContext(),"",Toast.LENGTH_LONG).show();
//                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        receiveView.setText(msg);
                      Log.d(TAG,"-------receive"+ Arrays.toString(txValue)+"::"+Arrays.toString(battery));
                      if(txValue==null&&battery == null){
                          return;
                      }
                      if(battery!=null){
                          int batteryValue  = battery[0];
                          batteryView.setText("电量："+batteryValue+"%");
                          return;
                      }

                      if(txValue.length >=3){
                          byte cValue = txValue[2];
                          int up = get(cValue,0);
                          int down = get(cValue,1);
                          int right = get(cValue,2);
                          int clear = get(cValue,3);
                          int colorSelect = get(cValue,4);
                          int writeState = ((txValue[0] & 0xFF)>>7);
                          int press = ((((txValue[0] & 0xFF)<<9) & 0xFFFF)>>1) + (txValue[1] & 0xFF);
                          colorIndex[0] = colorIndex[0]+up;
                          colorIndex[1] = colorIndex[1]+down;
                          colorIndex[2] = colorIndex[2]+right;
                          colorIndex[3] = colorIndex[3]+clear;
                          colorIndex[4] = colorIndex[4]+colorSelect;
                          upView.setBackgroundColor(colors[colorIndex[0]%colors.length]);
                          downView.setBackgroundColor(colors[colorIndex[1]%colors.length]);
                          right_action.setBackgroundColor(colors[colorIndex[2]%colors.length]);
                          rubber.setBackgroundColor(colors[colorIndex[3]%colors.length]);
                          colorView.setBackgroundColor(colors[colorIndex[4]%colors.length]);
                          pressView.setText("当前压感值："+press);
                          int batteryValue  = txValue[3];
                          write = writeState;
                          batteryView.setText("电量："+batteryValue+"%");
                          if(colorIndex[3]%colors.length == 0){
                              myView.setMode(Pen);
                          }else{
                              myView.setMode(Eraser);
                          }

//                            upView.setBackgroundResource(colorIndex[0] == 1?R.drawable.shape_label_red:R.drawable.shape_label_orange);
//                          right_action.setBackgroundResource(colorIndex[2] == 1?R.drawable.shape_label_red:R.drawable.shape_label_orange);
//                           rubber.setBackgroundResource(colorIndex[3] == 1?R.drawable.shape_label_red:R.drawable.shape_label_orange);
//
//                          downView.setBackgroundResource(colorIndex[1] == 1?R.drawable.shape_label_red:R.drawable.shape_label_orange);
//                          colorView.setBackgroundResource(colorIndex[4] == 1?R.drawable.shape_label_red:R.drawable.shape_label_orange);

                          Log.d(TAG,"up:"+up+",down:"+down+",right:"+right+",clear:"+clear+"color:"+colorSelect+",write:"+writeState+",press:"+press+",barttery:");
                      }

                    }
                });
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }

            if (action.equals(UartService.DEVICE_SUPPORT_UART)){


                msgs.add("成功订阅");
                msg = "";
                for(int i=0;i<msgs.size();i++){
                    msg = msg+msgs.get(i);
                    msg = msg+"   -->>   ";
                }
//                    msg = msg+"\n"+receive;
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        receiveView.setText(msg);
//                    }
//                });

            }

            if (action.equals(UartService.DEVICE_SUPPORT_UUIDS)){
                final String msg1 = intent.getStringExtra("test");

                msgs.add(msg1);
                msg = "";
                for(int i=0;i<msgs.size();i++){
                    msg = msg+msgs.get(i);
                    msg = msg+"   -->>   ";
                }


//                    msg = msg+"\n"+receive;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        showMessage(msg1);
                        receiveView.setText(msg);
                    }
                });
            }


        }
    };

    public static int get(int num, int index)
    {
        return (num & (0x1 << index)) >> index;
    }

    private void showMessage(String msg){
        Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(UartService.DEVICE_SUPPORT_UART);
        intentFilter.addAction(UartService.DEVICE_SUPPORT_UUIDS);
        return intentFilter;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    connect.setText(mDevice.getName()+ " - connecting");
                    connect.setTag(new Integer(3));
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    class MyView extends View {
        private int mMode = 1;
        private Bitmap mBitmap;
        private Canvas mCanvas;
        private Paint mEraserPaint;
        private Paint mPaint;
        private Path mPath;
        private float mX, mY;
        private static final float TOUCH_TOLERANCE = 4;

        public MyView(Context context) {
            super(context);
            setFocusable(true);
            initPaint();
        }


        //设置绘制模式是“画笔”还是“橡皮擦”
        public void setMode(int mode){
            this.mMode = mode;
        }

        public int getMode(){
            return this.mMode;
        }

        private void initPaint() {
            //画笔
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setColor(Color.BLACK);
            mPaint.setStrokeWidth(10);
            //橡皮擦
            mEraserPaint = new Paint();
            mEraserPaint.setAlpha(0);
            //这个属性是设置paint为橡皮擦重中之重
            //这是重点
            //下面这句代码是橡皮擦设置的重点
            mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            //上面这句代码是橡皮擦设置的重点（重要的事是不是一定要说三遍）
            mEraserPaint.setAntiAlias(true);
            mEraserPaint.setDither(true);
            mEraserPaint.setStyle(Paint.Style.STROKE);
            mEraserPaint.setStrokeJoin(Paint.Join.ROUND);
            mEraserPaint.setStrokeWidth(40);

            mPath = new Path();

            mBitmap = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        public void clearAll(){
            mPath = new Path();

            mBitmap = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mBitmap != null) {
                canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            }
            super.onDraw(canvas);
        }

        private void touch_start(float x, float y) {

            mPath.reset();
            mPath.moveTo(x, y);
            mX = x;
            mY = y;
            //如果是“画笔”模式就用mPaint画笔进行绘制
            if (mMode == Pen) {
                mCanvas.drawPath(mPath, mPaint);
            }
            //如果是“橡皮擦”模式就用mEraserPaint画笔进行绘制
            if (mMode == Eraser) {
                mCanvas.drawPath(mPath, mEraserPaint);
            }

        }

        private void touch_move(float x, float y) {

            float dx = Math.abs(x - mX);
            float dy = Math.abs(y - mY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                mX = x;
                mY = y;
                if (mMode == Pen) {
                    mCanvas.drawPath(mPath, mPaint);
                }
                if (mMode == Eraser) {
                    mCanvas.drawPath(mPath, mEraserPaint);
                }
            }
        }


        private void touch_up() {

            mPath.lineTo(mX, mY);
            if (mMode == Pen) {
                mCanvas.drawPath(mPath, mPaint);
            }
            if (mMode == Eraser) {
                mCanvas.drawPath(mPath, mEraserPaint);
            }
        }
        private boolean startPaint = false;
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if(write ==1){
                        startPaint = true;
                        touch_start(x, y);
                        invalidate();
                    }

                    break;
                case MotionEvent.ACTION_MOVE:
                    if(write == 1){
                        if(startPaint){
                            touch_move(x, y);
                        }else{
                            startPaint = true;
                            touch_start(x, y);
                        }
                        invalidate();
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    startPaint = false;
                    if(write == 1) {
                        touch_up();
                        invalidate();
                    }

                    break;
            }
            return true;
        }
    }
}
