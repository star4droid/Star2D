if(Gdx.input.isTouched()){
    if((!(held))){
            held = true;
                    if((!(over))){
                                com.star4droid.template.nav.Nav.goTo(player, "", (float)((getTouch().x/com.star4droid.template.Items.StageImp.WORLD_SCALE)), (float)((getTouch().y/com.star4droid.template.Items.StageImp.WORLD_SCALE)), (float)(210));
                                            com.star4droid.template.nav.Nav.place(marker, (float)((getTouch().x/com.star4droid.template.Items.StageImp.WORLD_SCALE)), (float)((getTouch().y/com.star4droid.template.Items.StageImp.WORLD_SCALE)));
                                                        marker.getActor().setVisible(true);
                                                        
                    }
                    
    }
    
} else {
    held = false;
    
}
