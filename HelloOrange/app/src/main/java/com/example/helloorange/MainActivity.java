package com.example.helloorange;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.os.Build;
import android.os.Bundle;

import android.content.Intent;
import android.os.ParcelUuid;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import static com.example.helloorange.UARTUtil.*;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mGattServer;

    private ArrayList<BluetoothDevice> mConnectedDevices;
    private ArrayAdapter<BluetoothDevice> mConnectedDevicesAdapter;

    private ArrayList<String> mMessageReceived;
    private ArrayAdapter<String> mMessageReceivedAdapter;

    private byte[] storage = hexStringToByteArray("1111");
    ListView mListView1, mListView2;
    TextView mTextView3;
    EditText mEditText1;
    Button mBtn1;

    BluetoothDevice mSelectedDev;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //checkAndRequestPermissions(this);

        mListView1 = (ListView) findViewById(R.id.listView1);
        mListView2 = (ListView) findViewById(R.id.listView2);
        mTextView3 = (TextView) findViewById(R.id.textView3);
        mEditText1 = (EditText) findViewById(R.id.editText1);

        mEditText1.setText("Hello");
        mEditText1.setVisibility(View.INVISIBLE);


        //ListView list = new ListView(this);
        //setContentView(list);

        mConnectedDevices = new ArrayList<BluetoothDevice>();
        mConnectedDevicesAdapter = new ArrayAdapter<BluetoothDevice>(this,
                android.R.layout.simple_list_item_1, mConnectedDevices);
        mListView1.setAdapter(mConnectedDevicesAdapter);


        mListView1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                BluetoothDevice dev = (BluetoothDevice) parent.getAdapter().getItem(position);
                mSelectedDev = dev;
                UpdateSelection();

                String buf = String.format("Selected: %d -> %s (%s)", position, dev.getAddress().toString(), dev.getName().toString());
                Log.d(TAG, buf);

                String buf2 = String.format("%s", mSelectedDev.getName());
                Toast toast = Toast.makeText(getApplicationContext(), buf2, Toast.LENGTH_SHORT);
                toast.show();
            }
        });


        mSelectedDev = null;
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mMessageReceived = new ArrayList<String>();
        mMessageReceivedAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mMessageReceived);

        mListView2.setAdapter(mMessageReceivedAdapter);


        mMessageReceived.add("Ready...");

        mBtn1 = (Button) findViewById(R.id.button1);

        //mBtn1.setVisibility(View.INVISIBLE);
        mBtn1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //mBtn1.setVisibility(View.INVISIBLE);
                // TO last device
                //sendTestMsg();
                //SendMessageToHost("Hello!");

                SendMessageToSelectedDev(mEditText1.getText().toString());
                //SendMessageToHost("Hello !");

            }
        });
        mBtn1.setVisibility(View.INVISIBLE);
    }

    private void UpdateSelection()
    {
        if(mSelectedDev == null) {
            postStatusMessage("GATT Server Ready");
            return;
        }

        String devname = mSelectedDev.getName().toString();
        mTextView3.setText(devname);

        mEditText1.setVisibility(View.VISIBLE);
        mBtn1.setVisibility(View.VISIBLE);
        postStatusMessage("Connected to "+devname);
    }


    protected void onResume() {
        super.onResume();
        /*
         * Make sure bluetooth is enabled
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        /*
         * Check for advertising support.
         */
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, "No Advertising Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);

        // If everything is okay then start
        initServer();
        startAdvertising();
    }

    /*
     * Callback handles events from the framework describing
     * if we were successful in starting the advertisement requests.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Peripheral Advertise Started.");
            postStatusMessage("GATT Server Ready");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "Peripheral Advertise Failed: "+errorCode);
            postStatusMessage("GATT Server Advertise Error "+errorCode);
        }
    };

    private Handler mHandler = new Handler();

    private void postStatusMessage(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setTitle(message);
            }
        });
    }

    /*
     * Create the GATT server instance, attaching all services and
     * characteristics that should be exposed
     */
    private void initServer() {
        BluetoothGattService UART_SERVICE =new BluetoothGattService(UARTProfile.UART_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic TX_READ_CHAR =
                new BluetoothGattCharacteristic(UARTProfile.TX_READ_CHAR,
                        //Read-only characteristic, supports notifications
                        BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ);

        //Descriptor for read notifications
        BluetoothGattDescriptor TX_READ_CHAR_DESC = new BluetoothGattDescriptor(UARTProfile.TX_READ_CHAR_DESC, UARTProfile.DESCRIPTOR_PERMISSION);
        TX_READ_CHAR.addDescriptor(TX_READ_CHAR_DESC);


        BluetoothGattCharacteristic RX_WRITE_CHAR =
                new BluetoothGattCharacteristic(UARTProfile.RX_WRITE_CHAR,
                        //write permissions
                        BluetoothGattCharacteristic.PROPERTY_WRITE , BluetoothGattCharacteristic.PERMISSION_WRITE);


        UART_SERVICE.addCharacteristic(TX_READ_CHAR);
        UART_SERVICE.addCharacteristic(RX_WRITE_CHAR);

        mGattServer.addService(UART_SERVICE);
    }


    /*
     * Initialize the advertiser
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                //.setIncludeDeviceName(true)
                //.addServiceUuid(new ParcelUuid(UARTProfile.UART_SERVICE))
                .build();
        // See https://developer-joe.tistory.com/189

        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }


    private void postDeviceChange(final BluetoothDevice device, final boolean toAdd) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //This will add the item to our list and update the adapter at the same time.
                if (toAdd) {
                    if (mConnectedDevicesAdapter.getPosition(device) < 0){
                        mConnectedDevicesAdapter.add(device);
                    }

                } else {
                    mConnectedDevicesAdapter.remove(device);
                }

            }
        });
    }


    /*
     * Terminate the server and any running callbacks
     */
    private void shutdownServer() {
        //mHandler.removeCallbacks(mNotifyRunnable);

        if (mGattServer == null) return;

        mGattServer.close();
    }

//        private Runnable mNotifyRunnable = new Runnable() {
//            @Override
//            public void run() {
//                mHandler.postDelayed(this, 2000);
//            }
//        };


    /* Callback handles all incoming requests from GATT clients.
     * From connections to read/write requests.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(TAG, "onConnectionStateChange "
                    +UARTProfile.getStatusDescription(status)+" "
                    +UARTProfile.getStateDescription(newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                postDeviceChange(device, true);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                postDeviceChange(device, false);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d("Start", "Our gatt server service was added.");
            super.onServiceAdded(status, service);
        }


        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "READ called onCharacteristicReadRequest " + characteristic.getUuid().toString());

            if (UARTProfile.TX_READ_CHAR.equals(characteristic.getUuid())) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        storage);
            }
        }


        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, "onCharacteristicWriteRequest "+characteristic.getUuid().toString());

            if (UARTProfile.RX_WRITE_CHAR.equals(characteristic.getUuid())) {

                //IMP: Copy the received value to storage
                storage = value;
                if (responseNeeded) {
                    mGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            value);
                    Log.d(TAG, "Received  data on "+characteristic.getUuid().toString());
                    Log.d(TAG, "Received data"+ bytesToHex(value));
                    Log.d(TAG, device.getAddress().toString());
                }

                //IMP: Respond
                //sendOurResponse();

                final String msgstr = new String(value);
                ProcMessage(value);
                final String buf = String.format("%s from %s", msgstr, device.getName().toString());
                //final String buf = String.format("We received : %s \n from %s \nthru %s", msgstr, device.getAddress().toString(), characteristic.getUuid().toString());//new String(value);//String.format("We received data: %s", value);
                //mMessageReceivedAdapter.notifyDataSetChanged();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMessageReceivedAdapter.insert(buf, 0);
                        //Toast.makeText(MainActivity.this, buf , Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }

        //Send notification to all the devices once you write
        private void ProcMessage(byte[] value) {
            //String buf = new String(value);
            for (BluetoothDevice device : mConnectedDevices) {
                BluetoothGattCharacteristic readCharacteristic = mGattServer.getService(UARTProfile.UART_SERVICE)
                        .getCharacteristic(UARTProfile.TX_READ_CHAR);

                byte[] notify_msg = value;
                String hexStorage =  bytesToHex(value);
                Log.d(TAG, "received string = "+bytesToHex(value));

                if(hexStorage.equals("77686F616D69")){ //whoami

                    notify_msg = "I am echo an machine".getBytes();

                }else if(bytesToHex(value).equals("64617465")){ //date
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    Date date = new Date();
                    notify_msg = dateFormat.format(date).getBytes();

                }else{
                    //TODO: Do nothing send what you received. Basically echo
                }
                readCharacteristic.setValue(notify_msg);
                Log.d(TAG, "Sending Notifications"+notify_msg);

                //notifyCharacteristicChanged()
                // -->  A notification or indication is sent to the remote device to signal that the characteristic has been updated.

                boolean is_notified =  mGattServer.notifyCharacteristicChanged(device, readCharacteristic, false);
                Log.d(TAG, "Notifications ="+is_notified);
            }
        }


        @Override
        public void onNotificationSent(BluetoothDevice device, int status)
        {
            Log.d("GattServer", "onNotificationSent");
            super.onNotificationSent(device, status);
        }


        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Log.d("HELLO", "Our gatt server descriptor was read.");
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.d("DONE", "Our gatt server descriptor was read.");
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d("HELLO", "Our gatt server descriptor was written.");
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d("DONE", "Our gatt server descriptor was written.");

            //NOTE: Its important to send response. It expects response else it will disconnect
            if (responseNeeded) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value);

            }

        }

        //end of gatt server
    };

    private void SendMessageToSelectedDev(String msg) {
        if (mSelectedDev==null) return;

        BluetoothGattCharacteristic txChar = mGattServer.getService(UARTProfile.UART_SERVICE)
                .getCharacteristic(UARTProfile.TX_READ_CHAR);

        byte[] notify_msg = msg.getBytes();
        txChar.setValue(notify_msg);

        boolean is_notified =  mGattServer.notifyCharacteristicChanged(mSelectedDev, txChar, false);
        Log.d(TAG, "Notifications ="+is_notified);

        String buf = String.format("Sent to %s..%b: %s", mSelectedDev.getName().toString(), is_notified, msg);
        mMessageReceivedAdapter.insert(buf, 0);


    }


    private void SendMessageToHost(String msg) {
        for (BluetoothDevice device : mConnectedDevices) {

            String dev_last4 = device.getAddress().toString().substring(12);


            if(dev_last4.toLowerCase().equals("81:e0") ) // TODO : find another way
            {

                BluetoothGattCharacteristic txChar = mGattServer.getService(UARTProfile.UART_SERVICE)
                        .getCharacteristic(UARTProfile.TX_READ_CHAR);

                byte[] notify_msg = msg.getBytes();
                txChar.setValue(notify_msg);

                boolean is_notified =  mGattServer.notifyCharacteristicChanged(device, txChar, false);
                Log.d(TAG, "Notifications ="+is_notified);

                String buf = String.format("Sent to %s..%b: %s", device.getName().toString(), is_notified, msg);
                mMessageReceivedAdapter.insert(buf, 0);
            }

        }

    }

    private void sendTestMsg()
    {
        //Log.d(TAG, "aaa");
        for (BluetoothDevice device : mConnectedDevices) {
            String buf;
            buf = String.format("%s %s", device.getName().toString(), device.getAddress().toString());
            Log.d(TAG, buf );
            // S20 --> 74:9E:F5:A7:81:E0
            String dev_last4 = device.getAddress().toString().substring(12);
            if(dev_last4.toLowerCase().equals("81:e0") )
            {
                String buf2 = String.format("Connected with %s (%s)", device.getName().toString(), dev_last4 );
                Log.d(TAG, buf2);
                mMessageReceivedAdapter.insert(buf2, 0);
            }




        }


    }



}