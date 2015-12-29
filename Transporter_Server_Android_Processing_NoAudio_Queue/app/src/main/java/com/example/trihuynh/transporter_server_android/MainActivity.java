package com.example.trihuynh.transporter_server_android;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;


public class MainActivity extends ActionBarActivity {

    static {
        // If you use opencv 2.4, System.loadLibrary("opencv_java")
        System.loadLibrary("opencv_java");
    }

    Button btnConnect, btnExit;
    EditText texIP, texPort;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = (Button)findViewById(R.id.button_connect);
        btnExit = (Button)findViewById(R.id.button_exit);
        texIP = (EditText)findViewById(R.id.text_ip);
        texPort = (EditText)findViewById(R.id.text_port);

        btnConnect.setOnClickListener(btnConnectOnClickListener);

        /*
        Mat test = new Mat(200, 200, CvType.CV_8UC1);
        Mat m = Highgui.imread("/storage/sdcard0/Download/beardman.jpg");
        if (m.rows()>0)
        {
            Toast toast = Toast.makeText(getApplicationContext(), m.height()+"x"+m.width(), Toast.LENGTH_LONG);
            toast.show();
        }
        else
        {
            Toast toast = Toast.makeText(getApplicationContext(), "Can't open image", Toast.LENGTH_LONG);
            toast.show();
        }
        */
        //Imgproc.equalizeHist(test, test);
    }

    View.OnClickListener btnConnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(),CamViewActivity.class);
            intent.putExtra("ip",texIP.getText().toString());
            intent.putExtra("port",Integer.parseInt(texPort.getText().toString()));
            startActivity(intent);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
