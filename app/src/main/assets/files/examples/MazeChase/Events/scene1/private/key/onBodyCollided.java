if(body2.getName().equals("player")){
    key.getActor().setVisible(false);
        Timer.schedule(new Timer.Task(){
            public void run(){
                key.destroy();
            }
        },(float)(0.05));
            Timer.schedule(new Timer.Task(){
                public void run(){
                    door.destroy();
                }
            },(float)(0.05));
                info.setItemText(String.valueOf("The door is open: find the star!"));
                
}
