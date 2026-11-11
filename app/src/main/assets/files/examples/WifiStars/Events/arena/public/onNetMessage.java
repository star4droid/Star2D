if((String.valueOf(name).equals(String.valueOf("win")) && (!(over)))){
    over = true;
        winner = data;
            info.setItemText(String.valueOf((String.valueOf(winner) + String.valueOf(" wins!"))));
                Timer.schedule(new Timer.Task(){
                    public void run(){
                                if(com.star4droid.template.net.Net.isHost()){
                                    com.star4droid.template.net.Net.loadSceneForAll("arena");
                                    
                        }
                        
                    }
                },(float)(4));
                
}
