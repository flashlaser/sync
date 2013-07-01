package com.weibo.wesync.data;

import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author Eric Liang
 */
public class Folder {
	//Meta information
	public String id;
	public int maxChildId = -1;
	
	//Changes on children, unnecessary to store in persistent databases.
	public SortedSet<FolderChange> changes; 
	
	//Root folder is the parent of the others, whose children are meta-messages;
	//Just for full-synchronization.
	public SortedSet<FolderChild> children; 
	
	public Folder(String id){
		this.id = id;
	}
	
	/*
	 * Set the unread for notice while adding child
	 */
	public void addChild(FolderChild child){
		addChildStealthily(child);
		addChange(child.id, true);
	}
	
	private void ensureChanges(){
		if( null == changes ){
			synchronized(this){
				if( null == changes ){
					changes = new ConcurrentSkipListSet<FolderChange>();
				}
			}
		}
	}
	public void addChange(String childId, boolean isAdd){
		FolderChange c = new FolderChange(childId, isAdd);
		
		ensureChanges();
		if( FolderID.getType(id).equals(FolderID.Type.Root) ){
			if ( changes.contains(c) ) return;	
		}
		
		changes.add(c);
	}
	
	public void addChildStealthily(FolderChild child){
		ensureChildren();
		children.add(child);
	}
	
	private void ensureChildren() {
		if( null == children ) {
			synchronized( this ){
				if( null == children ){
					children = new ConcurrentSkipListSet<FolderChild>();
				}
			}
		}
	}
}
