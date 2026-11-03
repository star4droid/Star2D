msgText.setItemText(String.valueOf("Flashlight found: the light button switches it"));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(3.5));
