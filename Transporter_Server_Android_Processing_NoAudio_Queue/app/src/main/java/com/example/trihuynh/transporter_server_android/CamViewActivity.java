package com.example.trihuynh.transporter_server_android;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.Image;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.PowerManager;
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.List;
import java.net.ServerSocket;

import android.media.AudioTrack;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Range;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.lang.Math;


public class CamViewActivity extends ActionBarActivity {

    private static final String LOG_TAG = "Transporter";
    private String hostname;

    ImageView imageView;
    SocketClient socketClient;
    Socket sock = null;
    Thread thread,receiveThread, serverThread;
    Socket socketServer;


    private int socketServerPort = 8888;
    private boolean isClientConnected = false;

    int HORIZONTAL_CROP = 60;
    int vertical_crop;
    int imgWidth = 640, imgHeight=480;
    Mat preImageSmooth, preImageSmooth2;
    Mat preImageSmoothCropped, preImageSmoothCropped2;
    Bitmap bitmapStereo;
    boolean hasBitmapStereo = false;
    boolean hasBitmapStereoForView = false;
    boolean hasRawImage = false;
    Mat imageRaw;
    int lenQueue = 50;
    Mat[] queueImageReceived = new Mat[50];
    Mat[] queueImageProcessed = new Mat[50];
    int frontImageReceived = 0, rearImageReceived=lenQueue-1;
    int frontImageProcessedSent = 0, frontImageProcessedViewed = 0, rearImageProcessed=lenQueue-1;
    int numImageReceived = 0, numImageSent = 0, numImageProcessed = 0, numImageViewed = 0;
    boolean terminated = false;
    Bitmap bitmap, bitmapDecoded;
    Mat imgStereoView = new Mat();

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

        frontImageReceived = 0;
        rearImageReceived=lenQueue-1;
        frontImageProcessedSent = 0;
        frontImageProcessedViewed = 0;
        rearImageProcessed=lenQueue-1;
        numImageReceived = 0;
        numImageSent = 0;
        numImageProcessed = 0;
        numImageViewed = 0;

        try
        {
            socketClient = new SocketClient(intent.getStringExtra("ip"),intent.getIntExtra("port", 8888));
            socketClient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        catch (Exception e)
        {
            Log.e("OnCreate() Error",e.toString());
        }

    }

    public void startServerSocket()
    {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socketServer = new Socket("robotbase.ddns.net",socketServerPort);
                    startServerVideo();
                    startServerMessage();

                }
                catch (Exception e)
                {
                    Log.e("ServerSocket Error",e.toString());
                }
            }
        });
        serverThread.start();
    }

    public void startServerVideo()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream outputStream = socketServer.getOutputStream();

                    byte[] buff;
                    byte[] buffMatOfByteArray = new byte[300000];

                    while (!isClientConnected)
                    {
                        numImageSent=numImageProcessed;
                        frontImageProcessedSent = rearImageProcessed;
                    }
                    buff = String.format("%08d", imgWidth).getBytes();
                    outputStream.write(buff, 0, 8);
                    buff = String.format("%08d", imgHeight).getBytes();
                    outputStream.write(buff, 0, 8);
                    MatOfInt paramJPEG = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY,90);
                    MatOfByte buffMatOfByte = new MatOfByte();

                    while (!terminated) {
                        if (numImageProcessed>numImageSent) {
                            Mat imgSend = queueImageProcessed[frontImageProcessedSent].clone();
                            Imgproc.cvtColor(imgSend, imgSend, Imgproc.COLOR_RGB2BGR);
                            Highgui.imencode(".jpg",imgSend,buffMatOfByte,paramJPEG);
                            imgSend.release();
                            frontImageProcessedSent = (frontImageProcessedSent+1)%lenQueue;
                            numImageSent++;

                            buffMatOfByte.get(0,0,buffMatOfByteArray);
                            buff = String.format("%08d", buffMatOfByte.rows()*buffMatOfByte.cols()).getBytes();
                            outputStream.write(buff, 0, 8);
                            outputStream.write(buffMatOfByteArray, 0, buffMatOfByte.rows()*buffMatOfByte.cols());
                        }
                    }
                }
                catch (Exception e)
                {
                    Log.e("StartServerVideo()",e.toString());
                }
            }
        });
        thread.start();
    }

    public void startServerMessage()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream inputStream = socketServer.getInputStream();
                    byte[] buf = new byte[4];
                    while (!terminated) {
                        inputStream.read(buf, 0, 4);
                        if ((((char) buf[0]) == 'G') && (((char) buf[1]) == 'O')) {
                            isClientConnected = true;
                        }
                        else
                            if ((((char) buf[0]) == 'Q') && (((char) buf[1]) == 'U') && (((char) buf[2]) == 'I') && (((char) buf[3]) == 'T')) {
                                terminated = true;
                                finish();
                            }
                    }

                }
                catch (Exception e)
                {

                }
            }
        });
        thread.start();
    }

    public void startStabilzation()
    {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                double xReal = 0, yReal = 0, aReal = 0;
                double xSmooth = 0, ySmooth = 0, aSmooth = 0;
                double dxReal = 0, dyReal = 0, daReal = 0;

                double xReal2 = 0, yReal2 = 0, aReal2 = 0;
                double xSmooth2 = 0, ySmooth2 = 0, aSmooth2 = 0;
                double dxReal2 = 0, dyReal2 = 0, daReal2 = 0;
                Mat matPreTrans=new Mat(2,3,CvType.CV_64FC1);
                matPreTrans.put(0,0,Math.cos(0));
                matPreTrans.put(0,1,-Math.sin(0));
                matPreTrans.put(1,0,Math.sin(0));
                matPreTrans.put(1,1,Math.cos(0));
                matPreTrans.put(0,2,0);
                matPreTrans.put(1,2,0);
                Mat matPreTrans2=new Mat(2,3,CvType.CV_64FC1);
                matPreTrans2.put(0,0,Math.cos(0));
                matPreTrans2.put(0,1,-Math.sin(0));
                matPreTrans2.put(1,0,Math.sin(0));
                matPreTrans2.put(1,1,Math.cos(0));
                matPreTrans2.put(0,2,0);
                matPreTrans2.put(1,2,0);
                double preDxReal=0, preDyReal=0, preDaReal=0;
                double preDxReal2=0, preDyReal2=0, preDaReal2=0;
                int numStableChangeX = 0, numStableChangeY = 0, numStableChangeA = 0;
                int numStableChangeX2 = 0, numStableChangeY2 = 0, numStableChangeA2 = 0;

                while (numImageProcessed>=numImageReceived)
                {

                }

                Mat preImageTmp = queueImageReceived[frontImageReceived];
                frontImageReceived = (frontImageReceived+1)%lenQueue;

                Mat preImage = new Mat(preImageTmp, new Rect(0,0,preImageTmp.cols()/2,preImageTmp.rows()));
                Mat preImage2 = new Mat(preImageTmp, new Rect(preImageTmp.cols()/2,0,preImageTmp.cols()/2,preImageTmp.rows()));
                vertical_crop = HORIZONTAL_CROP*preImage.rows()/preImage.cols();

                preImageSmooth = new Mat(preImage.rows(), preImage.cols(), preImage.type());
                preImageSmooth2 = new Mat(preImage.rows(), preImage.cols(), preImage.type());

                preImageSmoothCropped = preImageSmooth.submat(vertical_crop, preImageSmooth.rows() - vertical_crop, HORIZONTAL_CROP, preImageSmooth.cols() - HORIZONTAL_CROP);
                preImageSmoothCropped2 = preImageSmooth2.submat(vertical_crop, preImageSmooth2.rows() - vertical_crop, HORIZONTAL_CROP, preImageSmooth2.cols() - HORIZONTAL_CROP);
                //-------------

                Mat imgStereo = new Mat(preImageSmoothCropped.rows(), preImageSmoothCropped.cols() * 2, preImageSmoothCropped.type());

                preImageSmoothCropped.copyTo(imgStereo.submat(0, preImageSmoothCropped.rows(), 0, preImageSmoothCropped.cols()));
                preImageSmoothCropped2.copyTo(imgStereo.submat(0, preImageSmoothCropped2.rows(), preImageSmoothCropped2.cols(), preImageSmoothCropped2.cols() * 2));
                rearImageProcessed = (rearImageProcessed+1)%lenQueue;
                queueImageProcessed[rearImageProcessed] = imgStereo;
                numImageProcessed++;

                bitmap = Bitmap.createBitmap(imgStereo.cols(), imgStereo.rows(), Bitmap.Config.RGB_565);

                Mat preImageGray=new Mat(preImage.rows(),preImage.cols(), CvType.CV_8UC3), preImageGray2=new Mat(preImage.rows(),preImage.cols(), CvType.CV_8UC3);
                Imgproc.cvtColor(preImage,preImageGray,Imgproc.COLOR_BGR2GRAY);
                Imgproc.cvtColor(preImage2,preImageGray2,Imgproc.COLOR_BGR2GRAY);
                Mat curImageGray = preImageGray.clone();
                Mat curImageGray2 = preImageGray2.clone();

                while (!terminated) {
                    Mat matTrans;
                    Mat matTrans2;

                    if (numImageProcessed<numImageReceived) {
                        Mat curImageTmp = queueImageReceived[frontImageReceived];
                        frontImageReceived = (frontImageReceived + 1) % lenQueue;

                        Mat curImage = new Mat(curImageTmp, new Rect(0, 0, curImageTmp.cols() / 2, curImageTmp.rows()));
                        Mat curImage2 = new Mat(curImageTmp, new Rect(curImageTmp.cols() / 2, 0, curImageTmp.cols() / 2, curImageTmp.rows()));

                        Imgproc.cvtColor(curImage, curImageGray, Imgproc.COLOR_BGR2GRAY);
                        Imgproc.cvtColor(curImage2, curImageGray2, Imgproc.COLOR_BGR2GRAY);

                        matTrans = Video.estimateRigidTransform(preImageGray, curImageGray, false);
                        matTrans2 = Video.estimateRigidTransform(preImageGray2, curImageGray2, false);
                        if (matTrans.empty()) {
                            matPreTrans.copyTo(matTrans);
                        } else {
                            matTrans.copyTo(matPreTrans);
                        }
                        if (matTrans2.empty()) {
                            matPreTrans2.copyTo(matTrans2);
                        } else {
                            matTrans2.copyTo(matPreTrans2);
                        }
                        dxReal = matTrans.get(0, 2)[0];
                        dyReal = matTrans.get(1, 2)[0];
                        daReal = Math.atan2(matTrans.get(1, 0)[0], matTrans.get(0, 0)[0]);

                        dxReal2 = matTrans2.get(0, 2)[0];
                        dyReal2 = matTrans2.get(1, 2)[0];
                        daReal2 = Math.atan2(matTrans2.get(1, 0)[0], matTrans2.get(0, 0)[0]);

                        double maxDx = 70, maxDy = 70, maxDa = 30;
                        double weightPastX, weightPastY, weightPastA;
                        double weightPastX2, weightPastY2, weightPastA2;

                        if ((Math.abs(dxReal) >= maxDx) || (Math.abs(dyReal) >= maxDy) || (Math.abs(daReal) >= maxDa) || (Math.abs(dxReal2) >= maxDx) || (Math.abs(dyReal2) >= maxDy) || (Math.abs(daReal2) >= maxDa)) {
                            dxReal = 0;
                            dyReal = 0;
                            daReal = 0;

                            xReal = 0;
                            yReal = 0;
                            aReal = 0;

                            xSmooth = 0;
                            ySmooth = 0;
                            aSmooth = 0;

                            dxReal2 = 0;
                            dyReal2 = 0;
                            daReal2 = 0;

                            xReal2 = 0;
                            yReal2 = 0;
                            aReal2 = 0;

                            xSmooth2 = 0;
                            ySmooth2 = 0;
                            aSmooth2 = 0;

                            numStableChangeX = 0;
                            numStableChangeY = 0;
                            numStableChangeA = 0;

                            numStableChangeX2 = 0;
                            numStableChangeY2 = 0;
                            numStableChangeA2 = 0;
                        } else {
                            xReal += dxReal;
                            yReal += dyReal;
                            aReal += daReal;
                            xReal2 += dxReal2;
                            yReal2 += dyReal2;
                            aReal2 += daReal2;
                        }

                        if ((preDxReal * dxReal) >= 0) {
                            numStableChangeX++;
                        } else {
                            numStableChangeX = 0;
                        }
                        if ((preDyReal * dyReal) >= 0) {
                            numStableChangeY++;
                        } else {
                            numStableChangeY = 0;
                        }
                        if ((preDaReal * daReal) >= 0) {
                            numStableChangeA++;
                        } else {
                            numStableChangeA = 0;
                        }

                        if ((preDxReal2 * dxReal2) >= 0) {
                            numStableChangeX2++;
                        } else {
                            numStableChangeX2 = 0;
                        }
                        if ((preDyReal2 * dyReal2) >= 0) {
                            numStableChangeY2++;
                        } else {
                            numStableChangeY2 = 0;
                        }
                        if ((preDaReal2 * daReal2) >= 0) {
                            numStableChangeA2++;
                        } else {
                            numStableChangeA2 = 0;
                        }

                        double paramSmoothHigh = 0.9, paramSmoothLow = 0.5;
                        double weightStableChangeX, weightStableChangeY, weightStableChangeA;
                        double weightStableChangeX2, weightStableChangeY2, weightStableChangeA2;
                        weightStableChangeX = ((double) Math.min(numStableChangeX, 20)) / 20.0;
                        weightPastX = weightStableChangeX * paramSmoothLow + (1 - weightStableChangeX) * paramSmoothHigh;
                        weightStableChangeY = ((double) Math.min(numStableChangeY, 20)) / 20.0;
                        weightPastY = weightStableChangeY * paramSmoothLow + (1 - weightStableChangeY) * paramSmoothHigh;
                        weightStableChangeA = ((double) Math.min(numStableChangeA, 20)) / 20.0;
                        weightPastA = weightStableChangeA * paramSmoothLow + (1 - weightStableChangeA) * paramSmoothHigh;

                        xSmooth = (weightPastX * xSmooth + (1 - weightPastX) * xReal);
                        ySmooth = (weightPastY * ySmooth + (1 - weightPastY) * yReal);
                        aSmooth = (weightPastA * aSmooth + (1 - weightPastA) * aReal);

                        xSmooth = (weightPastX * xSmooth + (1 - weightPastX) * xReal);
                        ySmooth = (weightPastY * ySmooth + (1 - weightPastY) * yReal);
                        aSmooth = (weightPastA * aSmooth + (1 - weightPastA) * aReal);

                        weightStableChangeX2 = ((double) Math.min(numStableChangeX2, 20)) / 20.0;
                        weightPastX2 = weightStableChangeX2 * paramSmoothLow + (1 - weightStableChangeX2) * paramSmoothHigh;
                        weightStableChangeY2 = ((double) Math.min(numStableChangeY2, 20)) / 20.0;
                        weightPastY2 = weightStableChangeY2 * paramSmoothLow + (1 - weightStableChangeY2) * paramSmoothHigh;
                        weightStableChangeA2 = ((double) Math.min(numStableChangeA2, 20)) / 20.0;
                        weightPastA2 = weightStableChangeA2 * paramSmoothLow + (1 - weightStableChangeA2) * paramSmoothHigh;

                        xSmooth2 = (weightPastX2 * xSmooth2 + (1 - weightPastX2) * xReal2);
                        ySmooth2 = (weightPastY2 * ySmooth2 + (1 - weightPastY2) * yReal2);
                        aSmooth2 = (weightPastA2 * aSmooth2 + (1 - weightPastA2) * aReal2);

                        xSmooth2 = (weightPastX2 * xSmooth2 + (1 - weightPastX2) * xReal2);
                        ySmooth2 = (weightPastY2 * ySmooth2 + (1 - weightPastY2) * yReal2);
                        aSmooth2 = (weightPastA2 * aSmooth2 + (1 - weightPastA2) * aReal2);

                        double curDa, curDx, curDy;
                        double curDa2, curDx2, curDy2;

                        curDa = daReal + (aSmooth - aReal);
                        curDx = dxReal + (xSmooth - xReal);
                        curDy = dyReal + (ySmooth - yReal);

                        matTrans.put(0, 0, Math.cos(curDa));
                        matTrans.put(0, 1, -Math.sin(curDa));
                        matTrans.put(1, 0, Math.sin(curDa));
                        matTrans.put(1, 1, Math.cos(curDa));
                        matTrans.put(0, 2, curDx);
                        matTrans.put(1, 2, curDy);

                        preDaReal = daReal;
                        preDxReal = dxReal;
                        preDyReal = dyReal;


                        curDa2 = daReal2 + (aSmooth2 - aReal2);
                        curDx2 = dxReal2 + (xSmooth2 - xReal2);
                        curDy2 = dyReal2 + (ySmooth2 - yReal2);

                        matTrans2.put(0, 0, Math.cos(curDa2));
                        matTrans2.put(0, 1, -Math.sin(curDa2));
                        matTrans2.put(1, 0, Math.sin(curDa2));
                        matTrans2.put(1, 1, Math.cos(curDa2));
                        matTrans2.put(0, 2, curDx2);
                        matTrans2.put(1, 2, curDy2);

                        preDaReal2 = daReal2;
                        preDxReal2 = dxReal2;
                        preDyReal2 = dyReal2;


                        Imgproc.warpAffine(preImage, preImageSmooth, matTrans, preImage.size());
                        Imgproc.warpAffine(preImage2, preImageSmooth2, matTrans2, preImage.size());

                        preImageSmoothCropped = preImageSmooth.submat(vertical_crop, preImageSmooth.rows() - vertical_crop, HORIZONTAL_CROP, preImageSmooth.cols() - HORIZONTAL_CROP);
                        preImageSmoothCropped2 = preImageSmooth2.submat(vertical_crop, preImageSmooth2.rows() - vertical_crop, HORIZONTAL_CROP, preImageSmooth2.cols() - HORIZONTAL_CROP);
                        //-------------

                        rearImageProcessed = (rearImageProcessed + 1) % lenQueue;
                        if (numImageProcessed<lenQueue) {
                            queueImageProcessed[rearImageProcessed] = new Mat(preImageSmoothCropped.rows(), preImageSmoothCropped.cols() * 2, preImageSmoothCropped.type());
                        }

                        preImageSmoothCropped.copyTo(queueImageProcessed[rearImageProcessed].submat(0, preImageSmoothCropped.rows(), 0, preImageSmoothCropped.cols()));
                        preImageSmoothCropped2.copyTo(queueImageProcessed[rearImageProcessed].submat(0, preImageSmoothCropped2.rows(), preImageSmoothCropped2.cols(), preImageSmoothCropped2.cols() * 2));
                        numImageProcessed++;

                        Log.i("Queue Processed","numImageReceived="+numImageReceived+", numImageProcessed="+numImageProcessed+", numImageSent="+numImageSent);

                        preImage = curImage;
                        preImage2 = curImage2;

                        curImageGray.copyTo(preImageGray);
                        curImageGray2.copyTo(preImageGray2);
                    }
                }
            }
        });
        thread.start();
    }

    @Override
    protected void onStop()
    {
        try {
            OutputStream outputStream = sock.getOutputStream();
            byte[] quitMessage = new byte[]{'Q','U','I','T'};
            outputStream.write(quitMessage,0,quitMessage.length);
            sock.close();
            socketServer.close();
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
                socketServer = new Socket("robotbase.ddns.net",socketServerPort);
                startServerMessage();
                startServerVideo();
                startStabilzation();

                while (sock==null)
                    sock = new Socket(strIP, iPort);
                Log.i(LOG_TAG, "Socket Log: Connected to video socket");

                inputStream = sock.getInputStream();
                ByteArrayOutputStream messageReceived = new ByteArrayOutputStream();
                inputStream.read(buffer, 0, 8);
                messageReceived.write(buffer, 0, 8);

                messageReceived.reset();
                inputStream.read(buffer, 0, 8);
                messageReceived.write(buffer,0,8);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inBitmap = bitmapDecoded;  // the old Bitmap that should be reused
                options.inMutable = true;
                options.inSampleSize = 1;

                while (!terminated) {
                    int numByteRead = 0;
                    messageReceived.reset();
                    inputStream.read(buffer, 0, 8);
                    messageReceived.write(buffer, 0, 8);
                    int dataLength = Integer.parseInt(messageReceived.toString());
                    int start = 0;
                    while (dataLength > 0) {
                        numByteRead = inputStream.read(buffer, start, dataLength);
                        if (numByteRead > 0) {
                            dataLength -= numByteRead;
                            start+=numByteRead;
                        } else {
                            onStop();
                            return null;
                        }
                    }

                    bitmapDecoded = BitmapFactory.decodeByteArray(buffer, 0, start, options);
                    options.inBitmap = bitmapDecoded;

                    rearImageReceived = (rearImageReceived + 1)%lenQueue;
                    if (numImageReceived<lenQueue) {
                        queueImageReceived[rearImageReceived] = new Mat(bitmapDecoded.getWidth(),bitmapDecoded.getHeight(),CvType.CV_8UC3);
                    }

                    Utils.bitmapToMat(bitmapDecoded,queueImageReceived[rearImageReceived]);
                    numImageReceived++;

                    if (numImageViewed<numImageProcessed)
                    {
                        numImageViewed++;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    imgStereoView = queueImageProcessed[frontImageProcessedViewed];
                                    frontImageProcessedViewed = (frontImageProcessedViewed+1)%lenQueue;
                                    Utils.matToBitmap(imgStereoView, bitmap);
                                    imageView.setImageBitmap(bitmap);
                                } catch (Exception e) {

                                }
                            }
                        });
                    }
                }

            }
            catch (Exception e)
            {
                Log.e("Socket Error",e.toString());
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
