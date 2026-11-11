setGravity(0,0);
if(com.star4droid.template.net.Net.isConnected()){
    info.setItemText(String.valueOf("Collect the stars! First to 10 wins."));
        Timer.schedule(new Timer.Task(){
            public void run(){
                        if((!(over))){
                            info.setItemText(String.valueOf(""));
                            
                }
                
            }
        },(float)(2.5));
        
} else {
    info.setItemText(String.valueOf("Play from the lobby (scene1): host or join a game first."));
    
}
