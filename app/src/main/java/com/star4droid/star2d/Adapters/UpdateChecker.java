package com.star4droid.star2d.Adapters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.ConfirmDialog;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String CURRENT_VERSION = "0.1.7";
    private static final String CHECK_UPDATE_URL = "https://raw.githubusercontent.com/star4droid/Star2D/refs/heads/master/assets/latest.version"; 
    private static final String UPDATE_PAGE_URL = "https://github.com/star4droid/Star2D/releases/"; 

    public static void checkForUpdate(TestApp testApp) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        
        executorService.execute(() -> {
            String latestVersion = fetchLatestVersion();
            if(latestVersion!=null)
				com.badlogic.gdx.Gdx.files.external("logs/update/latest.txt").writeString("version : "+latestVersion+"\n",false);
            Gdx.app.postRunnable(() -> {
                if (latestVersion != null && !latestVersion.equals(CURRENT_VERSION)) {
                    Gdx.files.external("logs/update/"+CURRENT_VERSION+".txt").writeString(latestVersion,false);
					showUpdateDialog(testApp);
                }
            });
        });
    }

    private static String fetchLatestVersion() {
        String response = null;
		FileHandle folder = Gdx.files.external("logs/update");
		FileHandle version = folder.child(CURRENT_VERSION+".txt");
		if(folder.exists() && folder.isDirectory() && version.exists() && !version.readString().equals("")){
			return version.readString();
		} else if(folder.exists() && folder.isDirectory())
			folder.deleteDirectory();
        try {
            URL url = new URL(CHECK_UPDATE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();
                response = result.toString().trim();
            } else {
                com.badlogic.gdx.Gdx.files.external("logs/update/log.txt").writeString("response not ok\n",true);
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
			if(!e.toString().toLowerCase().contains("no address"))
            	com.badlogic.gdx.Gdx.files.external("logs/update/log.txt").writeString("Error : "+e.toString()+"\n",true);
           // throw new RuntimeException(e.toString());
        }
        return response;
    }

    private static void showUpdateDialog(TestApp app) {
		if(app.getProjectsStage()!=null)
		new ConfirmDialog("Update Available","Update Available!\nDo you want to update ?",ok->{
			if(ok){
				Gdx.net.openURI(UPDATE_PAGE_URL);
			}
		}).show(app.getProjectsStage());
		/*new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_needed))
                .setMessage(context.getString(R.string.update_needed))
                .setCancelable(false)
                .setNegativeButton(context.getString(R.string.close), (dialog, which) -> dialog.dismiss())
                .setPositiveButton(context.getString(R.string.update), (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_PAGE_URL));
                    context.startActivity(browserIntent);
                })
                .show();
		*/
    }
}
