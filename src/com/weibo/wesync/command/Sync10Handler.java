package com.weibo.wesync.command;

import java.util.Calendar;
import java.util.SortedSet;
import java.util.TimeZone;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.WeSyncService;
import com.weibo.wesync.WeSyncURI;
import com.weibo.wesync.data.FolderChange;
import com.weibo.wesync.data.FolderChild;
import com.weibo.wesync.data.FolderID;
import com.weibo.wesync.data.Group;
import com.weibo.wesync.data.MetaMessageType;
import com.weibo.wesync.data.SyncKey;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Notice;
import com.weibo.wesync.data.WeSyncMessage.SyncReq;
import com.weibo.wesync.data.WeSyncMessage.SyncResp;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * @author Eric Liang
 * 
 * The sequence diagram script for synchronization, PlantUML or www.websequencediagrams.com will help you.
 * 
 * 	@startuml sequence_diagram_sync.png
	title wesync synchronization new style
	==Normal Case==
	Client->Server: 1 sync with key: 0
	note right of Server: [101,102]
	Server->Client: return [101,102] next key: k102
	note right of Server: [101,102]
	Client->Server: 2 sync with key: k102 
	note right of Server: []
	Server->Client: return [] with next key: empty
	
	==Send Message==
	Client->Server: 1 sync with key: 0
	note right of Server: [101,102]
	alt with new message ( client change [xxx] )
	Server->Client: return [101,102] next key: k102 + applied message id: 103
	note right of Server: [101,102]
	Client->Server: 2 sync with key: k102
	note right of Server: []
	Server->Client: return [] with next key: empty
	else no client changes
	note over Client,Server: normal case
	end
	
	==Direct Push (Send Message Only)==
	Client->Server: 1 sync with key: 0
	note right of Server: [101,102]
	alt with flag: sendOnly
	Server->Client: return [] next key: 0 + applied message id: 103
	note right of Server: [101,102]
	else without flag:
	note over Client,Server: send message
	end
	@enduml
 *
 */

public class Sync10Handler extends BaseHandler {
	
	public final static int DEFAULT_BATCH_SIZE = 20;
	public final static int PROPERTY_BATCH_SIZE = 200;
	
	public Sync10Handler(WeSyncService weSync) {
		super(weSync);
	}
	
	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean _fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		SyncReq req;
		try {
			req = SyncReq.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("Sync request by "+username+", "+req);
		
		String folderIdStr = req.getFolderId();	
		FolderID folderId = new FolderID(folderIdStr);
		
		if( !isAccessPermitted(username, folderId) ){
			log.warn("Unpermited access: "+ username + " on folder " + folderIdStr);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
				
		//Non-conversation folders will be auto-created.
		if( !weSync.getDataService().isFolderExist(folderIdStr) && folderId.type.equals( FolderID.Type.Conversation) ){
			log.warn("User: " + username + " is trying to synchronize on non-exist folder: "+ folderId);
			// TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		SyncResp.Builder respBuilder = SyncResp.newBuilder();
		respBuilder.setFolderId(folderIdStr);
		boolean isFullSync = req.getIsFullSync();
		
		String syncKey = req.getKey();
		if( syncKey.equals(TAG_SYNC_KEY) ){
			respBuilder.setIsFullSync( isFullSync );
			
			//Effected new full sync will clean up the changes
			if( isFullSync ){
				weSync.getDataService().removeAllFolderChanges(folderIdStr);
			}
		}else{			
			// If conflict with the request.getIsFullSync, use this one first.
			// TODO maybe fix this in protocol
			isFullSync = SyncKey.isFullSyncKey(syncKey);
			respBuilder.setIsFullSync(isFullSync);
		}
		
		//Folder type specific process
		String groupHistoryFolderId = null;
		int batchSize = DEFAULT_BATCH_SIZE;
		switch( folderId.type ){
		case Property:
			//Property folder only support full sync
			isFullSync = true;
			respBuilder.setIsFullSync(true);
			batchSize = PROPERTY_BATCH_SIZE;
			break;
		case Group:
			String groupId = FolderID.getGroup(folderId);
			groupHistoryFolderId = Group.historyFolderId(groupId);
			break;
		default:
			; //do nothing
		}
		
		//TODO: initialize the nextKey, might be changed later
		String nextKey = SyncKey.emptySyncKey(folderIdStr, isFullSync);
		respBuilder.setHasNext(false);
		if( !req.hasIsSendOnly() || !req.getIsSendOnly() ){
			if (isFullSync) {
				SortedSet<FolderChild> following = groupHistoryFolderId == null ? 
						getChildrenBySyncKey(syncKey, req, folderIdStr, batchSize)
						: getChildrenBySyncKey(syncKey, req, groupHistoryFolderId, batchSize);
								
				if (null != following && !following.isEmpty() ) {
					if( following.size() >= batchSize ){
						respBuilder.setHasNext(true);
					}
					
					String childIdForSyncKey;
					if( req.hasIsForward() && req.getIsForward() ){
						childIdForSyncKey = following.last().id;
					}else{
						childIdForSyncKey = following.first().id;
					}
					nextKey = SyncKey.syncKeyOnChild(folderIdStr, childIdForSyncKey);
					
					if( folderId.type.equals(FolderID.Type.Property) ){
						Meta.Builder metaBuilder = Meta.newBuilder();
						for (FolderChild c : following) {
							metaBuilder.setId(c.id).setType(ByteString.copyFrom(new byte[] { MetaMessageType.property.toByte() }));
							respBuilder.addServerChanges( metaBuilder.build() );
						}
					}else{
						String metaFolderId = folderId.type.equals(FolderID.Type.Group) ? groupHistoryFolderId : folderIdStr;
						for (FolderChild c : following) {
							String msgId = FolderChild.generateId(metaFolderId,Long.valueOf(c.id));
							Meta msg = weSync.getDataService().getMetaMessage(msgId);
							if (null != msg) {
								respBuilder.addServerChanges(msg);
							}
						}
					}
				}
			} else {
				weSync.getDataService().cleanupSynchronizedChanges(folderIdStr, syncKey);
				SortedSet<FolderChange> following = weSync.getDataService().getFolderChanges(folderIdStr, 0, batchSize-1);
				
				if (null != following && !following.isEmpty()) {
					if( following.size() >= batchSize ) {
						respBuilder.setHasNext(true);
					}
					
					FolderChange lastChange = following.last();
					nextKey = SyncKey.syncKeyOnChange(folderIdStr, lastChange);
					
					String metaFolderId = folderId.type.equals(FolderID.Type.Group) ? groupHistoryFolderId : folderIdStr;
					for (FolderChange fc : following) {
						String msgId;
						try{
							msgId = FolderChild.generateId(metaFolderId,Long.valueOf(fc.childId));
						}catch(java.lang.NumberFormatException e){
							//Maybe some meta from other folder, e.g. group chat 
							msgId = fc.childId;
						}
						
						Meta msg;
						if (fc.isAdd) {
							msg = weSync.getDataService().getMetaMessage(msgId);
						} else {
							// TODO empty meta means delete?
							msg = Meta.newBuilder().setId(msgId).build();
						}
						if (null != msg) {
							respBuilder.addServerChanges(msg);
						}
					}
				}
			}
		}

		// Check and store client changes
		applyClientChanges( folderId, req, respBuilder );
		
		respBuilder.setNextKey(nextKey);
		return respBuilder.build().toByteArray();
	}

	private SortedSet<FolderChild> getChildrenBySyncKey(String syncKey, SyncReq req, String folderIdStr, int batchSize) {
		SortedSet<FolderChild> following = null;
		if( !SyncKey.isEmpty(syncKey) ){
			String childId = SyncKey.getChildBySyncKey(syncKey);
			if( null != childId ){
				if( req.hasIsForward() && req.getIsForward() ){
					following = weSync.getDataService().getFollowingChildren(folderIdStr, childId, batchSize);
				}else{
					//back traversal
					following = weSync.getDataService().getPrecedingChildren(folderIdStr, childId, batchSize);
				}
			}
		}else if(syncKey.equals(TAG_SYNC_KEY)){
			if( req.hasHintChildId() ){
				String childId = String.valueOf( FolderChild.getScore(req.getHintChildId()) );
				if (req.hasIsForward() && req.getIsForward()) {
					following = weSync.getDataService().getFollowingChildren(folderIdStr, childId, batchSize);
				} else {
					// back traversal
					following = weSync.getDataService().getPrecedingChildren(folderIdStr, childId, batchSize);
				}
			}else{
				if( req.hasIsForward() && req.getIsForward() ){
					following = weSync.getDataService().getChildren(folderIdStr, 0, batchSize-1);
				}else{
					following = weSync.getDataService().getChildren(folderIdStr, -batchSize, -1);
				}
			}
		}else ;
		
		return following;
	}

	private boolean isAccessPermitted(String username, FolderID folderId) {
		switch( folderId.type ){
		case Property:
			String propName = FolderID.getProperty(folderId);
			if( weSync.isPropertySupported(propName) ){
				if( propName.equals(Group.PROP_MEMBERS) || propName.equals(Group.PROP_HISTORY) ){
					String groupId = FolderID.getUsername(folderId);
					if( weSync.getDataService().isMember(username, groupId) ){
						return true;
					}
				}
			}
			break;
		case Group:
			String groupId = FolderID.getGroup(folderId);
			if( weSync.getDataService().isMember(username, groupId) ){
				return true;
			}
		default:
			break;
		}
		
		return FolderID.isBelongTo(folderId, username);
	}

	private void applyClientChanges(FolderID folderId, SyncReq req, SyncResp.Builder respBuilder) {
		for(Meta metaMsg : req.getClientChangesList() ){
			if( !isClientChangeValid(folderId, metaMsg) ){
				log.warn("Reject client change :" + metaMsg.toString() );
				continue;
			}
			
			if( !weSync.getPrivacyService().isMessagePermitted(metaMsg.getFrom(), metaMsg.getTo(), metaMsg) ){
				log.warn("Message not permitted : "+ metaMsg.getFrom()+" -> "+metaMsg.getTo() );
				continue;
			}
			
			//Fix the time by server
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
			int seconds = (int) (cal.getTimeInMillis() / 1000);
			Meta clientChangeWithTimeFixed = Meta.newBuilder(metaMsg)
					.setTime( seconds )
					.build();
			
			Meta clientChangeForNotice = refineMetaForNotice( clientChangeWithTimeFixed );

			String newId;
			if( folderId.type.equals(FolderID.Type.Group) ){
				String groupId = FolderID.getGroup(folderId);
				newId = weSync.getDataService().storeToGroup(clientChangeWithTimeFixed, groupId);
				weSync.getGroupMessageService().broadcast(groupId, clientChangeWithTimeFixed.getFrom(), newId);
				
				SortedSet<FolderChild> members = weSync.getDataService().members(groupId);
				for(FolderChild fc : members ){
					String recvFolderId = FolderID.onGroup(fc.id, groupId);
					noticeReceiver( fc.id, recvFolderId, clientChangeForNotice );
				}
			}else if( folderId.type.equals(FolderID.Type.Property) ){
				newId = weSync.getDataService().storeProperty(clientChangeWithTimeFixed);
			}else{
				newId = weSync.getDataService().store(clientChangeWithTimeFixed);
				//TODO indicate the success
				if( null == newId ) newId =  clientChangeWithTimeFixed.getId();
				
				String recvFolderId = FolderID.onConversation(clientChangeWithTimeFixed.getTo(), clientChangeWithTimeFixed.getFrom());
				noticeReceiver( clientChangeWithTimeFixed.getTo(), recvFolderId, clientChangeForNotice );
			}
			
			Meta clientChangeIndicator = Meta.newBuilder()
					.setId( clientChangeWithTimeFixed.getId() )
					.setType( clientChangeWithTimeFixed.getType() )
					.setContent( ByteString.copyFromUtf8( newId ) )
					.setTime( clientChangeWithTimeFixed.getTime() )
					.build();
			respBuilder.addClientChanges(clientChangeIndicator);
		}			
	}
	
	private Meta refineMetaForNotice(Meta orig){
		if( !orig.hasType() ) return null;
		
		Meta.Builder ret = Meta.newBuilder()
				.setId(orig.getId())
				.setType(orig.getType());
		
		if( orig.hasFrom() ) ret.setFrom( orig.getFrom() );
		if( orig.hasSpanId() ) ret.setSpanId( orig.getSpanId() );
		if( orig.hasSpanSequenceNo() ) ret.setSpanSequenceNo( orig.getSpanSequenceNo() );
		
		MetaMessageType type = MetaMessageType.valueOf(ret.getType().byteAt(0));
		switch( type ){
		case text:
			ret.setContent( orig.getContent() );
			break;
		case mixed:
			ret.setContent( orig.getContent() );
			break;
		default:
			ret.setContent( ByteString.copyFromUtf8( type.toString() ));
		}
		
		return ret.build();
	}

	private void noticeReceiver(String username, String folderId, Meta msg) {
		int unreadNum = weSync.getDataService().getUnreadNumber(folderId);
		
		Unread.Builder unreadBuilder = Unread.newBuilder()
				.setFolderId( folderId )
				.setNum( unreadNum );
		if( msg != null ){
			unreadBuilder.setContent(msg);
		}
		
		Notice notice = Notice.newBuilder()
				.addUnread(unreadBuilder.build())
				.build();
		weSync.getNoticeService().send(username, notice);
	}

	private boolean isClientChangeValid(FolderID folderId, Meta clientChange) {
		switch( folderId.type ){
		case Conversation:
			if( FolderID.getUsername(folderId).equals(clientChange.getFrom()) 
					&& FolderID.getUserChatWith(folderId).equals(clientChange.getTo()) ){
				// TODO is the receiver permitted?
				return true;
			}
			break;
		case Group:
			if(  FolderID.getUsername(folderId).equals(clientChange.getFrom() ) 
					&& FolderID.getGroup(folderId).equals( clientChange.getTo() ) ){
				return true;
			}
		default:
			log.warn( "Malicious client change on folder: "+ folderId );
		}
		return false;
	}

}
