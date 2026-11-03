com.star4droid.template.d3.B3.playSound("/crash_impact.ogg", (float)(1));
com.star4droid.template.d3.B3.shake(null, (float)(0.5));
crashes += 1;
crashText.setItemText(String.valueOf((String.valueOf((String.valueOf("Crashes ") + String.valueOf(crashes))) + String.valueOf(" / 3"))));
if((crashes >= 3)){
    {
        com.star4droid.template.behaviors.lib.Car3D __bhv = (com.star4droid.template.behaviors.lib.Car3D) playerCar.getBehavior("Car3D");
        if (__bhv != null) __bhv.setGas((float)(-0.005));
    }
        {
            com.star4droid.template.behaviors.lib.Car3D __bhv = (com.star4droid.template.behaviors.lib.Car3D) playerCar.getBehavior("Car3D");
            if (__bhv != null) __bhv.setHandbrake((true));
        }
            {
                com.star4droid.template.behaviors.lib.EndlessRoad3D __bhv = (com.star4droid.template.behaviors.lib.EndlessRoad3D) road.getBehavior("EndlessRoad3D");
                if (__bhv != null) __bhv.setPaused((true));
            }
                overText.getActor().setVisible(true);
                    restartBtn.getActor().setVisible(true);
                        restartText.getActor().setVisible(true);
                        
}
