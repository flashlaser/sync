package com.weibo.wesync;

import java.util.Calendar;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.weibo.wesync.data.DataStore;
import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.Group;
import com.weibo.wesync.data.GroupOperationType;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.SyncKey;
import com.weibo.wesync.data.WeSyncMessage.FileData;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * @author Eric Liang
 */
public class DataServiceImpl implements DataService {
	protected Logger log = LoggerFactory.getLogger(DataServiceImpl.class);

	private int childLimit = 100;
	private int changeLimit = 100;
	
	public DataStore dataStore;

	public DataServiceImpl(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	@Override
	public boolean prepareForNewUser(String username) {
		String rootId = FolderID.onRoot(username);
		dataStore.createFolder(rootId);
		return true;
	}

	@Override
	public boolean newConversation(String userFrom, String userTo) {
		String convId = FolderID.onConversation(userFrom, userTo);
		dataStore.createFolder(convId);

		// Add to userFrom's root folder
		String fromRoot = FolderID.onRoot(userFrom);
		dataStore.addChild(fromRoot, convId, 0);

		return true;
	}

	private long reserveChildId(String folderId) {
		long newId = dataStore.reserveChildId(folderId);

		// Add DateStamp for shard in DB, the MMDD.
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		int stamp = cal.get(Calendar.YEAR) % 100 * 10000
				+ (cal.get(Calendar.MONTH) + 1) * 100
				+ cal.get(Calendar.DAY_OF_MONTH);

		return newId * 1000000 + stamp;
	}

	@Override
	public String storeProperty(Meta meta) {
		// Should come from the property service
		if (!meta.hasFrom() || !meta.hasTo()) {
			log.error("Can not store incomplete property meta message : "
					+ meta.getId());
			return null;
		}

		String childId = meta.getId();
		String folderId = FolderID.onProperty(meta.getFrom(), meta.getTo());
		dataStore.addChild(folderId, childId, 0);
		
		fixRootFolder(folderId, meta.getFrom(), true);
		
		return childId;
	}
	
	public String store(String folderId, Meta meta, boolean changeUnread){
		String user = new FolderID(folderId).prefix;
		
		String dataFolderId = null;
		if (meta.hasSpanId() ) {
			if( MetaMessageType.subfolder.equals(MetaMessageType.valueOf(meta.getType().byteAt(0))) ){
				dataStore.createFolder( FolderID.onData(user, meta.getSpanId()) );
			}else{
				dataFolderId = FolderID.onData(user, meta.getSpanId() );
			}
		}

		long childId = meta.hasPrestoreId() ? meta.getPrestoreId() : reserveChildId(folderId);
		String childIdStr = String.valueOf(childId);
		String msgId = FolderChild.generateId(folderId, childId);
		Meta newMeta = Meta.newBuilder(meta).setId(msgId).build();

		dataStore.addMetaMessage(newMeta);
		
		if (dataFolderId != null) {
			addChildToFolder(dataFolderId, childIdStr, childId);
		} else {
			addChildToFolder(folderId, childIdStr, childId);
		}

		if (changeUnread) {
			int changeNum = dataStore.numberOfFolderChanges(folderId);
			if( changeNum >= changeLimit ){
				dataStore.removeFolderChanges(folderId, changeNum+1-changeLimit);
			}
			dataStore.addFolderChange(folderId, new FolderChange(childIdStr, true));
			fixRootFolder(folderId, user, true);
		}

		return msgId;
	}
	
	private void addChildToFolder(String folderId, String childIdStr, long childId){
		int childNum = dataStore.numberOfChildren(folderId);
		if( childNum >= childLimit ){
			dataStore.removeChildren(folderId, childNum+1-childLimit);
		}
		dataStore.addChild(folderId, childIdStr, childId);
	}
		
	@Override
	public String store(Meta meta) {
		if (MetaMessageType.valueOf(meta.getType().byteAt(0)).equals(
				MetaMessageType.property)) {
			return storeProperty(meta);
		}

		String senderId = storeToSenderBox(meta);
		storeToReceiverBox(meta);

		return senderId;
	}

	private String storeToSenderBox(Meta meta) {
		String folderId = FolderID.onConversation(meta.getFrom(), meta.getTo());
		return store(folderId, meta, false);
	}

	private String storeToReceiverBox(Meta meta) {
		String folderId = FolderID.onConversation(meta.getTo(), meta.getFrom());
		return store(folderId, meta, true);
	}
	
	@Override
	public String storeToGroup(Meta meta, String groupId) {
		String chatHistoryFolderId = Group.historyFolderId(groupId);
		
		String dataFolderId = null;
		if (meta.hasSpanId() ) {
			if( MetaMessageType.subfolder.equals(MetaMessageType.valueOf(meta.getType().byteAt(0))) ){
				dataStore.createFolder( FolderID.onData(groupId, meta.getSpanId()) );
			}else{
				dataFolderId = FolderID.onData(groupId, meta.getSpanId() );
			}
		}
		
		long childId = reserveChildId(chatHistoryFolderId);
		String childIdStr = String.valueOf(childId);

		String msgId = FolderChild.generateId(chatHistoryFolderId, childId);
		Meta newMeta = Meta.newBuilder(meta).setId(msgId).build();

		dataStore.addMetaMessage(newMeta);
		if (dataFolderId != null) {
			addChildToFolder(dataFolderId, childIdStr, childId);
		} else {
			addChildToFolder(chatHistoryFolderId, childIdStr, childId);
		}

		return childIdStr;
	}

	@Override
	public Meta getMetaMessageInGroup(String groupId, long id) {
		String chatHistoryFolderId = Group.historyFolderId(groupId);
		String msgId = FolderChild.generateId(chatHistoryFolderId,Long.valueOf(id));
		return getMetaMessage(msgId);
	}
	
	@Override
	public Meta getMetaMessage(String folderId, long id) {
		String msgId = FolderChild.generateId(folderId, id);
		return getMetaMessage(msgId);
	}
	
	@Override
	public Meta getMetaMessage(String id) {
		return dataStore.getMetaMessage(id);
	}

	@Override
	public FileData store(FileData file) {
		return dataStore.storeFile(file);
	}

	@Override
	public FileData getFileByIndex(FileData index) {
		return dataStore.getFileByIndex(index);
	}

	@Override
	public FileData getFileById(String fileId) {
		return dataStore.getFileById(fileId);
	}

	@Override
	public int getUnreadNumber(String folderId) {
		return dataStore.numberOfFolderChanges(folderId);
	}

	@Override
	public SortedSet<FolderChange> getFolderChanges(String folderId) {
		return dataStore.getFolderChanges(folderId);
	}

	@Override
	public SortedSet<FolderChild> getChildren(String folderId) {
		String storeFolderId = getStoreFolderId(folderId);
		return dataStore.getChildren(storeFolderId);
	}

	@Override
	public boolean cleanupSynchronizedChanges(String folderId, String syncKey) {
		if (!SyncKey.isEmpty(syncKey)) {
			String changeStr = SyncKey.getChangeString(syncKey);
			FolderChange change = FolderChange.fromString(changeStr);
			dataStore.removePrecedingFolderChange(folderId, change);
		}
		return true;
	}

	@Override
	public SortedSet<FolderChange> getFolderChanges(String folderId,
			int beginIndex, int endIndex) {
		return dataStore.getFolderChanges(folderId, beginIndex, endIndex);
	}

	@Override
	public SortedSet<FolderChild> getChildren(String folderId, int beginIndex,
			int endIndex) {
		String storeFolderId = getStoreFolderId(folderId);
		return dataStore.getChildren(storeFolderId, beginIndex, endIndex);
	}

	@Override
	public SortedSet<FolderChange> getFollowingChanges(String folderId,
			FolderChange change, int n) {
		return dataStore.getFollowingChanges(folderId, change, n);
	}

	@Override
	public SortedSet<FolderChild> getFollowingChildren(String folderId,
			String childId, int n) {
		String storeFolderId = getStoreFolderId(folderId); 
		return dataStore.getFollowingChildren(storeFolderId, childId, n);
	}

	@Override
	public List<Unread> getUnreadNumber(List<String> folderIds) {
		return dataStore.numberOfFolderChanges(folderIds);
	}

	@Override
	public boolean isFolderExist(String folderId) {
		return dataStore.getMaxChildId(folderId) != -1;
	}

	@Override
	public boolean cleanupFolder(String folderId) {
		dataStore.removeAllFolderChanges(folderId);
		return dataStore.removeAllChildren(folderId);
	}

	@Override
	public boolean removeFolder(String username, String folderId) {
		dataStore.destroyFolder(folderId);
		return dataStore.removeChild(FolderID.onRoot(username), folderId);
	}

	@Override
	public SortedSet<FolderChange> getPrecedingChanges(String folderId,
			FolderChange change, int n) {
		return dataStore.getPrecedingChanges(folderId, change, n);
	}
	
	@Override
	public SortedSet<FolderChild> getPrecedingChildren(String folderId,
			String childId, int n) {
		String storeFolderId = getStoreFolderId(folderId); 
		return dataStore.getPrecedingChildren(storeFolderId, childId, n);
	}

	@Override
	public String createGroup(String creator, List<String> members) {
		String creatorRoot = FolderID.onRoot(creator);
		long groupIdScore = dataStore.reserveChildId(creatorRoot);

		String groupId = Group.generateId(creator, groupIdScore);
		String memberPropId = Group.memberFolderId(groupId);

		dataStore.createFolder(memberPropId);
		// TODO change the score?
		dataStore.addChild(memberPropId, creator, 0);
		// FIXME batch operations
		for (String m : members) {
			dataStore.addChild(memberPropId, m, 1);
		}

		fixRootFolder(memberPropId, creator, false);
		// also broadcast to members
		for (String m : members) {
			fixRootFolder(memberPropId, m, false);
		}

		return groupId;
	}

	@Override
	public SortedSet<FolderChild> members(String groupId) {
		return dataStore.getChildren(Group.memberFolderId(groupId));
	}

	@Override
	public String newGroupChat(String username, String groupId) {
		String convId = FolderID.onGroup(username, groupId);
		dataStore.createFolder(convId);

		// Add to userFrom's root folder
		String fromRoot = FolderID.onRoot(username);
		dataStore.addChild(fromRoot, convId, 0);

		return convId;
	}

	@Override
	public boolean isMember(String username, String groupId) {
		return dataStore.isChild(username, Group.memberFolderId(groupId));
	}

	private void sendNoticeToGroupMember(String groupId, String fromUser, String msgId, String toUser){
		String convId = FolderID.onGroup(toUser, groupId);
		if (!toUser.equals(fromUser)) {
			// Change unread if not sender
			dataStore.addFolderChange(convId, new FolderChange(msgId, true));
			fixRootFolder(convId, toUser, true);
		}
	}
	
	private void fixRootFolder(String folderId, String username, boolean checkExistence){
		String rootId = FolderID.onRoot(username);
		if( !checkExistence || !dataStore.isChild(folderId, rootId) ){
			long childFolderId = reserveChildId(rootId);
			dataStore.addChild(rootId, folderId, childFolderId);
		}
		dataStore.addFolderChange(rootId, new FolderChange(folderId, true));
	}
	@Override
	public boolean broadcastNewMessage(String groupId, String fromUser,
			String msgId) {
		// broadcast to members
		SortedSet<FolderChild> members = members(groupId);
		for (FolderChild fc : members) {
			sendNoticeToGroupMember(groupId, fromUser, msgId, fc.id);
		}
		return true;
	}

	@Override
	public boolean broadcastMemberChange(String groupId, GroupOperationType type, String affectedUser) {
		String memberPropId = Group.memberFolderId(groupId);
		SortedSet<FolderChild> members = members(groupId);
		for (FolderChild fc : members) {
			if( type.equals(GroupOperationType.addMember) ){
				fixRootFolder( memberPropId, fc.id, false);
			}else{
				String rootId = FolderID.onRoot(fc.id);
				dataStore.addFolderChange(rootId,new FolderChange(memberPropId, true));
			}
		}
		
		if( type.equals(GroupOperationType.removeMember)
				|| type.equals( GroupOperationType.quitGroup) ){
			String affectedUserRootId = FolderID.onRoot(affectedUser);
			dataStore.removeChild(affectedUserRootId, memberPropId);
		}
		
		return true;
	}

	@Override
	public boolean removeMember(String groupId, String username) {
		String folderId = Group.memberFolderId(groupId);
		return dataStore.removeChild(folderId, username);
	}

	@Override
	public boolean addMember(String groupId, String username) {
		String folderId = Group.memberFolderId(groupId);
		return dataStore.addChild(folderId, username, dataStore.reserveChildId(folderId));
	}

	@Override
	public SortedSet<FolderChange> removeAllFolderChanges(String folderId) {
		return dataStore.removeAllFolderChanges(folderId);
	}

	@Override
	public boolean setFolderLimit(int childLimit, int changeLimit) {
		this.childLimit = childLimit;
		this.changeLimit = changeLimit;
		return true;
	}

	@Override
	public boolean removeFolderChange(String folderId, FolderChange change) {
		return dataStore.removeFolderChange(folderId, change);
	}
	
	/*
	 * Return the folder ID which stores the meta message
	 */
	private String getStoreFolderId(String folderId){
		FolderID fid = new FolderID(folderId);
		if( fid.type.equals( FolderID.Type.Group ) ){
			return Group.historyFolderId( FolderID.getGroup(fid) );
		}
		return folderId;
	}
}
