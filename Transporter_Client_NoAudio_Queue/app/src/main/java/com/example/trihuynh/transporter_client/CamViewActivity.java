package com.example.trihuynh.transporter_client;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.IOException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.List;

public class CamViewActivity extends ActionBarActivity {

    private static final String LOG_TAG = "Transporter";

    private String hostname;

    ImageView imageView;
    SocketClient socketClient;
    Socket sock = null;
    Thread thread,receiveThread;
    int queueLen = 50;
    //Bitmap[] queueBitmapReceived = new Bitmap[queueLen];
    byte[][] queueBitmapReceived = new byte[120][300000];
    int[] queueBitmapReceivedLen = new int[120];
    int queueBitmapReceivedFront = 0;
    int queueBitmapReceivedRear = 0;
    int numBitmapReceived = 0, numBitmapViewed = 0;
    int fps = 12;
    long TimeWait = 1000/fps;
    Bitmap bitmapReceived;
    long StartTime = 0;
    boolean isTerminated = false;
    BitmapFactory.Options options = new BitmapFactory.Options();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam_view);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        getSupportActionBar().hide();
        Intent intent = getIntent();
        imageView = (ImageView)findViewById(R.id.image_view);
        hostname = intent.getStringExtra("ip");
        options.inBitmap = bitmapReceived;
        options.inMutable = true;
        options.inSampleSize = 1;
        StartTime = System.currentTimeMillis();
        try
        {
            socketClient = new SocketClient(intent.getStringExtra("ip"),intent.getIntExtra("port", 2222));
            socketClient.execute();
            startViewing();
        }
        catch (Exception e)
        {

        }

    }

    @Override
    protected void onStop()
    {
        try {
            isTerminated = true;
            OutputStream outputStream = sock.getOutputStream();
            byte[] quitMessage = new byte[]{'Q','U','I','T'};
            outputStream.write(quitMessage,0,quitMessage.length);
            sock.close();
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "OnStop in CamView: " + e.toString());
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cam_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void startViewing()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (numBitmapReceived<20)
                {
                    SystemClock.sleep(50);
                }
                while (!isTerminated) {
                    try {
                        if (Math.abs(System.currentTimeMillis()-StartTime)>=TimeWait) {
                            StartTime = System.currentTimeMillis();
                            if (numBitmapReceived>numBitmapViewed) {

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try
                                        {
                                            bitmapReceived = BitmapFactory.decodeByteArray(queueBitmapReceived[queueBitmapReceivedFront], 0, queueBitmapReceivedLen[queueBitmapReceivedFront],options);
                                            options.inBitmap = bitmapReceived;
                                            imageView.setImageBitmap(bitmapReceived);
                                            queueBitmapReceivedFront = (queueBitmapReceivedFront + 1) % queueLen;
                                            numBitmapViewed++;
                                            Log.i("Queue Debugging", "numBitmapReceived = " + numBitmapReceived + ", numBitmapViewed = " + numBitmapViewed);
                                        }
                                        catch (Exception e)
                                        {
                                            Log.e("Error in Viewing", e.toString());
                                        }
                                    }
                                });

                            }
                        }
                    } catch (Exception e) {

                    }
                }
            }
        });
        thread.start();
    }

    public class SocketClient extends AsyncTask<Void, Void, Void>
    {
        String strIP;
        int iPort;

        InputStream inputStream;
        byte[] buffer = new byte[300000];

        SocketClient(String ip, int port)
        {
            strIP = ip;
            iPort = port;
        }
        @Override
        protected Void doInBackground(Void... arg0)
        {
            try {
                while (sock==null) {
                    SystemClock.sleep(50);
                    sock = new Socket(strIP, iPort);
                }
                Log.i(LOG_TAG, "Socket Log: Connected to video socket");
                inputStream = sock.getInputStream();
                ByteArrayOutputStream messageReceived = new ByteArrayOutputStream();
                inputStream.read(buffer, 0, 8);
                messageReceived.write(buffer, 0, 8);
                //process messageReceived
                messageReceived.reset();
                inputStream.read(buffer, 0, 8);
                messageReceived.write(buffer,0,8);

                int start = 0;
                while (true)
                {
                    int numByteRead = 0;
                    int dataLength = 8;
                    messageReceived.reset();
                    start = 0;
                    while (dataLength>0) {
                        numByteRead = inputStream.read(buffer, start, dataLength);
                        dataLength-=numByteRead;
                        start+=numByteRead;
                    }
                    messageReceived.write(buffer, 0, 8);
                    dataLength = Integer.parseInt(messageReceived.toString());
                    messageReceived.reset();
                    start = 0;
                    queueBitmapReceivedLen[queueBitmapReceivedRear] = dataLength;
                    while (dataLength>0)
                    {
                        numByteRead = inputStream.read(queueBitmapReceived[queueBitmapReceivedRear],start,Math.min(1024,dataLength));
                        if (numByteRead>0)
                        {
                            dataLength-=numByteRead;
                            start+=numByteRead;
                        }
                        else
                        {
                            onStop();
                            return null;
                        }
                    }

                    queueBitmapReceivedRear = (queueBitmapReceivedRear+1)%queueLen;

                    numBitmapReceived++;

                }
            }
            catch (Exception e)
            {
                Log.i("VideoSocket Error",e.toString());
            }


            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            try {

            }
            catch (Exception e)
            {

            }
            super.onPostExecute(result);
        }
    }
}
