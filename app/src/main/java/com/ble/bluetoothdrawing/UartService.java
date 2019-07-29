
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ble.bluetoothdrawing;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class UartService extends Service {
    private final static String TAG = UartService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.nordicsemi.nrfUART.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.nordicsemi.nrfUART.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.nordicsemi.nrfUART.EXTRA_DATA";
    public final static String EXTRA_DATA_BATTERY =
            "com.nordicsemi.nrfUART.EXTRA_DATA_BATTERY";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String DEVICE_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_SUPPORT_UART";
    public final static String DEVICE_SUPPORT_UUIDS =
            "com.nordicsemi.nrfUART.DEVICE_SUPPORT_UUIDS";

    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_NOTIFY_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_BATTERY_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("00001523-1212-efde-1523-785feabcd123");
    public static final UUID RX_CHAR_UUID = UUID.fromString("00001525-1212-efde-1523-785feabcd123");
    public static final UUID TX_CHAR_UUID = UUID.fromString("00001524-1212-efde-1523-785feabcd123");
    
   
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.i(TAG, "onConnectionStateChange:"+status+"::"+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;

//                //阅读连接的远程设备的RSSI。
//                gatt.readRemoteRssi();
//                // 发现远程设备提供的服务及其特性和描述符
//                gatt.discoverServices();

                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.w(TAG, "mBluetoothGatt = " + mBluetoothGatt );
                Log.w(TAG, "mBluetoothGatt2 = " + gatt );
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged received: " + characteristic.getUuid());
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            Log.d(TAG, "onPhyUpdate: ");
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            Log.d(TAG, "onPhyRead: ");
            super.onPhyRead(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: ");
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead: ");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite: ");
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.d(TAG, "onReliableWriteCompleted: ");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.d(TAG, "onReadRemoteRssi: ");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG, "onMtuChanged: ");
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,final String msg) {
        final Intent intent = new Intent(action);
        intent.putExtra("test",msg);
        showMessage(msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is handling for the notification on TX Character of NUS service

        Log.d(TAG, String.format("Received TX: %s", Arrays.toString(characteristic.getValue()) )+":::"+characteristic.getUuid());
        if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
        	
           // Log.d(TAG, String.format("Received TX: %d",characteristic.getValue() ));
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
        } else if(TX_POWER_LEVEL_UUID.equals(characteristic.getUuid())||TX_POWER_NOTIFY_UUID.equals(characteristic.getUuid())){
            intent.putExtra(EXTRA_DATA_BATTERY, characteristic.getValue());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        UartService getService() {
            return UartService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
       // mBluetoothGatt.close();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.w(TAG, "mBluetoothGatt closed");
        mBluetoothDeviceAddress = null;
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *

    */
    
    /**
     * Enable Notification on TX characteristic
     *
     * @return 
     */
    public void enableTXNotification()
    {
//        getBattery();
        List<BluetoothGattService> list= getSupportedGattServices();
        String uuids = "";
        for(BluetoothGattService item:list){
            uuids = uuids+item.getUuid().toString()+"\n";
            Log.d(TAG,item.getUuid().toString()+":"+item.getType());
        }
        broadcastUpdate(DEVICE_SUPPORT_UUIDS,uuids);
    	BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
    	if (RxService == null) {
            showMessage("Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        List<BluetoothGattCharacteristic> ccc = RxService.getCharacteristics();
        for(BluetoothGattCharacteristic item:ccc){
            Log.d(TAG,"tx:"+item.getUuid().toString()+":");
        }
    	BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            showMessage("Tx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar,true);
        Log.d(TAG,"cccd::"+TxChar.getDescriptors().size()+":");
        for(BluetoothGattDescriptor des:TxChar.getDescriptors()){
            Log.d(TAG,"cccd::"+des.getUuid().toString()+":");
        }
        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        boolean status =  mBluetoothGatt.writeDescriptor(descriptor);
        Log.d(TAG, "write TXchar - status=" + status);
        if(status){
            broadcastUpdate(DEVICE_SUPPORT_UART);
//            new Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    getBattery();
//                }
//            },2000);
        }
//        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//        status =  mBluetoothGatt.writeDescriptor(descriptor);
//        Log.d(TAG, "write RXchar - status=" + status);
    }

    private Runnable delay = new Runnable() {
        @Override
        public void run() {
          enableTXIndication();
        }
    };

    private Handler handler = new Handler();

    public void enableTXIndication()
    {
    	/*
    	if (mBluetoothGatt == null) {
    		showMessage("mBluetoothGatt null" + mBluetoothGatt);
    		broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
    		return;
    	}
    		*/

        List<BluetoothGattService> list= getSupportedGattServices();
        String uuids = "";
        for(BluetoothGattService item:list){
            uuids = uuids+item.getUuid().toString()+"\n";
            Log.d(TAG,item.getUuid().toString()+":"+item.getType());
        }
        broadcastUpdate(DEVICE_SUPPORT_UUIDS,uuids);
        BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            showMessage("Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        List<BluetoothGattCharacteristic> ccc = RxService.getCharacteristics();
        for(BluetoothGattCharacteristic item:ccc){
            Log.d(TAG,"tx:"+item.getUuid().toString()+":");
        }
        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            showMessage("Tx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar,true);
        for(BluetoothGattDescriptor des:TxChar.getDescriptors()){
            Log.d(TAG,"cccd::"+des.getUuid().toString()+":");
        }
        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        boolean status =  mBluetoothGatt.writeDescriptor(descriptor);
        if(!status){
            handler.removeCallbacks(delay);
            handler.postDelayed(delay,1000);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    enableTXIndication();
                }
            },1000);
        }else{
            broadcastUpdate(DEVICE_SUPPORT_UART);
//            Toast.makeText(getApplicationContext(), "消息订阅成功！！！", Toast.LENGTH_SHORT).show();
            handler.removeCallbacks(delay);
        }
        Log.d(TAG, "write RXchar - status=" + status);

//        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//        status =  mBluetoothGatt.writeDescriptor(descriptor);
//        Log.d(TAG, "write RXchar - status=" + status);
    }

    public void writeRXCharacteristic(byte[] value)
    {
    
    	
    	BluetoothGattService RxService = mBluetoothGatt.getService(RX_SERVICE_UUID);
    	showMessage("mBluetoothGatt null"+ mBluetoothGatt);
    	if (RxService == null) {
            showMessage("Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
    	BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            showMessage("Rx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        RxChar.setValue(value);
    	boolean status = mBluetoothGatt.writeCharacteristic(RxChar);
    	
        Log.d(TAG, "write TXchar - status=" + status);  
    }
    
    private void showMessage(String msg) {
//        Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_LONG).show();
        Log.e(TAG, msg);
    }
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public int getBattery(){
        List<BluetoothGattService> list= getSupportedGattServices();
        for(BluetoothGattService item:list){
            Log.d(TAG,item.getUuid().toString()+":"+item.getType());
        }
        BluetoothGattService RxService = mBluetoothGatt.getService(TX_BATTERY_UUID);
        BluetoothGattCharacteristic batteryChar  = RxService.getCharacteristic(TX_POWER_LEVEL_UUID);
        if(batteryChar == null){
            return -1;
        }


        final int charaProp = batteryChar.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//            if (mNotifyCharacteristic != null) {
//                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
//                mNotifyCharacteristic = null;
//            }
            Log.d(TAG,"PROPERTY_READ");
            mBluetoothGatt.readCharacteristic(batteryChar);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//            mNotifyCharacteristic = batteryChar;
            Log.d(TAG,"PROPERTY_NOTIFY");
            mBluetoothGatt.setCharacteristicNotification(batteryChar, true);
        }

//        BluetoothGattCharacteristic batteryChar2 = batteryChar.getDescriptor()
//        List<BluetoothGattCharacteristic> ccc = RxService.getCharacteristics();
//        for(BluetoothGattCharacteristic item:ccc){
//            Log.d(TAG,"getBattery:"+item.getUuid().toString()+":"+Arrays.toString(item.getValue()));
//            for(BluetoothGattDescriptor iii:item.getDescriptors()){
//                Log.d(TAG,"getBattery2:"+iii.getUuid().toString()+":"+Arrays.toString(iii.getValue()));
//            }
//        }
        return -1;
    }
}
