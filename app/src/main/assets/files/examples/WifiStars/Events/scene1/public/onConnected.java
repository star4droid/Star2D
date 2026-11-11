if(com.star4droid.template.net.Net.isHost()){
    status.setItemText(String.valueOf("Your game is open: friends can find it now."));
    
} else {
    status.setItemText(String.valueOf("Joined! Waiting for the host to start..."));
    
}
