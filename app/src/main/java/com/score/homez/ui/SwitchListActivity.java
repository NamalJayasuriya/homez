package com.score.homez.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.score.homez.R;
import com.score.homez.db.HomezDbSource;
import com.score.homez.pojos.Switch;
import com.score.homez.utils.ActivityUtils;
import com.score.homez.utils.SenzUtils;
import com.score.senz.ISenzService;
import com.score.senzc.pojos.Senz;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Switch activity
 */
public class SwitchListActivity extends Activity {

    private static final String TAG = SwitchListActivity.class.getName();

    // we use custom font here
    private Typeface typeface;

    // switch list
    private ArrayList<Switch> switchList;

    private ListView switchListView;
    private SwitchListAdapter switchListAdapter;

    // service interface
    private ISenzService senzService = null;

    // service bind status
    private boolean isServiceBound = false;

    // keep track with weather response received
    private boolean isResponseReceived = false;
    private boolean isTimerNotStarted = true;

    // timers fot get/put
    private CountDownTimer getTimer;

    private String photoString="";
    private String lastTimerID="";

    //private CountDownTimer putTimer;
    private HashMap<String, CountDownTimer> timerList = new HashMap();

    // service connection
    private ServiceConnection senzServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Connected with senz service");
            senzService = ISenzService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Disconnected from senz service");
            senzService = null;
        }
    };

    // Receives data senz to here
    private BroadcastReceiver senzMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Got message from Senz service *** ");
            handleMessage(intent);
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.switch_list_layout);

        bindSenzService();
        registerReceiver(senzMessageReceiver, new IntentFilter("com.score.senz.DATA_SENZ"));

        initUi();
        setupActionBar();
        popUpSwitchList();

        //set the switches to the last exit stage

       /* for(Switch aSwitch:switchList)
            aSwitch.setStatus(aSwitch.getStatus()==1? 0:1);*/




        //doGet(switchList, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // reload list
        popUpSwitchList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(senzMessageReceiver);
        if (isServiceBound) {
            unbindService(senzServiceConnection);
            isServiceBound = false;
        }
    }

    /**
     * Bind with senz service
     */
    private void bindSenzService() {
        if (!isServiceBound) {
            Intent intent = new Intent();
            intent.setClassName("com.score.senz", "com.score.senz.services.RemoteSenzService");
            bindService(intent, senzServiceConnection, BIND_AUTO_CREATE);
            isServiceBound = true;
        }
    }

    /**
     * Initialize UI components
     */
    private void initUi() {
        typeface = Typeface.createFromAsset(getAssets(), "fonts/vegur_2.otf");

        switchListView = (ListView) findViewById(R.id.switch_list);

        // add header and footer for list
        View headerView = View.inflate(this, R.layout.list_footer, null);
        View footerView = View.inflate(this, R.layout.list_footer, null);
        switchListView.addHeaderView(headerView);
        switchListView.addFooterView(footerView);
    }

    /**
     * Display switch list
     */
    public void popUpSwitchList() {
        // Create sample list now
        switchList = (ArrayList<Switch>) new HomezDbSource(this).getAllSwitches();
        if(switchList.isEmpty()){

            for(int i=1;i<9;i++){
                Switch s =new Switch("switch  "+i ,i%2);
                switchList.add(s);
            }
        }
        switchListAdapter = new SwitchListAdapter(switchList, this);
        switchListAdapter.notifyDataSetChanged();
        switchListView.setAdapter(switchListAdapter);
    }

    /**
     * Set up action bar
     */
    private void setupActionBar() {
        // enable ActionBar app icon to behave as action to toggle nav drawer
        //getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        int titleId = getResources().getIdentifier("action_bar_title", "id", "android");
        TextView yourTextView = (TextView) findViewById(titleId);
        yourTextView.setTextColor(getResources().getColor(R.color.white));
        yourTextView.setTypeface(typeface);

        getActionBar().setTitle("Homez");
    }

    /**
     * Send periodic PUT request via timer
     *
     * @param aSwitch
     */
    public void doPut(Switch aSwitch) {
        // create put senz
        final Senz senz = SenzUtils.createPutSenz(aSwitch, this);
        //Timer is used to identified the SenZ
        final String timerID = senz.getAttributes().get("time");
        if (senz != null) {
            ActivityUtils.cancelProgressDialog();
            ActivityUtils.showProgressDialog(this, "Please wait...");
            timerList.put(timerID, new CountDownTimer(16000, 5000) {
                //putTimer = new CountDownTimer(16000, 5000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    //if (!isResponseReceived) {
                    if (timerList.containsKey(timerID)) {
                        Log.d(TAG, "PUT Response not received yet");
                        // send put
                        try {
                            senzService.send(senz);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onFinish() {
                    ActivityUtils.hideSoftKeyboard(SwitchListActivity.this);
                    ActivityUtils.cancelProgressDialog();
                    // display message dialog that we couldn't reach the user
                    //if (!isResponseReceived) {
                    if (timerList.containsKey(timerID)) {
                        //isResponseReceived = true;
                        timerList.remove(timerID);
                        String message = "<font color=#000000>Seems we couldn't reach </font> <font color=#eada00>" +
                                "<b>" + senz.getReceiver().getUsername() + "</b>" + "</font> <font color=#000000> at this moment</font>";
                        displayInformationMessageDialog("#PUT Fail", message);
                    }
                }
            });
            timerList.get(timerID).start();

        }
    }

    /**
     * Send periodic GET senz via timer
     *
     * @param switchList
     */
    public void doGet(ArrayList<Switch> switchList,boolean getType) {
        // create get senz
        Senz senz=null;
        if (getType) {
            senz=SenzUtils.createGetSenz(switchList, this);
        }else{
            senz = SenzUtils.createGetPhotoSenze(this);
        }
        Log.d(TAG, "*********** "+senz.getAttributes().toString());
        //Timer is used to identified the SenZ
        final String timerID = senz.getAttributes().get("time");
        if (senz != null) {
            ActivityUtils.cancelProgressDialog();
            ActivityUtils.showProgressDialog(this, "Please wait...");
            final Senz finalSenz = senz;
            timerList.put(timerID, new CountDownTimer(16000, 5000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (timerList.containsKey(timerID)) {
                        Log.d(TAG, "GET Response not received yet "+timerID);
                        // send GET
                        try {
                            senzService.send(finalSenz);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
                @Override
                public void onFinish() {
                    ActivityUtils.hideSoftKeyboard(SwitchListActivity.this);
                    ActivityUtils.cancelProgressDialog();
                    if (timerList.containsKey(timerID)) {
                        //isResponseReceived = true;
                        timerList.remove(timerID);
                        String message = "<font color=#000000>Seems we couldn't reach </font> <font color=#eada00>" +
                                "<b>" + finalSenz.getReceiver().getUsername() + "</b>" + "</font> <font color=#000000> at this moment</font>";
                        displayInformationMessageDialog("#GET Fail", message);
                    }
                }
            });
            timerList.get(timerID).start();

        }
    }

    /**
     * Handle broadcast message receives
     * Need to handle registration success failure here
     *
     * @param intent intent
     */
    private void handleMessage(Intent intent) {
        String action = intent.getAction();

        if (action.equalsIgnoreCase("com.score.senz.DATA_SENZ")) {
            Senz senz = intent.getExtras().getParcelable("SENZ");
            String timerID = senz.getAttributes().get("time");
            if (senz.getAttributes().containsValue("PutDone")) {
                // response received for PUT senz
                if (timerList.containsKey(timerID)) {
                    ActivityUtils.cancelProgressDialog();
                    timerList.get(timerID).cancel();
                    timerList.remove(timerID);
                    Log.d(TAG, "PutResponse received " + timerID);
                    onPostPut(senz);
                }
            }else if (senz.getAttributes().containsKey("photo")) {
                if (timerList.containsKey(timerID)) {
                    ActivityUtils.cancelProgressDialog();
                    timerList.get(timerID).cancel();
                    timerList.remove(timerID);
                }
                onPostPhoto(senz, timerID);
                lastTimerID=timerID;
            }else if (senz.getAttributes().containsValue("GetResponse")) {
                // response received for GET senz
                if (timerList.containsKey(timerID)) {
                    ActivityUtils.cancelProgressDialog();
                    timerList.get(timerID).cancel();
                    timerList.remove(timerID);
                    Log.d(TAG, "Getresponse received -----");
                    onPostGet(senz);
                }
            }
        }
    }

    /**
     * Call after PUT response
     *
     * @param senz
     */
    private void onPostPut(Senz senz) {
        //putTimer.cancel();
        // TODO update switch in db
        HomezDbSource dbSource = new HomezDbSource(this);
        for (String key : senz.getAttributes().keySet()) {
            if (!key.equals("time") && !key.equals("msg")) {
                int status = senz.getAttributes().get(key).equals("on") ? 1 : 0;
                dbSource.setSwitchStatus(new Switch(key, status));
                Log.d(TAG, "Update switch status ** " + senz.getAttributes().get(key));
            }
        }
        // reload list
        popUpSwitchList();

    }

    /**
     * Call after GET response
     *
     * @param senz
     */
    private void onPostGet(Senz senz) {
        // TODO update switch status in DB
        //new HomezDbSource(this).setSwitchStatus(new Switch(name, status));
        // TODO update switch in db
        HomezDbSource dbSource = new HomezDbSource(this);
        for (String key : senz.getAttributes().keySet()) {
            if (!key.equals("time") && !key.equals("msg")) {
                int status = senz.getAttributes().get(key).equals("on") ? 1 : 0;
                dbSource.setSwitchStatus(new Switch(key, status));
                Log.d(TAG, "GET: Update switch status ** " + senz.getAttributes().get(key));
            }
        }
        popUpSwitchList();
    }

    private void onPostPhoto(Senz senz,String timeID) {
        // TODO update switch status in DB
        if (senz.getAttributes().get("photo").equals("ENDPHOTO")){
            byte[] photoData = Base64.decode(photoString, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(photoData, 0, photoData.length);
            Log.d(TAG, "++ Photo received " + photoData.length);
            Drawable draw = new BitmapDrawable(getResources(), bm);
            displayImageDialog("Photo Received",draw);

        } else if (lastTimerID.equals(timeID)) {
            photoString+=senz.getAttributes().get("photo");
        }else {
            lastTimerID=timeID;
            photoString=senz.getAttributes().get("photo");
        }
        Log.d(TAG, "*** Photo length" + photoString.length()+" "+lastTimerID+" "+timeID);
    }

    /**
     * Display message dialog when user request(click) to delete invoice
     *
     * @param message message to be display
     */
    public void displayInformationMessageDialog(String title, String message) {
        final Dialog dialog = new Dialog(this);

        //set layout for dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.information_message_dialog);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        // set dialog texts
        TextView messageHeaderTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_header_text);
        TextView messageTextView = (TextView) dialog.findViewById(R.id.information_message_dialog_layout_message_text);
        messageHeaderTextView.setText(title);
        messageTextView.setText(Html.fromHtml(message));

        // set custom font
        messageHeaderTextView.setTypeface(typeface);
        messageTextView.setTypeface(typeface);

        //set ok button
        Button okButton = (Button) dialog.findViewById(R.id.information_message_dialog_layout_ok_button);
        okButton.setTypeface(typeface);
        okButton.setTypeface(null, Typeface.BOLD);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.show();
    }


    /**
     * Display home light image
     * TODO class this function when receives the image
     *
     * @param title    title
     * @param drawable image
     */
    public void displayImageDialog(String title, Drawable drawable) {
        final Dialog dialog = new Dialog(this);

        //set layout for dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.image_dialog_layout);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        // set dialog texts
        TextView messageHeaderTextView = (TextView) dialog.findViewById(R.id.image_dialog_layout_message_header_text);
        messageHeaderTextView.setText(title);
        messageHeaderTextView.setTypeface(typeface);

        // set image
        ImageView imageView = (ImageView) dialog.findViewById(R.id.image_dialog_layout_light_image);
        imageView.setBackground(drawable);
        //set ok button
        Button okButton = (Button) dialog.findViewById(R.id.image_dialog_layout_ok_button);
        okButton.setTypeface(typeface);
        okButton.setTypeface(null, Typeface.BOLD);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.cancel();
            }
        });

        dialog.show();
    }

}
