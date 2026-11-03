loopSound("/ocean_waves.ogg",true);
startSound("/ocean_waves.ogg");
Timer.schedule(new Timer.Task(){
    public void run(){
            com.star4droid.template.d3.B3.playSound("/seagull_1.ogg", (float)(0.55));
    
    }
},(float)(9),(float)(9));
Timer.schedule(new Timer.Task(){
    public void run(){
            com.star4droid.template.d3.B3.playSound("/seagull_2.ogg", (float)(0.45));
    
    }
},(float)(14),(float)(14));
