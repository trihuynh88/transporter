package com.example.trihuynh.transporter_client;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


public class MainActivity extends ActionBarActivity {

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
