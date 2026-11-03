flashBtn.getActor().setVisible(true);
msgText.setItemText(String.valueOf("Flashlight found: tap the light button"));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(3.5));
