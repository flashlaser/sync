package com.weibo.wesync.command;

import java.util.Calendar;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.Command;
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

public class SyncHandler extends BaseHandler {
	
	public final static int DEFAULT_BATCH_SIZE = 20;
	public final static int PROPERTY_BATCH_SIZE = 200;
	private static final int MAX_BODY_LENGTH = 1 * 1024 * 1024;
	private static ExecutorService executor = Executors.newFixedThreadPool(32);
	
	public SyncHandler(WeSyncService weSync) {
		super(weSync);
	}
	
	@Override
	public byte[] handle(String username, WeSyncURI uri, byte[] data, boolean fromListener) {
		log.debug("Protocol "+weSync.version()+" request by "+username+" uri: "+uri);
		
		SyncReq req;
		try {
			req = SyncReq.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Invalid request from user "+username+" : "+uri);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
		
		log.debug("Sync request by "+username+", "+ getDebugInfo(req));
		
		String folderIdStr = req.getFolderId();	
		FolderID folderId = new FolderID(folderIdStr);
		
		if (fromListener) {
			applyClientChanges(folderId, req, null, true);
			return null;
		}
		
		if( !isAccessPermitted(username, folderId) ){
			log.warn("Unpermited access: "+ username + " on folder " + folderIdStr);
			//TODO error code should be returned to client
			throw new RuntimeException();
		}
				
		//Folder autocreation
		if( !weSync.getDataService().isFolderExist(folderIdStr) ){
			//TODO folder amount limit?
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
		String groupId = null;
		int batchSize = DEFAULT_BATCH_SIZE;
		switch( folderId.type ){
		case Property:
			//Property folder only support full sync
			isFullSync = true;
			respBuilder.setIsFullSync(true);
			batchSize = PROPERTY_BATCH_SIZE;
			break;
		case Group:
			groupId = FolderID.getGroup(folderId);
			break;
		default:
			; //do nothing
		}
		
		//TODO: initialize the nextKey, might be changed later
		String nextKey = SyncKey.emptySyncKey(folderIdStr, isFullSync);
		respBuilder.setHasNext(false);
		
		if( !req.hasIsSendOnly() || !req.getIsSendOnly() ){
			if (isFullSync) {
				SortedSet<FolderChild> following = getChildrenForFullSync(syncKey, req, folderIdStr, batchSize);
				
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
						for (FolderChild c : following) {
							//TODO tidy the group choices
							Meta msg = folderId.type.equals(FolderID.Type.Group) ?
									weSync.getDataService().getMetaMessageInGroup(groupId, Long.valueOf(c.id))
									: weSync.getDataService().getMetaMessage(folderIdStr, Long.valueOf(c.id));

							FolderChange change = new FolderChange( String.valueOf( c.score ), true);
							msg = checkMetaSize(folderIdStr, msg, change);
									
							if (null != msg) {
								respBuilder.addServerChanges(msg);
								
								if( MetaMessageType.valueOf( msg.getType().byteAt(0) ).equals( MetaMessageType.subfolder) ){
									SortedSet<FolderChild> grandsons = weSync.getDataService().getChildren( msg.getContent().toStringUtf8() );
									for(FolderChild gs: grandsons){
										Meta gsMsg = folderId.type.equals(FolderID.Type.Group) ?
												weSync.getDataService().getMetaMessageInGroup(groupId, Long.valueOf(gs.id))
												: weSync.getDataService().getMetaMessage(folderIdStr, Long.valueOf(gs.id));
										if( null != gsMsg ){
											respBuilder.addServerChanges(gsMsg);
										}
									}
								}
							}
						}
					}
				}
			} else {
				weSync.getDataService().cleanupSynchronizedChanges(folderIdStr, syncKey);
				
				SortedSet<FolderChange> following = null;
				if( !req.hasIsSiblingInHarmony() ){
					following = weSync.getDataService().getFolderChanges(folderIdStr, 0, batchSize-1);
				}else{
					if( req.getIsSiblingInHarmony() ){
						//All previous notice has successfully push the changes, 
						//then just process the selective ACKs
						if( req.getSelectiveAckCount() >= 0 ){
							for(String ack : req.getSelectiveAckList()){
								FolderChange change = new FolderChange( String.valueOf( new FolderChild(ack).score ), true);
								weSync.getDataService().removeFolderChange(folderIdStr, change);
							}
						}
					}else {
						following = weSync.getDataService().getFolderChanges(folderIdStr, 0, batchSize-1);
						
						//In case that unacknowledged messages overflow the batchSize limit, 
						//so retrieve them by following instead of by preceding and then filter it,
						//then process the selective ACK
						if( req.getSelectiveAckCount() == 1 ){
							String ack = req.getSelectiveAck(0);
							FolderChange change = new FolderChange( String.valueOf( new FolderChild(ack).score ), true);
							if( null != following ) {
								following = following.headSet(change);
							}
						}
					}
				}
					
				if (null != following && !following.isEmpty()) {
					if (following.size() >= batchSize) {
						respBuilder.setHasNext(true);
					}

					FolderChange lastChange = following.last();
					nextKey = SyncKey.syncKeyOnChange(folderIdStr, lastChange);

					for (FolderChange fc : following) {
						Meta msg = null;
						
						if (fc.isAdd) {
							Long childId = Long.valueOf(fc.childId);
							if( folderId.type.equals( FolderID.Type.Group ) ){
								msg = weSync.getDataService().getMetaMessageInGroup(groupId, childId);
								//fix msg id to folderchild id
								msg = Meta.newBuilder(msg).setId(FolderChild.generateId(folderIdStr, childId)).build();
							}else{
								msg = weSync.getDataService().getMetaMessage(folderIdStr, childId);
							}
						} else {
							// TODO empty meta means delete?
						}
						
						msg = checkMetaSize(folderIdStr, msg, fc);
						
						if (null != msg) {
							respBuilder.addServerChanges(msg);
						}
					}
				}
			}
		}

		// Check and store client changes
		applyClientChanges( folderId, req, respBuilder, fromListener );
		
		respBuilder.setNextKey(nextKey);
		return respBuilder.build().toByteArray();
	}

	// @by jichao, 考虑到无线设备内存有限，实际上超过1M通常会导致内存溢出，所以5M max Content length
	private Meta checkMetaSize(String folderIdStr, Meta msg, FolderChange fc) {
		if(msg.toByteArray().length > MAX_BODY_LENGTH) {
			log.warn("applyClientChanges->" + msg.getFrom() + "|" + msg.getTo() + "|" + msg.getType() + 
				", length=" + msg.toByteArray().length + 
				", is discarded because it's length is too large");
			
			weSync.getDataService().removeFolderChange(folderIdStr, fc);
			return null;
		}
		else {
			return msg;
		}
	}
	
	private SortedSet<FolderChild> getChildrenForFullSync(String syncKey, SyncReq req, String folderIdStr, int batchSize) {
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

	/*
	 * don't display too much binary data such as thumbnail, audio
	 */
	private String getDebugInfo(SyncReq req) {
		try {
			SyncReq.Builder reqbuilder = SyncReq.newBuilder();
			reqbuilder.setKey(req.getKey());
			reqbuilder.setFolderId(req.getFolderId());
			List<Meta> changes = req.getClientChangesList();
			
			for(Meta change : changes) {
				Meta.Builder metabuilder = Meta.newBuilder();
				metabuilder.mergeFrom(change.toByteArray());
				
				if(MetaMessageType.valueOf(change.getType().byteAt(0)).equals(MetaMessageType.audio)) {
					metabuilder.setContent(ByteString.copyFromUtf8(""));
				}
				
				if(MetaMessageType.valueOf(change.getType().byteAt(0)).equals(MetaMessageType.image)) {
					metabuilder.setThumbnail(ByteString.copyFromUtf8(""));
				}
				
				reqbuilder.addClientChanges(metabuilder.build());
			}
			
			return reqbuilder.build().toString();
		} catch (InvalidProtocolBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return req.toString();
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

	private void applyClientChanges(FolderID folderId, SyncReq req, SyncResp.Builder respBuilder, boolean fromListener) {
		for(Meta metaMsg : req.getClientChangesList() ) {
			
			// @by jichao, 考虑到无线设备内存有限，实际上超过1M通常会导致内存溢出，所以1M max Content length
			if(metaMsg.toByteArray().length > MAX_BODY_LENGTH) {
				log.warn("applyClientChanges->" + metaMsg.getFrom() + "|" + metaMsg.getTo() + "|" + metaMsg.getType() + 
					", length=" + metaMsg.toByteArray().length + 
					", is discarded because it's length is too large");
				
				// TO DO 返回错误
				return;
			}
			
			Meta msgWithTimeFixed = metaMsg;
			
			if (!fromListener) {
				if (!isClientChangeValid(folderId, metaMsg)) {
					log.warn("Reject client change :" + metaMsg.toString());
					continue;
				}

				if (!weSync.getPrivacyService().isMessagePermitted(metaMsg.getFrom(), metaMsg.getTo(), metaMsg)) {
					log.warn("Message not permitted : " + metaMsg.getFrom()+ " -> " + metaMsg.getTo());
					continue;
				}
				
				// Fix the time by server
				Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
				int seconds = (int) (cal.getTimeInMillis() / 1000);
				msgWithTimeFixed = Meta.newBuilder(metaMsg).setTime(seconds).build();
			}

			applySingleClientChange(folderId, req, respBuilder, fromListener, msgWithTimeFixed);
		}			
	}
	
	private void applySingleClientChange(FolderID folderId, SyncReq req,
			SyncResp.Builder respBuilder, boolean fromListener, Meta metaMsg) {
		
		if (metaMsg.hasSpanId()) {
			FolderID subFolderId = folderId.type.equals(FolderID.Type.Group) 
					? new FolderID(FolderID.onData(FolderID.getGroup(folderId), metaMsg.getSpanId())) 
					: new FolderID(FolderID.onData(FolderID.getUsername(folderId), metaMsg.getSpanId()));

			if (metaMsg.hasSpanSequenceNo() && metaMsg.getSpanSequenceNo() == 1) {
				Meta subFolderMsg = Meta
						.newBuilder()
						.setId(metaMsg.getId())
						.setFrom(metaMsg.getFrom())
						.setTo(metaMsg.getTo())
						.setType(ByteString.copyFrom(new byte[] { MetaMessageType.subfolder.toByte() }))
						.setSpanId(metaMsg.getSpanId())
						.setContent(ByteString.copyFromUtf8(subFolderId.toString()))
						.build(); 
				applyClientChangeInner(folderId, req, respBuilder, subFolderMsg, null, fromListener, 
						folderId.type.equals(FolderID.Type.Group));
			}

			applyClientChangeInner(subFolderId, req, respBuilder, metaMsg, folderId, fromListener, true);
		} else {
			applyClientChangeInner(folderId, req, respBuilder, metaMsg, null, fromListener, true);
		}
	}

	//TODO this method has too many parameters, maybe should consider to unify the storage for conversation and group
	private void applyClientChangeInner(FolderID folderId, SyncReq req,
			SyncResp.Builder respBuilder, Meta metaMsg, FolderID parentFolder,
			boolean fromListener, boolean noticeListener) {
		if (fromListener) {
			applyClientChangeForReceiver(folderId, req, respBuilder, metaMsg, parentFolder);
		} else {
			String newId = applyClientChangeForSender(folderId, req, respBuilder, metaMsg, parentFolder);
			if( newId != null && noticeListener){
				String noticeFolderId = parentFolder == null ? folderId.toString() : parentFolder.toString();
				Meta msgWithIDFixed = Meta.newBuilder(metaMsg)
						.setId(newId)
						.build();
				
				SyncReq syncReqForListener = SyncReq.newBuilder()
						.setKey(TAG_SYNC_KEY).setFolderId(noticeFolderId)
						.addClientChanges(msgWithIDFixed).build();
				
				
				noticeListener(Command.Sync, syncReqForListener.toByteString());
			}
		}
	}

	private String applyClientChangeForSender(FolderID folderId, SyncReq req,
			SyncResp.Builder respBuilder, Meta metaMsg, FolderID parentFolder) {
		FolderID storeFolder = parentFolder == null? folderId : parentFolder;
		
		String newId;
		switch (storeFolder.type) {
		case Group: {
			String groupId = parentFolder == null ? FolderID.getGroup(folderId) : FolderID.getGroup(parentFolder);
			newId = weSync.getDataService().storeToGroup(metaMsg, groupId);
			break;
		}
		case Property: {
			newId = weSync.getDataService().storeProperty(metaMsg);
			break;
		}
		default: {
			newId = weSync.getDataService().store(storeFolder.toString(), metaMsg, false);
			break;
		}
		}

		Meta.Builder clientChangeIndicator = Meta.newBuilder()
				.setId(metaMsg.getId())
				.setType(metaMsg.getType())
				.setContent(ByteString.copyFromUtf8(newId))
				.setTime(metaMsg.getTime());
		if (metaMsg.hasSpanId())
			clientChangeIndicator.setSpanId(metaMsg.getSpanId());
		
		if (metaMsg.hasSpanLimit())
			clientChangeIndicator.setSpanLimit(metaMsg.getSpanLimit());
		
		if (null != respBuilder) {
			respBuilder.addClientChanges(clientChangeIndicator.build());
		}
		return newId;
	}

	private String applyClientChangeForReceiver(FolderID folderId, SyncReq req,
			SyncResp.Builder respBuilder, Meta metaMsg, FolderID parentFolder) {
		FolderID storeFolder = parentFolder == null ? folderId : parentFolder;

		String newId = null;
		switch (storeFolder.type) {
		case Group: {
			String toUser = FolderID.getUsername(storeFolder);
			String fixedMsgId = FolderChild.generateId(storeFolder.toString(), Long.valueOf(metaMsg.getId()).longValue());
			Meta msgWithIDFixed = Meta.newBuilder(metaMsg).setId(fixedMsgId).build();
			
			// @by jichao, subfolder is not needed to send to client, overmore, if send,
			// it cause audio message msgid duplicated and can't be received
			MetaMessageType metatype = MetaMessageType.valueOf((metaMsg.getType().toByteArray())[0]);
			
			if(metatype != MetaMessageType.subfolder) {
				noticeReceiver(toUser, storeFolder.toString(), msgWithIDFixed);
			}
			
			break;
		}
		case Property: {
			// TODO
			break;
		}
		default: {
			newId = weSync.getDataService().store(storeFolder.toString(), metaMsg, true);
			Meta msgWithIDFixed = Meta.newBuilder(metaMsg).setId(newId).build();
			noticeReceiver(msgWithIDFixed.getTo(), storeFolder.toString(), msgWithIDFixed);
			break;
		}
		}
		return newId;
	}

	// @by jichao, 去掉unread content，减少流量消耗
//	private Meta refineMetaForNotice(Meta orig){
//		if( !orig.hasType() ) return null;
//		
//		Meta.Builder ret = Meta.newBuilder()
//				.setId(orig.getId())
//				.setType(orig.getType());
//		
//		if( orig.hasFrom() ) ret.setFrom( orig.getFrom() );
//		if( orig.hasSpanId() ) ret.setSpanId( orig.getSpanId() );
//		if( orig.hasSpanSequenceNo() ) ret.setSpanSequenceNo( orig.getSpanSequenceNo() ); 
//			
//		MetaMessageType type = MetaMessageType.valueOf(ret.getType().byteAt(0));
//		switch( type ){
//		case text:
//			ret.setContent( orig.getContent() );
//			break;
//		case mixed:
//			ret.setContent( orig.getContent() );
//			break;
//		default:
//			ret.setContent( ByteString.copyFromUtf8( type.toString() ));
//		}
//		
//		return ret.build();
//	}

	private void noticeReceiver(final String username, String folderId, Meta orig) {
		
		int unreadNum = weSync.getDataService().getUnreadNumber(folderId);
		
		Unread.Builder unreadBuilder = Unread.newBuilder()
				.setFolderId( folderId )
				.setNum( unreadNum );
		
		// @by jichao, 去掉unread content，减少流量消耗
//		Meta clientChangeForNotice = refineMetaForNotice( orig );
//		if( clientChangeForNotice != null ){
//			unreadBuilder.setContent(clientChangeForNotice);
//		}
		
		//concern the siblings 
		FolderChange change = new FolderChange( String.valueOf( new FolderChild(orig.getId()).score ), true);
		orig = checkMetaSize(folderId, orig, change);
		
		if(orig != null) {
			Notice.Builder noticeBuilder = Notice.newBuilder().addUnread(unreadBuilder.build());
			noticeBuilder.addMessage(orig);
			
			SortedSet<FolderChange> precedings = weSync.getDataService().getPrecedingChanges(folderId, change, 1);
			if( null != precedings ){
				for(FolderChange c : precedings ){
					String expectAck = FolderChild.generateId(folderId, Long.valueOf(c.childId));
					noticeBuilder.addExpectAck( expectAck );
				}
			}

			final Notice notice = noticeBuilder.build();
			
			executor.execute(new Runnable() {public void run() {
				weSync.getNoticeService().send(username, notice);
			}});
		}
	}

	private boolean isClientChangeValid(FolderID folderId, Meta clientChange) {
		switch( folderId.type ){
		case Conversation:
		case Conversation2:
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
