msgText.setItemText(String.valueOf("Old books... one is still warm, as if someone just held it."));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(3.5));
