package com.weibo.wesync.data;

/**
 * Utility operations on group
 * 
 * @author Eric Liang
 *
 */
public final class Group {
	public final static String ID_PREFIX = "G"; 
	public final static char ID_SPLIT = '$'; //different with folder split
	
	//Properties
	public final static String PROP_MEMBERS = ID_PREFIX+"Members"; 
	public final static String PROP_HISTORY = ID_PREFIX+"History"; 
	
	public static String generateId(String creator, long sequence){
		return ID_PREFIX+ID_SPLIT+creator+ID_SPLIT+sequence;
	}
	public static boolean isGroupID(String id){
		return id.startsWith(ID_PREFIX+ID_SPLIT);
	}

	public static String memberFolderId(String groupId){
		return FolderID.onProperty(groupId, PROP_MEMBERS);
	}
	public static String historyFolderId(String groupId){
		return FolderID.onProperty(groupId, PROP_HISTORY);
	}
}
