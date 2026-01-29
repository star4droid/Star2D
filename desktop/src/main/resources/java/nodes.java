--main
split
-color:#018253•
addSource code
<<=>>
%1$s
%2$s
split
-color:#018253•
try
<<=>>
try {
%1$s
} catch(Throwable e){}
split
-color:#018253•
void name
<<=>>
public void %1$s {
%2$s
}
split
-color:#018253•
while__star__if__ boolean(Boolean)
<<=>>
while(%1$s){
%2$s
}
%3$s
%4$s
split
-color:#018253•
if__star__if__ boolean(Boolean)
<<=>>
if(%1$s){
%2$s
} else {
%3$s
}
%4$s
split
-color:#018253•
openUrl url
<<=>>
openUrl("%1$s");
%2$s
split
-color:#018253•
OpenScene scene(scene)
<<=>>
openScene("%1$s");
%2$s
split
-color:#018253•
finish
<<=>>
finish();
%1$s
split
-color:#018253•
_DoAfter delay name
<<=>>
Timer.Task %2$s = Timer.schedule(new Timer.Task(){
    public void run(){
        %3$s
    }
},(float)(%1$s));
split
-color:#018253•
cancelTimer name
<<=>>
%1$s.stop();
split
-color:#018253•
_repeatEvery delay name
<<=>>
Timer.Task %2$s = Timer.schedule(new Timer.Task(){
    public void run(){
        %3$s
    }
},(int)(%1$s),(int)(%1$s));
split
-color:#018253•
writeToFile path string
<<=>>
write(%1$s,%2$s);
%3$s
split
-color:#018253•
writeInternal path string
<<=>>
writeInternal(%1$s,%2$s);
%3$s
split
--Camera
split
-color:#387095•
setZoom zoom
<<=>>
setZoom((float)(%1$s));
%2$s
split
-color:#387095•
cameraFollow body(Body)
<<=>>
setCameraXY(%1$s);
%2$s
split
-color:#387095•
setCameraXAt body(Body)
<<=>>
setCameraX(%1$s);
%2$s
split
-color:#387095•
setCameraYAt body(Body)
<<=>>
setCameraY(%1$s);
%2$s
split
-color:#387095•
setCameraXY x y
<<=>>
setCameraXY((float)(%1$s),(float)(%2$s));
%3$s
split
-color:#387095•
setCameraCenter x y
<<=>>
setCameraCenter((float)(%1$s),(float)(%2$s));
%3$s
split
-color:#387095•
setCameraX x
<<=>>
setCameraX((float)(%1$s));
%2$s
split
-color:#387095•
setCameraY y
<<=>>
setCameraY((float)(%1$s));
%2$s
split
-color:#387095•
setCameraOffset offsetX offsetY
<<=>>
setCameraOffset((float)(%1$s),(float)(%2$s));
%3$s
split
--Configurations
split
-color:#88213B•
setsplits splits
<<=>>
setsplits(%1$s);
%2$s
split
-color:#88213B•
ifCollision__star__if__ body1(Body) body2(Body)
<<=>>
if(checkCollision(%1$s,%2$s)){
%3$s
} else {
%4$s
}
%5$s
split
-color:#88213B•
Pause
<<=>>
Pause();
%1$s
split
-color:#88213B•
Resume
<<=>>
Resume();
%1$s
split
-color:#88213B•
saveValue key value
<<=>>
saveValue(%1$s,%2$s);
%3$s
split
-color:#88213B•
setGravity x y
<<=>>
setGravity(%1$s,%2$s);
%3$s
split
--Debug
split
-color:#88213B•
Toast message
<<=>>
toast(%1$s);
%2$s
split
-color:#88213B•
debug message
<<=>>
debug(%1$s);
%2$s
split
-color:#88213B•
--Ui Nodes
split
-color:#88213B•
setVisibility item(Body) visibility(Boolean)
<<=>>
%1$s.getActor().setVisible(%2$s);
%3$s
split
-color:#88213B•
setText ui(Body) text
<<=>>
%1$s.setItemText(%2$s);
%3$s
split
-color:#88213B•
setAlpha body(Body) alpha
<<=>>
//%1$s.setAlpha(%2$s);
//ToDo : I think can be done by :
//Color c = item.getActor().getColor();
//item.getActor.setColor(c.r,c.g,c.b,alpha);
//later ≈≈
%3$s
split
-color:#88213B•
setProgress progress(Body) value
<<=>>
%1$s.setProgress((int)(%2$s));
%3$s
split
-color:#88213B•
setMax progress(Body) max
<<=>>
%1$s.setMax((int)(%2$s));
%3$s
split
-color:#88213B•
--BodyModify
split
-color:#4A7814•
setScaleXY item(Body) scaleX scaleY
<<=>>
%1$s.getActor().setScaleX((float)(%2$s));
%1$s.getActor().setScaleY((float)(%3$s));
%4$s
split
-color:#4A7814•
setScaleX item(Body) scaleX
<<=>>
%1$s.getActor().setScaleX((float)(%2$s));
%3$s
split
-color:#4A7814•
setScaleY item(Body) scaleY
<<=>>
%1$s.getActor().setScaleY((float)(%2$s));
%3$s
split
-color:#4A7814•
setTransform body(Body) x y angle
<<=>>
%1$s.getBody().setTransform((float)(%2$s),(float)(%3$s),(float)(%4$s));
%5$s
split
-color:#4A7814•
setAnimation body(Body) animation(animation)
<<=>>
setAnimation(%1$s,"%2$s");
%3$s
split
-color:#4A7814•
removeAnimation body(Body)
<<=>>
%1$s.removeAnimation();
%2$s
split
-color:#4A7814•
setX body(Body) x
<<=>>
%1$s.getBody().setTransform((float)(%2$s),%1$s.getBody().getPosition().y,(float)(%1$s.getActor().getRotation()));
%3$s
split
-color:#4A7814•
setY body(Body) y
<<=>>
%1$s.getBody().setTransform(%1$s.getBody().getPosition().x,(float)(%2$s),(%1$s).getActor().getRotation());
%3$s
split
-color:#4A7814•
setAngle body(Body) angle
<<=>>
%1$s.getBody().setTransform(%1$s.getBody().getPosition(),(float)(%2$s));
%3$s
split
-color:#4A7814•
setImage body(Body) image(image)
<<=>>
setImage(%1$s,"%2$s");
%3$s
split
-color:#4A7814•
copyBody body(Body) var newName
<<=>>
PlayerItem %2$s = %1$s.getClone(%3$s);
%4$s
split
-color:#4A7814•
destroy body(Body)
<<=>>
%1$s.destroy();
%2$s
split
-color:#4A7814•
moveTowardBody body(Body) target step
<<=>>
//%1$s.moveTowardBody(%2$s,(float)(%3$s));
%4$s
split
-color:#4A7814•
moveTowardBody body(Body) Vector2 step
<<=>>
//%1$s.moveTowardPoint(%2$s,(float)(%3$s));
%4$s
split
--bodyControl
split
-color:#008375•
applyForceToCenter body(Body) x y wake(Boolean)
<<=>>
%1$s.getBody().applyForceToCenter((float)(%2$s),(float)(%3$s),%4$s);
%5$s
split
-color:#008375•
applyLinearImpulse body(Body) x y pointX pointY wake(Boolean)
<<=>>
%1$s.getBody().applyLinearImpulse((float)(%2$s),(float)(%3$s),(float)(%4$s),(float)(%5$s), %6$s);
%7$s
split
-color:#008375•
applyLinearImpulseToCenter body(Body) x y wake(Boolean)
<<=>>
%1$s.getBody().applyLinearImpulse((float)(%2$s),(float)(%3$s),%1$s.getBody().getWorldCenter().x,%1$s.getBody().getWorldCenter().y, %4$s);
%5$s
split
-color:#008375•
applyForce body(Body) x y pointX pointY wake(Boolean)
<<=>>
%1$s.getBody().applyForce((float)(%2$s),(float)(%3$s),(float)(%4$s),(float)(%5$s),%6$s);
%7$s
split
-color:#008375•
setAngularVelocity body(Body) velocity
<<=>>
%1$s.getBody().setAngularVelocity((float)(%2$s));
%3$s
split
-color:#008375•
applyAngularImpulse body(Body) impulse wake(Boolean)
<<=>>
%1$s.getBody().applyAngularImpulse((float)(%2$s),%3$s);
%4$s
split
-color:#008375•
setGravityScale body(Body) GravityScale
<<=>>
%1$s.getBody().setGravityScale(%2$s);
%3$s
split
-color:#008375•
setLinearVelocity body(Body) xVelocity yVelocity
<<=>>
%1$s.getBody().setLinearVelocity((float)(%2$s),(float)(%3$s));
%4$s
split
-color:#008375•
applyTorque body(Body) torque wake(Boolean)
<<=>>
%1$s.getBody().applyTorque((float)(%2$s),%3$s);
%4$s
split
-color:#008375•
setLinearDamping body(Body) value
<<=>>
%1$s.getBody().setLinearDamping((float)(%2$s));
%3$s
split
-color:#008375•
--bodyBooleans
split
-color:#008375•
setAwake body(Body) wake(Boolean)
<<=>>
%1$s.getBody().setAwake(%2$s);
%3$s
split
-color:#008375•
setBullet body(Body) bullet
<<=>>
%1$s.getBody().setBullet(%2$s);
%3$s
split
-color:#008375•
setFixedRotation body(Body) fixed
<<=>>
%1$s.getBody().setFixedRotation(%2$s);
%3$s
split
-color:#008375•
setActive body(Body) active
<<=>>
%1$s.getBody().setActive(%2$s);
%3$s
split
-color:#008375•
setSleepingAllowed body(Body) sleeping
<<=>>
%1$s.getBody().setSleepingAllowed(%2$s);
%3$s
split
--Sounds
split
-color:#BE9333•
loopSound Sound(sound) repeat?
<<=>>
loopSound("%1$s",%2$s);
%3$s
split
-color:#BE9333•
playSound Sound(sound)
<<=>>
startSound("%1$s");
%2$s
split
-color:#BE9333•
pauseSound Sound(sound)
<<=>>
pauseSound("%1$s");
%2$s
split
-color:#BE9333•
releaseSound Sound(sound)
<<=>>
releaseSound("%1$s");
%2$s
split
--Joints
split
-color:#BE9333•
setMaxMotorTorque joint torque
<<=>>
%1$s.setMaxMotorTorque(%2$s);
%3$s
split
-color:#BE9333•
setMotorSpeed joint speed
<<=>>
%1$s.setMotorSpeed(%2$s);
%3$s
split
-color:#BE9333•
setLimits joint x y
<<=>>
%1$s.setLimits(%2$s,%3$s);
%4$s
split
-color:#BE9333•
setSpringDampingRatio joint ratio
<<=>>
%1$s.setSpringDampingRatio(%2$s);
%3$s
split
-color:#BE9333•
setSpringFrequencyHz joint frequency
<<=>>
%1$s.setSpringFrequencyHz(%2$s);
%3$s
split
-color:#BE9333•
enableMotor joint enable
<<=>>
%1$s.enableMotor(%2$s);
%3$s
split
-color:#BE9333•
enableLimit joint enable
<<=>>
%1$s.enableLimit(%2$s);
%3$s