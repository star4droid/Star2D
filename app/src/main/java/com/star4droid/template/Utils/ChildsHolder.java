package com.star4droid.template.Utils;

import java.util.ArrayList;

public class ChildsHolder {
	private PlayerItem playerItem;
	private ChildsHolder parent;
	public ArrayList<PlayerItem> childs=new ArrayList<>();
	public ChildsHolder(PlayerItem item){
		playerItem=item;
	}
	
	public void setParent(ChildsHolder childsHolder){
		if(parent!=null&&parent.childs.contains(this)) parent.childs.remove(this);
		parent = childsHolder;
		if(parent!=null&&!parent.childs.contains(this)){
			parent.addChild(playerItem);
		}
	}
	
	public String getName(){
		return playerItem.getParentName();
	}
	public PlayerItem getPlayerItem(){
		return playerItem;
	}
	
	public void addChild(PlayerItem item){
		if(!childs.contains(item)) childs.add(item);
		if(item.getChildsHolder().parent==null||(!item.getChildsHolder().parent.getName().equals(getName()))){
			item.getChildsHolder().setParent(this);
		}
	}
	
	public ChildsHolder getChild(String child){
		for(PlayerItem item:childs){
			ChildsHolder holder=item.getChildsHolder();
			if(holder.getName().equals(child)){
				return holder;
			} else {
				ChildsHolder holder1=holder.getChild(child);
				if(holder1!=null) return holder1;
			}
		}
		return null;
	}
}