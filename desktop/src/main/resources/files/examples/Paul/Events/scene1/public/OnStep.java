setCameraXY(player);
if(checkCollision(player,coin1)){
coin1.destroy();
coinCount+=1;
Text1.setItemText("X"+String.valueOf(coinCount));
if(coinCount==3){
openScene("win");

} else {

}

} else {

}
if(checkCollision(player,coin2)){
coin2.destroy();
coinCount+=1;
Text1.setItemText("X"+String.valueOf(coinCount));
if(coinCount==3){
openScene("win");

} else {

}

} else {

}
if(checkCollision(player,coin3)){
coin3.destroy();
coinCount+=1;
Text1.setItemText("X"+String.valueOf(coinCount));
if(coinCount==3){
openScene("win");

} else {

}

} else {

}
