package com.weibo.wesync.data;

import java.util.List;
import java.util.SortedSet;

import com.weibo.wesync.data.WeSyncMessage.FileData;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * @author Eric Liang
 */
public interface DataStore {
	
	/**
	 * Folder meta operations
	 */
	public boolean createFolder(String folderId);
	public boolean destroyFolder(String folderId);
	//Return the folder's max childId number, -1 means folder not exist
	public int getMaxChildId( String folderId );
	//Usually, the store will increase the folderId's maxChildId and return the new value;
	public long reserveChildId( String folderId );
	public boolean isChild(String childId, String folderId);
	
	/**
	 * Operation on changes in Folder
	 */
	public SortedSet<FolderChange> getFolderChanges(String folderId);
	//Retrieve elements from beginIndex to endIndex, both beginIndex and endIndex are inclusive 
	public SortedSet<FolderChange> getFolderChanges(String folderId, int beginIndex, int endIndex);
	public int numberOfFolderChanges(String folderId);
	public boolean addFolderChange(String folderId, FolderChange change);
	public boolean removeFolderChange(String folderId, FolderChange change);
	//Remove @Param:num elements from the start
	public boolean removeFolderChanges(String folderId, int num);
	//Remove all the elements in front of @Param:change (inclusive), do nothing if change not found
	public boolean removePrecedingFolderChange(String folderId, FolderChange change);
	public SortedSet<FolderChange> removeAllFolderChanges(String folderId);
	//Retrieve the following Nth element of present one(exclusive), the n is suggestive but mandatory
	public SortedSet<FolderChange> getFollowingChanges(String folderId, FolderChange change, int n);
	//Retrieve the preceding Nth element of present one(exclusive), the n is suggestive but mandatory
	public SortedSet<FolderChange> getPrecedingChanges(String folderId, FolderChange change, int n);
	public List<Unread> numberOfFolderChanges(List<String> folderIds);

	/**
	 * Operation on children in Folder
	 */
	public SortedSet<FolderChild> getChildren(String folderId);
	//Retrieve elements from beginIndex to endIndex, both beginIndex and endIndex are inclusive 
	public SortedSet<FolderChild> getChildren(String folderId, int beginIndex, int endIndex);
	public int numberOfChildren(String folderId);
	public boolean addChild(String folderId, String childId, long score);
	public boolean removeChild(String folderId, String child);
	//Remove @Param:num elements from the start
	public boolean removeChildren(String folderId, int num);
	//Remove all the elements in front of @Param:change (inclusive), do nothing if child not found
	public boolean removePrecedingChildren(String folderId, FolderChild child);
	public boolean removeAllChildren(String folderId);
	//Retrieve the following N elements of present one(exclusive), the n is suggestive but mandatory	
	public SortedSet<FolderChild> getFollowingChildren(String folderId, String childId, int n);
	//Retrieve the preceding N elements of present one(exclusive), the n is suggestive but mandatory	
	public SortedSet<FolderChild> getPrecedingChildren(String folderId,String childId, int n);
	
	/**
	 * Operations on Meta messages
	 */
	public Meta getMetaMessage(String metaMsgId);
	public boolean addMetaMessage(Meta msg);
	public boolean removeMetaMessage(Meta msg);

	/**
	 * Operations on Files
	 */
	/*
	 * Store the file, complete file will store persistently while caching partial file data. 
	 * @return empty FileData( with file id only ) if sending file completed or also contains missing slices 
	 */
	public FileData storeFile(FileData fileData);
	
	/*
	 * Get file slices indicated by @param fileData
	 */
	public FileData getFileByIndex(FileData fileIndex);
	public FileData getFileById(String fileId);
}
