package com.star4droid.worldbuilder.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.star4droid.worldbuilder.WorldBuilder;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration c = new AndroidApplicationConfiguration();
        c.useImmersiveMode = true;
        c.useAccelerometer = false;
        c.useCompass = false;
        c.numSamples = 2;
        c.depth = 24;
        initialize(new WorldBuilder(), c);
    }
}
