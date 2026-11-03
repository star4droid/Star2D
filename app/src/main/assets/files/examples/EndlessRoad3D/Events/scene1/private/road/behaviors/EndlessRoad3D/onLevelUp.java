levelText.setItemText(String.valueOf((String.valueOf("Level ") + String.valueOf(level))));
Timer.schedule(new Timer.Task(){
    public void run(){
            levelText.setItemText(String.valueOf(""));
    
    }
},(float)(2));
