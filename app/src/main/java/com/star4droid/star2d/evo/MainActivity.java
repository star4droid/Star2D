
package com.star4droid.star2d.evo;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
//import com.star4droid.star2d.evo.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    //private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate and get instance of binding
        //binding = ActivityMainBinding.inflate(getLayoutInflater());

        // set content view to binding's root
        setContentView(R.layout.activity_main);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //this.binding = null;
    }
}
