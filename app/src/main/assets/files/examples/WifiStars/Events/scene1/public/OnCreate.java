status.setItemText(String.valueOf("Host a game, or search for the games of your friends."));
Timer.schedule(new Timer.Task(){
    public void run(){
            room0.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 0));
        room0Text.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 0));
            room1.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 1));
                room1Text.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 1));
                    room2.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 2));
                        room2Text.getActor().setVisible((com.star4droid.template.net.Net.roomCount() > 2));
                            if((com.star4droid.template.net.Net.roomCount() > 0)){
                                    room0Text.setItemText(String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomName((int)(0))) + String.valueOf((String.valueOf("  (") + String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomPlayers((int)(0))) + String.valueOf(" players)"))))))));
                                    
                            }
                                if((com.star4droid.template.net.Net.roomCount() > 1)){
                                        room1Text.setItemText(String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomName((int)(1))) + String.valueOf((String.valueOf("  (") + String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomPlayers((int)(1))) + String.valueOf(" players)"))))))));
                                        
                                }
                                    if((com.star4droid.template.net.Net.roomCount() > 2)){
                                            room2Text.setItemText(String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomName((int)(2))) + String.valueOf((String.valueOf("  (") + String.valueOf((String.valueOf(com.star4droid.template.net.Net.roomPlayers((int)(2))) + String.valueOf(" players)"))))))));
                                            
                                    }
                                        list = "";
                                            for(int i = 0; i < (int)(com.star4droid.template.net.Net.playerCount()); i++){
                                                    list = (String.valueOf(list) + String.valueOf((String.valueOf(com.star4droid.template.net.Net.playerName((int)(com.star4droid.template.net.Net.playerAt((int)(i))))) + String.valueOf("\n"))));
                                                    
                                            }
                                                if((!(com.star4droid.template.net.Net.isConnected()))){
                                                        list = "-";
                                                        
                                                }
                                                    players.setItemText(String.valueOf(list));
                                                        startBtn.getActor().setVisible(com.star4droid.template.net.Net.isHost());
                                                            startBtnText.getActor().setVisible(com.star4droid.template.net.Net.isHost());
                                                                leaveBtn.getActor().setVisible(com.star4droid.template.net.Net.isConnected());
                                                                    leaveBtnText.getActor().setVisible(com.star4droid.template.net.Net.isConnected());
                                                                        hostBtn.getActor().setVisible((!(com.star4droid.template.net.Net.isConnected())));
                                                                            hostBtnText.getActor().setVisible((!(com.star4droid.template.net.Net.isConnected())));
                                                                            
    }
},(float)(0.5),(float)(0.5));
