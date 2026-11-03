com.star4droid.template.d3.B3.playSound("/ui_click.ogg", (float)(0.6));
if(useJoystick){
    useJoystick = false;
        joy.getActor().setVisible(false);
            leftBtn.getActor().setVisible(true);
                rightBtn.getActor().setVisible(true);
                    ctrlText.setItemText(String.valueOf("Joystick"));
                    
} else {
    useJoystick = true;
        joy.getActor().setVisible(true);
            leftBtn.getActor().setVisible(false);
                rightBtn.getActor().setVisible(false);
                    ctrlText.setItemText(String.valueOf("Buttons"));
                    
}
