package com.adafruit.bluefruit.le.connect.app;

import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.app.settings.MqttUartSettingsActivity;
import com.adafruit.bluefruit.le.connect.app.settings.PreferencesFragment;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;
import com.drivewyze.EventReceiver;
import com.drivewyze.GPXLocationProvider;
import com.drivewyze.JDRIVE;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static android.webkit.WebSettings.PluginState.ON;
import static com.adafruit.bluefruit.le.connect.R.id.BTLEText;
import java.util.Random;
//import android.location.LocationManager;

//import org.json.JSONObject;
//import java.text.ParseException;

public class UartActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Configuration
    private final static boolean kUseColorsForData = true;
    public final static int kDefaultMaxPacketsToPaintAsText = 500;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_MqttSettingsActivity = 1;

    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";
    private final static String kPreferences_timestampDisplayMode = "timestampdisplaymode";

    // Colors
    private int mTxColor;
    private int mRxColor;
    private int mInfoColor = Color.parseColor("#F21625");

    // UI
    private EditText mBufferTextView;
    private ListView mBufferListView;
    private TimestampListAdapter mBufferListAdapter;
    private TextView BTLETextView;

//    private EditText mSendEditText;
    private MenuItem mMqttMenuItem;
    private Handler mMqttMenuItemAnimationHandler;
    private TextView mSentBytesTextView;
    private TextView mReceivedBytesTextView;

    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes an arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                updateTextDataUI();
                // Log.d(TAG, "updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;
    int sleepyZoneCount=0;
    // Data
    private boolean mShowDataInHexFormat;
    private boolean mIsTimestampDisplayMode;
    private boolean mIsEchoEnabled;
    private boolean mIsEolEnabled;

    private volatile SpannableStringBuilder mTextSpanBuffer;
    private volatile ArrayList<UartDataChunk> mDataBuffer;
    private volatile int mSentBytes;
    private volatile int mReceivedBytes;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    private int maxPacketsToPaintAsText;

    /////LOCATION

    private LocationManager locationManager;


    public class LatLonSleep {
        double lat;
        double lon;
        int sleepStatus;

        public LatLonSleep(double lat, double lon,int status) {
            this.lat = lat;
            this.lon = lon;
            this.sleepStatus=status;
        }

        public double getLat(){
            return this.lat;
        }

        public double getLon(){
            return this.lon;
        }

        public int getStatus(){
            return this.sleepStatus;
        }
    }

    final List<LatLonSleep> latLonSleepList = new ArrayList<LatLonSleep>();
///////////////DRIVE
    public String getUIName(String evt) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject json_o = (JSONObject)parser.parse(evt);
            Object o = json_o.get("name");
            if (o != null) {
                return o.toString();
            }
        } catch(ParseException pe) {
            Log.e(TAG, pe.getMessage());
        }
        return "Unknown UI";
    }

    public String getLoc(String evt) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject json_o = (JSONObject)parser.parse(evt);
            Object o = json_o.get("loc");
            if (o != null) {
                return o.toString();
            }
        } catch(ParseException pe) {
            Log.e(TAG, pe.getMessage());
        }
        return "Unknown Loc";
    }

    private void HookupStartButton() {
        final Button startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Log.v(TAG, "initializing drive");
                Log.v(TAG, getFilesDir().getPath());
                JDRIVE.instance().initialize(getFilesDir().getPath());
                JDRIVE.instance().setLocationProvider(new GPXLocationProvider(new File(getFilesDir(), "master.gpx").getPath()));

                // Setup the listener for the OSR
                JDRIVE.instance().osr(new EventReceiver() {
                    @Override
                    public void receive(String event, String ts) {
                        Log.v(TAG, "RECEIVED AN OSR!!!");
                        Log.v(TAG, "TS: " + ts);
                        sendOSU(ts);
                    }
                });

                // Setup the listener for the UI event which happens when a fence is entered and exited.
                JDRIVE.instance().addListenerForEvent("UI", "UI", new EventReceiver() {
                    @Override
                    public void receive(final String event, String ts) {
                        Log.v(TAG, "UI Event received!");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tv = (TextView)findViewById(R.id.textView3);
                                tv.setText(getUIName(event));

                                tv = (TextView)findViewById(R.id.textView2);
                                tv.setText(getLoc(event));

                            }
                        });
                    }
                });

                // start driving.
                Log.v(TAG, "running drive");
                Snackbar.make(v, "running!", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                JDRIVE.instance().run();
            }
        });
    }

    private void HookupStopButton() {
        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.v(TAG, "stopping drive");
                Snackbar.make(v, "stopping", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                JDRIVE.instance().stop();
            }
        });
    }

////DRIVE
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart);

        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // Get default theme colors
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        mTxColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
        mRxColor = typedValue.data;

        // UI
        mBufferListView = (ListView) findViewById(R.id.bufferListView);
        mBufferListAdapter = new TimestampListAdapter(this, R.layout.layout_uart_datachunkitem);
        mBufferListView.setAdapter(mBufferListAdapter);
        mBufferListView.setDivider(null);

        mBufferTextView = (EditText) findViewById(R.id.bufferTextView);
        if (mBufferTextView != null) {
            mBufferTextView.setKeyListener(null);     // make it not editable
        }
        BTLETextView=(TextView)findViewById(BTLEText);
//        mSendEditText = (EditText) findViewById(R.id.sendEditText);
//        mSendEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//            @Override
//            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
//                if (actionId == EditorInfo.IME_ACTION_SEND) {
//                    sendTrigger(null);
//                    return true;
//                }
//
//                return false;
//            }
//        });
//        mSendEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            public void onFocusChange(View view, boolean hasFocus) {
//                if (!hasFocus) {
//                    // Dismiss keyboard when sendEditText loses focus
//                    dismissKeyboard(view);
//                }
//            }
//        });

        mSentBytesTextView = (TextView) findViewById(R.id.sentBytesTextView);
        mReceivedBytesTextView = (TextView) findViewById(R.id.receivedBytesTextView);

        // Read shared preferences
        maxPacketsToPaintAsText = PreferencesFragment.getUartTextMaxPackets(this);
        //Log.d(TAG, "maxPacketsToPaintAsText: "+maxPacketsToPaintAsText);

        // Read local preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        mShowDataInHexFormat = !preferences.getBoolean(kPreferences_asciiMode, true);
        final boolean isTimestampDisplayMode = preferences.getBoolean(kPreferences_timestampDisplayMode, false);
        setDisplayFormatToTimestamp(isTimestampDisplayMode);
        mIsEchoEnabled = preferences.getBoolean(kPreferences_echo, true);
        mIsEolEnabled = preferences.getBoolean(kPreferences_eol, true);
        invalidateOptionsMenu();        // udpate options menu with current values

        // Continue
        onServicesDiscovered();

        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        copyGPXFile();
        HookupStartButton();
        HookupStopButton();


        ////////////////LOCATION

        // Define a listener that responds to location updates
        LocationManager locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
//                makeUseOfNewLocation(location);
                uartSendData("1", false);
                mBufferTextView.setText("");
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    Log.e(TAG, "LOLZ");
                }
                String text = mBufferTextView.getText().toString();
                int sleepStatus = 1;//Integer.parseInt(text);
                latLonSleepList.add(new LatLonSleep(location.getLatitude(), location.getLongitude(), sleepStatus));
                BTLETextView.setText(Double.toString(location.getLatitude()) + ", " + Double.toString(location.getLongitude()));
                for (LatLonSleep item : latLonSleepList) {
                    Log.v(TAG, item.getLat() + ", " + item.getLon() + ", " + item.getStatus());
                }
                int n = 8, count = 0;
                if (latLonSleepList.size() > n) {
                    for (LatLonSleep i : latLonSleepList.subList(Math.max(latLonSleepList.size() - n, 0), latLonSleepList.size())) {
                        if (i.getStatus() == 1)
                            count = count + 1;
                    }
                }

                if (count > 4) {
                    ///{
//                    "_id": "57ed2e8bd8e6e25432a6bc32",
//                            "id": 4752,
//                            "thirdPartyPrograms": [ ],
//                    "fenceType": "B",
//                            "location": {
//                        "type": "Polygon",
//                                "coordinates": [
//                        [
//                        [ -113.562332, 53.239616 ],
//                        [ -113.565323, 53.239933 ],
//                        [ -113.562248, 53.248684 ],
//                        [ -113.560944, 53.248566 ],
//                        [ -113.562332, 53.239616 ]
//                        ]
//                        ]
//                    },
//                    "type": "fence",
//                            "bearing": 180,
//                            "Seq": 64407,
//                            "loc": "AB Leduc Hwy-2 SB",
//                            "status": 1,
//                            "st": 1
//                }
//                }

                    JSONObject obj=new JSONObject();
                    obj.put("_id", UUID.randomUUID().toString());
                    obj.put("id",String.format("%04d", new Random(System.currentTimeMillis()).nextInt(10000)));
                    JSONArray arr=new JSONArray();
                    obj.put("thirdPartyPrograms",arr);
                    obj.put("fencetype","B");
                    JSONObject loc=new JSONObject();
                    loc.put("type","Polygon");
                    JSONArray coord=new JSONArray();
                    JSONArray coord2=new JSONArray();
                    JSONArray coord3=new JSONArray();
                    for(int i=1;i<=4;i++){
                        int size=latLonSleepList.size();

                        coord3.add(latLonSleepList.get(size-i).getLon());
                        coord3.add(latLonSleepList.get(size-i).getLat());
                        coord2.add(coord3);
                        coord3=new JSONArray();
                    }

                    coord.add(coord2);
                    loc.put("coordinates",coord);
                    obj.put("location",loc);
                    obj.put("type","fence");
                    obj.put("bearing",500);
                    obj.put("Seq",50000);
                    obj.put("loc","Sleepy Zone"+Integer.toString(sleepyZoneCount));
                    sleepyZoneCount=sleepyZoneCount+1;
                    obj.put("status",1);
                    obj.put("st",1);
                    Log.v(TAG,"obj"+obj);
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

// Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        // Start UI refresh
        //Log.d(TAG, "add ui timer");
        updateUI();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();

        //Log.d(TAG, "remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        // Save preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mIsEchoEnabled);
        editor.putBoolean(kPreferences_eol, mIsEolEnabled);
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);
        editor.putBoolean(kPreferences_timestampDisplayMode, mIsTimestampDisplayMode);

        editor.apply();
    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    public void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void sendTrigger(View view) {
//        String data = mSendEditText.getText().toString();
//        mSendEditText.setText("");       // Clear editText
        String data="1";
        uartSendData(data, false);
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
            mSentBytes += data.length();
        }

        // Add to current buffer
        byte[] bytes = new byte[0];
        try {
            bytes = data.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_TX, bytes);
        mDataBuffer.add(dataChunk);

        final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);
        if (mIsTimestampDisplayMode) {
            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
            mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] TX: " + formattedData, mTxColor));
            mBufferListView.setSelection(mBufferListAdapter.getCount());
        }

        // Update UI
        updateUI();
    }

    public void BTLECheck(View view) {
        String text = mBufferTextView.getText().toString(); // mShowDataInHexFormat ? mHexSpanBuffer.toString() : mAsciiSpanBuffer.toString();

        BTLETextView.setText(text);
//        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//        ClipData clip = ClipData.newPlainText("UART", text);
//        clipboard.setPrimaryClip(clip);
    }

//    public void onClickClear(View view) {
//        mTextSpanBuffer.clear();
//        mDataBufferLastSize = 0;
//        mBufferListAdapter.clear();
//        mBufferTextView.setText("");
//
//        mDataBuffer.clear();
//        mSentBytes = 0;
//        mReceivedBytes = 0;
//        updateUI();
//    }

//    public void onClickShare(View view) {
//        String textToSend = mBufferTextView.getText().toString(); // (mShowDataInHexFormat ? mHexSpanBuffer : mAsciiSpanBuffer).toString();
//
//        if (textToSend.length() > 0) {
//
//            Intent sendIntent = new Intent();
//            sendIntent.setAction(Intent.ACTION_SEND);
//            sendIntent.putExtra(Intent.EXTRA_TEXT, textToSend);
//            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.uart_share_subject));     // subject will be used if sent to an email app
//            sendIntent.setType("text/*");       // Note: don't use text/plain because dropbox will not appear as destination
//            // startActivity(sendIntent);
//            startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.uart_sharechooser_title)));      // Always show the app-chooser
//        } else {
//            new AlertDialog.Builder(this)
//                    .setMessage(getString(R.string.uart_share_empty))
//                    .setPositiveButton(android.R.string.ok, null)
//                    .show();
//        }
//    }

    /*
    public void onClickFormatAscii(View view) {
        mShowDataInHexFormat = false;
        recreateDataView();
    }

    public void onClickFormatHex(View view) {
        mShowDataInHexFormat = true;
        recreateDataView();
    }

    public void onClickDisplayFormatText(View view) {
        setDisplayFormatToTimestamp(false);
        recreateDataView();
    }

    public void onClickDisplayFormatTimestamp(View view) {
        setDisplayFormatToTimestamp(true);
        recreateDataView();
    }
    */

    private void setDisplayFormatToTimestamp(boolean enabled) {
        mIsTimestampDisplayMode = enabled;
        mBufferTextView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        mBufferListView.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_uart, menu);

        // Mqtt
        mMqttMenuItem = menu.findItem(R.id.action_mqttsettings);
        mMqttMenuItemAnimationHandler = new Handler();
        mMqttMenuItemAnimationRunnable.run();

        // DisplayMode
        MenuItem displayModeMenuItem = menu.findItem(R.id.action_displaymode);
        displayModeMenuItem.setTitle(String.format(getString(R.string.uart_action_displaymode_format), getString(mIsTimestampDisplayMode ? R.string.uart_displaymode_timestamp : R.string.uart_displaymode_text)));
        SubMenu displayModeSubMenu = displayModeMenuItem.getSubMenu();
        if (mIsTimestampDisplayMode) {
            MenuItem displayModeTimestampMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_timestamp);
            displayModeTimestampMenuItem.setChecked(true);
        } else {
            MenuItem displayModeTextMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_text);
            displayModeTextMenuItem.setChecked(true);
        }

        // DataMode
        MenuItem dataModeMenuItem = menu.findItem(R.id.action_datamode);
        dataModeMenuItem.setTitle(String.format(getString(R.string.uart_action_datamode_format), getString(mShowDataInHexFormat ? R.string.uart_format_hexadecimal : R.string.uart_format_ascii)));
        SubMenu dataModeSubMenu = dataModeMenuItem.getSubMenu();
        if (mShowDataInHexFormat) {
            MenuItem dataModeHexMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_hex);
            dataModeHexMenuItem.setChecked(true);
        } else {
            MenuItem dataModeAsciiMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_ascii);
            dataModeAsciiMenuItem.setChecked(true);
        }

        // Echo
        MenuItem echoMenuItem = menu.findItem(R.id.action_echo);
        echoMenuItem.setTitle(R.string.uart_action_echo);
        echoMenuItem.setChecked(mIsEchoEnabled);

        // Eol
        MenuItem eolMenuItem = menu.findItem(R.id.action_eol);
        eolMenuItem.setTitle(R.string.uart_action_eol);
        eolMenuItem.setChecked(mIsEolEnabled);

        return true;
    }

    private Runnable mMqttMenuItemAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            updateMqttStatus();
            mMqttMenuItemAnimationHandler.postDelayed(mMqttMenuItemAnimationRunnable, 500);
        }
    };
    private int mMqttMenuItemAnimationFrame = 0;

    private void updateMqttStatus() {
        if (mMqttMenuItem == null)
            return;      // Hack: Sometimes this could have not been initialized so we don't update icons

        MqttManager mqttManager = MqttManager.getInstance(this);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        if (status == MqttManager.MqqtConnectionStatus.CONNECTING) {
            final int kConnectingAnimationDrawableIds[] = {R.drawable.mqtt_connecting1, R.drawable.mqtt_connecting2, R.drawable.mqtt_connecting3};
            mMqttMenuItem.setIcon(kConnectingAnimationDrawableIds[mMqttMenuItemAnimationFrame]);
            mMqttMenuItemAnimationFrame = (mMqttMenuItemAnimationFrame + 1) % kConnectingAnimationDrawableIds.length;
        } else if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
            mMqttMenuItem.setIcon(R.drawable.mqtt_connected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        } else {
            mMqttMenuItem.setIcon(R.drawable.mqtt_disconnected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_help:
                startHelp();
                return true;

            case R.id.action_connected_settings:
                startConnectedSettings();
                return true;

            case R.id.action_refreshcache:
                if (mBleManager != null) {
                    mBleManager.refreshDeviceCache();
                }
                break;

            case R.id.action_mqttsettings:
                Intent intent = new Intent(this, MqttUartSettingsActivity.class);
                startActivityForResult(intent, kActivityRequestCode_MqttSettingsActivity);
                break;

            case R.id.action_displaymode_timestamp:
                setDisplayFormatToTimestamp(true);
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_displaymode_text:
                setDisplayFormatToTimestamp(false);
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_datamode_hex:
                mShowDataInHexFormat = true;
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_datamode_ascii:
                mShowDataInHexFormat = false;
                recreateDataView();
                invalidateOptionsMenu();
                return true;

            case R.id.action_echo:
                mIsEchoEnabled = !mIsEchoEnabled;
                invalidateOptionsMenu();
                return true;

            case R.id.action_eol:
                mIsEolEnabled = !mIsEolEnabled;
                invalidateOptionsMenu();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startConnectedSettings() {
        // Launch connected settings activity
        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == kActivityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
            finish();
        } else if (requestCode == kActivityRequestCode_MqttSettingsActivity && resultCode == RESULT_OK) {

        }
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.uart_help_title));
        intent.putExtra("help", "uart_help.html");
        startActivity(intent);
    }
    // endregion

    // region BleManagerListener
    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                mReceivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, bytes);
                mDataBuffer.add(dataChunk);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsTimestampDisplayMode) {
                            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                            final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);

                            mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] RX: " + formattedData, mRxColor));
                            //mBufferListAdapter.add("[" + currentDateTimeString + "] RX: " + formattedData);
                            //mBufferListView.smoothScrollToPosition(mBufferListAdapter.getCount() - 1);
                            mBufferListView.setSelection(mBufferListAdapter.getCount());
                        }
                        updateUI();
                    }
                });

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
                if (settings.isPublishEnabled()) {
                    String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                    final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                    final String text = bytesToText(bytes, false);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }
    }

    private String bytesToText(byte[] bytes, boolean simplifyNewLine) {
        String text = new String(bytes, Charset.forName("UTF-8"));
        if (simplifyNewLine) {
            text = text.replaceAll("(\\r\\n|\\r)", "\n");
        }
        return text;
    }
/*
    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    */
    // endregion

    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {

        if (kUseColorsForData) {
            final int from = spanBuffer.length();
            spanBuffer.append(text);
            spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spanBuffer.append(text);
        }
    }

    private void updateUI() {
        mSentBytesTextView.setText(String.format(getString(R.string.uart_sentbytes_format), mSentBytes));
        mReceivedBytesTextView.setText(String.format(getString(R.string.uart_receivedbytes_format), mReceivedBytes));
    }


    private int mDataBufferLastSize = 0;

    private void updateTextDataUI() {

        if (!mIsTimestampDisplayMode) {
            if (mDataBufferLastSize != mDataBuffer.size()) {

                final int bufferSize = mDataBuffer.size();
                if (bufferSize > maxPacketsToPaintAsText) {
                    mDataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
                    mTextSpanBuffer.clear();
                    addTextToSpanBuffer(mTextSpanBuffer, getString(R.string.uart_text_dataomitted) + "\n", mInfoColor);
                }

                // Log.d(TAG, "update packets: "+(bufferSize-mDataBufferLastSize));
                for (int i = mDataBufferLastSize; i < bufferSize; i++) {
                    final UartDataChunk dataChunk = mDataBuffer.get(i);
                    final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                    final byte[] bytes = dataChunk.getData();
                    final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);
                    addTextToSpanBuffer(mTextSpanBuffer, formattedData, isRX ? mRxColor : mTxColor);
                }

                mDataBufferLastSize = mDataBuffer.size();
                mBufferTextView.setText(mTextSpanBuffer);
                mBufferTextView.setSelection(0, mTextSpanBuffer.length());        // to automatically scroll to the end
            }
        }
    }

    private void recreateDataView() {

        if (mIsTimestampDisplayMode) {
            mBufferListAdapter.clear();

            final int bufferSize = mDataBuffer.size();
            for (int i = 0; i < bufferSize; i++) {

                final UartDataChunk dataChunk = mDataBuffer.get(i);
                final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                final byte[] bytes = dataChunk.getData();
                final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);

                final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData, isRX ? mRxColor : mTxColor));
//                mBufferListAdapter.add("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData);
            }
            mBufferListView.setSelection(mBufferListAdapter.getCount());
        } else {
            mDataBufferLastSize = 0;
            mTextSpanBuffer.clear();
            mBufferTextView.setText("");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder stringBuffer = new StringBuilder();
        for (byte aByte : bytes) {
            String charString = String.format("%02X", (byte) aByte);

            stringBuffer.append(charString).append(" ");
        }
        return stringBuffer.toString();
    }

    // region DataFragment
    public static class DataFragment extends Fragment {
        private boolean mShowDataInHexFormat;
        private SpannableStringBuilder mTextSpanBuffer;
        private ArrayList<UartDataChunk> mDataBuffer;
        private int mSentBytes;
        private int mReceivedBytes;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();

            mDataBuffer = new ArrayList<>();
            mTextSpanBuffer = new SpannableStringBuilder();
        } else {
            // Restore status
            mShowDataInHexFormat = mRetainedDataFragment.mShowDataInHexFormat;
            mTextSpanBuffer = mRetainedDataFragment.mTextSpanBuffer;
            mDataBuffer = mRetainedDataFragment.mDataBuffer;
            mSentBytes = mRetainedDataFragment.mSentBytes;
            mReceivedBytes = mRetainedDataFragment.mReceivedBytes;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mShowDataInHexFormat = mShowDataInHexFormat;
        mRetainedDataFragment.mTextSpanBuffer = mTextSpanBuffer;
        mRetainedDataFragment.mDataBuffer = mDataBuffer;
        mRetainedDataFragment.mSentBytes = mSentBytes;
        mRetainedDataFragment.mReceivedBytes = mReceivedBytes;
    }
    // endregion


    // region MqttManagerListener

    @Override
    public void onMqttConnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttDisconnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        //Log.d(TAG, "Mqtt messageArrived from topic: " +topic+ " message: "+message);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uartSendData(message, true);       // Don't republish to mqtt something received from mqtt
            }
        });

    }

    // endregion


    // region TimestampAdapter
    private class TimestampData {
        String text;
        int textColor;

        TimestampData(String text, int textColor) {
            this.text = text;
            this.textColor = textColor;
        }
    }

    private class TimestampListAdapter extends ArrayAdapter<TimestampData> {

        TimestampListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.layout_uart_datachunkitem, parent, false);
            }

            TimestampData data = getItem(position);
            TextView textView = (TextView) convertView;
            textView.setText(data.text);
            textView.setTextColor(data.textColor);

            return convertView;
        }
    }
    // endregion


    ///DRIVE
    private void copyGPXFile() {
        try {
            InputStream istream = getAssets().open("gpx/master.gpx");
            int size = istream.available();
            byte[] buffer = new byte[size];
            istream.read(buffer);
            istream.close();

            FileOutputStream ostream = new FileOutputStream(new File(getFilesDir() + "/master.gpx"));
            ostream.write(buffer);
            ostream.close();
        } catch(Exception e) {

        }
    }

    private void sendOSU(String ts) {
        try {
            Log.v(TAG, "preparing OSU");
            String[] files = getAssets().list("os");
            StringBuilder payload = new StringBuilder();
            int numFiles = files.length;
            int count = 0;
            for (String file : files) {
                count++;
                Log.v(TAG, "reading file: " + file);
                InputStream stream = getAssets().open("os/" + file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line;
                while ((line = reader.readLine()) != null) {
                    payload.append(line);
                }
                if (count < numFiles)
                    payload.append(',');
            }
            Log.v(TAG, "sending OSU");
            //JDRIVE.instance().osu(payload.toString(), ts);
        } catch (Exception ex) {
        }
    }










}
