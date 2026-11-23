package com.star4droid.star2d;

import android.app.Activity;
import android.content.Intent;
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
    /**
     * Opens World Builder. With a file it edits that world (made from template, 0 island or 1 empty, when the file
     * does not exist yet) and saves it there; without one it opens the worlds kept by World Builder itself.
     */
    public static void open(Activity from, java.io.File file, int template) {
        Intent i = new Intent(from, WorldBuilderActivity.class);
        if (file != null) i.putExtra("file", file.getAbsolutePath());
        i.putExtra("template", template);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WorldIO.folder = "worldbuilder/worlds";
        Intent in = getIntent();
        WorldBuilder.openFile = in == null ? null : in.getStringExtra("file");
        int t = in == null ? -1 : in.getIntExtra("template", -1);
        WorldBuilder.newTemplate = t < 0 ? 1 : t;
        AndroidApplicationConfiguration c = new AndroidApplicationConfiguration();
        c.useImmersiveMode = true;
        c.useAccelerometer = false;
        c.useCompass = false;
        c.numSamples = 2;
        c.depth = 24;
        initialize(new WorldBuilder(), c);
    }
}
