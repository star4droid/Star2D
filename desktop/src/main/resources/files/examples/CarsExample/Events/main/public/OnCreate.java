String car = getValue("car").replace("c","");
int cn = 3;
if(!car.equals("")){
cn = toInt(car);
}

switch(cn){
case 1:
Car = Car1;
BW = BW1;
FW = FW1;
bwj = bwj1;
break;
case 2:
Car = Car2;
BW = BW2;
FW = FW2;
bwj = bwj2;
break;
case 4:
Car = Car4;
BW = BW4;
FW = FW4;
bwj = bwj4;
break;
case 5:
Car = Car5;
BW = BW5;
FW = FW5;
bwj = bwj5;
break;
}
Car.getActor().setVisible(true);
FW.getActor().setVisible(true);
BW.getActor().setVisible(true);

Car.getBody().setActive(true);
FW.getBody().setActive(true);
BW.getBody().setActive(true);
String wheel = getValue("wheel");
if(!wheel.equals("")){
setImage(BW,wheel);
setImage(FW,wheel);
}
