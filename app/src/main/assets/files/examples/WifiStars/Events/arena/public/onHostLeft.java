over = true;
info.setItemText(String.valueOf("The host left the game"));
Timer.schedule(new Timer.Task(){
    public void run(){
            openScene("scene1");
    
    }
},(float)(2.5));
