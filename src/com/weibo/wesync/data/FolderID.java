package com.weibo.wesync.data;

/**
 * Folder id rules
 * @author Eric Liang
 */
public final class FolderID {
	public enum Type{
		Root, //the root, one per user
		Property, //contact, user info, etc. which can't be removed or cleared by user
		Conversation, //the conversation message store
		//FIXME folderID logic should be outside of WeSync, to be re-factored
		Conversation2, //slightly different type with conversation for LeavingMessage box
		Group, //store group members information
		Data, //sub-folder to store data slices
		Unknown
	}
	
	public final static char FOLDER_SPLIT = '-';
	private static String ROOT_TAG = "root";
	private static String PROP_TAG = "prop";
	private static String CONV_TAG = "conv";
	private static String CON2_TAG = "con2";
	private static String GROU_TAG = "grou";
	private static String DATA_TAG = "data";
	
	public String prefix="";
	public Type type;
	public String suffix="";
	
	public FolderID(String folderId){
		type = Type.Unknown;
		int idx1 = folderId.indexOf(FOLDER_SPLIT);
		if( idx1 >= 0 ){
			prefix = folderId.substring(0, idx1);

			int idx2 = folderId.indexOf(FOLDER_SPLIT, idx1+1);
			if (idx2 < 0) {
				if (folderId.substring(idx1 + 1).equals(ROOT_TAG)) {
					type = Type.Root;
				}
			} else {
				suffix = folderId.substring(idx2+1);
				String tag = folderId.substring(idx1+1, idx2);
				if (tag.equals(CONV_TAG)) type = Type.Conversation;
				else if (tag.equals(CON2_TAG)) type = Type.Conversation2;
				else if (tag.equals(PROP_TAG)) type = Type.Property;
				else if (tag.equals(GROU_TAG)) type = Type.Group;
				else if (tag.equals(DATA_TAG)) type = Type.Data;
				else ;
			}
		}
	}
	
	public String toString(){
		switch( type ){
		case Root:
			return onRoot(prefix);
		case Property:
			return onProperty(prefix, suffix);
		case Conversation:
			return onConversation(prefix, suffix);
		case Conversation2:
			return onConversation2(prefix, suffix);
		case Group:
			return onGroup(prefix, suffix);
		case Data:
			return onData(prefix, suffix);
		default:
			return prefix+FOLDER_SPLIT+"unknown";
		}
	}
	
	public static String onData(String username, String dataId) {
		return createFolderId(username, DATA_TAG, dataId);
	}

	public static String onRoot(String username) {
		return username+FOLDER_SPLIT+ROOT_TAG;
	}

	public static String onProperty(String username, String propName) {
		return createFolderId(username, PROP_TAG, propName);
	}

	public static String onConversation(String username, String userChatWith) {
		return createFolderId(username, CONV_TAG, userChatWith);
	}
	
	public static String onConversation2(String username, String userChatWith) {
		return createFolderId(username, CON2_TAG, userChatWith);
	}
	
	public static String onGroup(String username, String groupId){
		return createFolderId(username, GROU_TAG, groupId);
	}
	
	public static String getUsername(String folderId){
		return getPrefix(folderId);
	}
	public static String getUsername(FolderID fid){
		return fid.prefix;
	}
	
	public static boolean isBelongTo(String folderId, String username){
		if( username.equals( getUsername(folderId) )) return true;
		return false;
	}
	public static boolean isBelongTo(FolderID folderId, String username){
		if( username.equals( getUsername(folderId) )) return true;
		return false;
	}
	
	public static Type getType(String folderId){
		int idx1 = folderId.indexOf(FOLDER_SPLIT);
		if( idx1 < 0 ) return Type.Unknown;
		
		int idx2 = folderId.indexOf(FOLDER_SPLIT, idx1+1);
		if( idx2 < 0 ) {
			if( folderId.substring(idx1+1).equals(ROOT_TAG) ) return Type.Root;
		}else{
			String tag = folderId.substring(idx1+1, idx2);
			if( tag.equals(CONV_TAG) ) return Type.Conversation;
			if( tag.equals(CON2_TAG) ) return Type.Conversation2;
			if( tag.equals(PROP_TAG) ) return Type.Property;
			if( tag.equals(GROU_TAG) ) return Type.Group;
			if( tag.equals(DATA_TAG) ) return Type.Data;
		}
		
		return Type.Unknown;
	}
	
	public static String getUserChatWith(String folderId){
		FolderID fid = new FolderID(folderId);
		if( fid.type.equals(Type.Conversation) ){
			return getSuffix( fid, Type.Conversation );
		}else{
			return getSuffix( fid, Type.Conversation2 );
		}
	}
	public static String getProperty(String folderId){
		return getSuffix( new FolderID(folderId), Type.Property );
	}
	
	public static String getGroup(String folderId){
		return getSuffix( new FolderID(folderId), Type.Group );
	}
	
	//Usually the FolderID instance will be more efficient than the static way.
	public static String getUserChatWith(FolderID fid){
		if( fid.type.equals(Type.Conversation) ){
			return getSuffix( fid, Type.Conversation );
		}else{
			return getSuffix( fid, Type.Conversation2 );
		}
	}
	public static String getProperty(FolderID fid){
		return getSuffix(fid, Type.Property);
	}
	public static String getGroup(FolderID fid){
		return getSuffix(fid, Type.Group);
	}
	
	//Private utilities
	private static String createFolderId(String prefix, String tag, String suffix){
		return prefix+FOLDER_SPLIT+tag+FOLDER_SPLIT+suffix;
	}
	
	private static String getPrefix(String folderId){
		int idx = folderId.indexOf(FOLDER_SPLIT);
		if( idx <= 0 ) return "";
		return folderId.substring(0, idx);
	}
	private static String getSuffix(FolderID fid, Type check){
		if( fid.type.equals(check) ) return fid.suffix;
		return "";
	}
}
