msgText.setItemText(String.valueOf("Locked. The key must be somewhere outside..."));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(3.5));
