package com.star4droid.star2d;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.star4droid.worldbuilder.WorldBuilder;
import com.star4droid.worldbuilder.engine.WorldIO;

/**
 * World Builder inside Star2D: the same editor as the standalone app (module worldbuilder:core).
 * Its files come from assets/wb/ and its worlds are saved in files/worldbuilder/worlds.
 */
public class WorldBuilderActivity extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WorldIO.folder = "worldbuilder/worlds";
        AndroidApplicationConfiguration c = new AndroidApplicationConfiguration();
        c.useImmersiveMode = true;
        c.useAccelerometer = false;
        c.useCompass = false;
        c.numSamples = 2;
        c.depth = 24;
        initialize(new WorldBuilder(), c);
    }
}
