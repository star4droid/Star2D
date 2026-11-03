msgText.setItemText(String.valueOf("Scratches under the bed... from the inside."));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(3.5));
