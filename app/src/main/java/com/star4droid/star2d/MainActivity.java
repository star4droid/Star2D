package com.star4droid.star2d;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

import android.os.Bundle;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.evo.R;
import java.util.Timer;
import java.util.TimerTask;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Utils.setLanguage(this);
        setContentView(R.layout.activity_main);
		EngineSettings.init(this);
		new Timer().schedule(new TimerTask(){
			@Override
			public void run() {
			    if(checkPerms(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE/*, Manifest.permission.ACCESS_MEDIA_LOCATION*/)){
				    open();
				} 
			}
		},3000);
		 
    }
    
    private void open(){
        Intent i = new Intent();
		i.setClass(MainActivity.this,com.star4droid.star2d.EditorActivity.class);
		startActivity(i);
		finish();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            open();
        }
    }
    
    public boolean checkPerms(final String... perms) {
		for(String i : perms)
        if (ContextCompat.checkSelfPermission(this, i) == PackageManager.PERMISSION_DENIED) {
            final AlertDialog dialog = new AlertDialog.Builder(this).create();
            dialog.setTitle("Warning !");
            dialog.setMessage("Give the the necessary permissions...");
            dialog.setButton(
                    AlertDialog.BUTTON_POSITIVE,
                    "Ok",
                    (arg0, arg1) -> {
                        ActivityCompat.requestPermissions(
                                MainActivity.this, perms, 1000);
                        dialog.dismiss();
                    });
            dialog.setCancelable(false);
            dialog.show();
            return false;
        }
        return true;
    }
}