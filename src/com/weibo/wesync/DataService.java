package com.weibo.wesync;

import java.util.List;
import java.util.SortedSet;

import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.GroupOperationType;
import com.weibo.wesync.data.WeSyncMessage.FileData;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * Common logic for different datastores.
 * @author Eric Liang
 */
public interface DataService {
	/**
	 * Settings, storage limits
	 */
	public boolean setFolderLimit(int childLimit, int changeLimit);
	
	/**
	 * Utility operations
	 */
	// Create root folder and some property ones.
	public boolean prepareForNewUser(String username);
	public boolean newConversation(String userFrom, String userTo);
	public boolean removeFolder(String username, String folderId);
	public boolean cleanupFolder(String folderId);
	
	/**
	 * Group related
	 */
	//Retrieve all members in group @groupId 
	public SortedSet<FolderChild> members(String groupId);
	public boolean isMember(String username, String groupId);
	public boolean removeMember(String groupId, String username);
	public boolean addMember(String groupId, String username);
	//Return the folder id for group chat conversation
	public String newGroupChat(String username, String groupId);
	//Create group with members (creator excluded) by creator and return the group id
	public String createGroup(String creator, List<String> members);
	public boolean broadcastNewMessage(String groupId, String fromUser, String msgId);
	public boolean broadcastMemberChange(String groupId, GroupOperationType type, String affectedUser);
	
	/**
	 * Meta message operations
	 */
	//Store the chat message and put the index to both sender and receiver 's boxes,
	// and return the sender's meta id, null for store failure
	public String store(Meta meta);
	public String storeProperty(Meta meta);
	public String store(String folderId, Meta meta, boolean changeUnread);
	
	//Return message id
	public String storeToGroup(Meta meta, String groupId);
	
	public Meta getMetaMessageInGroup(String groupId, long id);
	public Meta getMetaMessage(String folderId, long id);
	public Meta getMetaMessage(String id);
	
	/**
	 * Folder operations
	 */
	public boolean isFolderExist( String folderId );
	public int getUnreadNumber(String folderId);
	public List<Unread> getUnreadNumber(List<String> folderIds);
	public boolean cleanupSynchronizedChanges( String folderId, String syncKey);
	
	//Retrieve all the elements
	public SortedSet<FolderChange> getFolderChanges(String folderId);
	public SortedSet<FolderChild> getChildren(String folderId);
	//Retrieve the first N elements, both beginIndex and endIndex are inclusive 
	public SortedSet<FolderChange> getFolderChanges(String folderId, int beginIndex, int endIndex);
	public SortedSet<FolderChild> getChildren(String folderId, int beginIndex, int endIndex);
	//Retrieve the following Nth element of present one(exclusive), the n is suggestive but mandatory
	public SortedSet<FolderChange> getFollowingChanges(String folderId, FolderChange change, int n);	
	public SortedSet<FolderChild> getFollowingChildren(String folderId, String childId, int n);
	//Retrieve the preceding Nth element of present one(exclusive), the n is suggestive but mandatory
	public SortedSet<FolderChange> getPrecedingChanges(String folderId, FolderChange change, int n);
	public SortedSet<FolderChild> getPrecedingChildren(String folderId, String childId, int n);
	
	//Remove and return the existing folder changes
	public SortedSet<FolderChange> removeAllFolderChanges( String folderId );
	public boolean removeFolderChange( String folderId, FolderChange change );
	/**
	 * File operations
	 */
	//@Return empty or missing slices when finishing the send operation
	public FileData store(FileData file);
	public FileData getFileByIndex(FileData index);
	public FileData getFileById(String fileId);
}
