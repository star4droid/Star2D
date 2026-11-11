if((body2.getName().equals("player") && (!(over)))){
    over = true;
        info.setItemText(String.valueOf("You found the star! Again in 3 s..."));
            com.star4droid.template.nav.Nav.stop(player);
                player.getBody().setLinearVelocity(0,0);
                player.getBody().setAngularVelocity(0);
                    Timer.schedule(new Timer.Task(){
                        public void run(){
                                    openScene("scene1");
                            
                        }
                    },(float)(3));
                    
}
