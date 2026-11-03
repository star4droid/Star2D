msgText.setItemText(String.valueOf("The diary says: 'Never let the candles go out.'"));
msgText.getActor().setVisible(true);
Timer.schedule(new Timer.Task(){
    public void run(){
            msgText.getActor().setVisible(false);
    
    }
},(float)(4.5));
