package com.star4droid.star2d.Activities;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.Service;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.star4droid.star2d.CodeEditor.MyIndexer;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.evo.R;
import android.content.Intent;
import com.star4droid.star2d.evo.star2dApp;
/*
    I don't use this class, but it should index java files and libraries on the background...
*/
public class IndexingService extends Service {
	
	public void onCreate(){
		super.onCreate();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
	    return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		NotificationManagerCompat managerCompat= NotificationManagerCompat.from(this);
		managerCompat.cancel(1);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Start foreground notification
		String channelID="Indexing Service";
		NotificationChannel channel = new NotificationChannel(channelID, channelID, NotificationManager.IMPORTANCE_MAX);
		channel.setDescription("To Index the jar files");
		
		NotificationManagerCompat managerCompat= NotificationManagerCompat.from(this);
		managerCompat.createNotificationChannel(channel);
		
		Notification notification = new NotificationCompat.Builder(star2dApp.getContext(), new Notification())
		.setContentTitle("Indexing Service")
		.setContentText("Running...")
		.setSmallIcon(R.drawable.icon)
		.setSilent(true)
		.setOngoing(true)
		.setChannelId(channelID)
		.setPriority(NotificationCompat.PRIORITY_HIGH)
		.build();
		startForeground(1, notification);
		new MyIndexer().indexFiles(Editor.getCurrentEditor(),star2dApp.getContext());
		return START_STICKY;
	}

}