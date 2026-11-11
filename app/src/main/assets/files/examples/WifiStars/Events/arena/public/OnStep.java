PlayerItem me = com.star4droid.template.net.Net.myElement();
if((me != null)){
    me.getBody().setLinearVelocity((float)((joy.getJoyStickX() * 70)),(float)((joy.getJoyStickY() * 70)));
    
}
list = "";
for(int i = 0; i < (int)(com.star4droid.template.net.Net.playerCount()); i++){
    PlayerItem p = com.star4droid.template.net.Net.playerElement((int)(com.star4droid.template.net.Net.playerAt((int)(i))));
        if((p != null)){
                if((com.star4droid.template.net.Net.ownerOf(p) == 1)){
                            p.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf("4FC3F7"));
                            
                }
                        if((com.star4droid.template.net.Net.ownerOf(p) == 2)){
                                    p.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf("FF8A65"));
                                    
                        }
                                if((com.star4droid.template.net.Net.ownerOf(p) == 3)){
                                            p.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf("AED581"));
                                            
                                }
                                        if((com.star4droid.template.net.Net.ownerOf(p) == 4)){
                                                    p.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf("F06292"));
                                                    
                                        }
                                                list = (String.valueOf(list) + String.valueOf((String.valueOf(com.star4droid.template.net.Net.playerName((int)(com.star4droid.template.net.Net.ownerOf(p)))) + String.valueOf((String.valueOf(": ") + String.valueOf((String.valueOf(Math.round(com.star4droid.template.Utils.ObjectVars.getFloat(p,"score"))) + String.valueOf("\n"))))))));
                                                        if((com.star4droid.template.net.Net.isHost() && (com.star4droid.template.nav.Nav.distance(p, star) < 52))){
                                                                    com.star4droid.template.Utils.ObjectVars.add(p,"score",1);
                                                                                com.star4droid.template.nav.Nav.place(star, (float)(random((int)(90),(int)(1190))), (float)(random((int)(90),(int)(560))));
                                                                                            if((com.star4droid.template.Utils.ObjectVars.getFloat(p,"score") >= 10)){
                                                                                                            com.star4droid.template.net.Net.runForAll(String.valueOf("win"), com.star4droid.template.net.Net.playerName((int)(com.star4droid.template.net.Net.ownerOf(p))));
                                                                                                            
                                                                                            }
                                                                                            
                                                        }
                                                        
        }
        
}
scores.setItemText(String.valueOf(list));
