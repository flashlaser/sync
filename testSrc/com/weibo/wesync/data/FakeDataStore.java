package com.weibo.wesync.data;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;
import com.weibo.wesync.data.WeSyncMessage.DataSlice;
import com.weibo.wesync.data.WeSyncMessage.FileData;
import com.weibo.wesync.data.WeSyncMessage.Meta;
import com.weibo.wesync.data.WeSyncMessage.Unread;

/**
 * @author Eric Liang
 */
public class FakeDataStore implements DataStore {	
	private final Logger log = LoggerFactory.getLogger(FakeDataStore.class);
	private final int IDC_NUMBER = 100;
	private final int IDC_INDEX = 1; // should be less than IDC_NUMBER
	
	private Map<String, Folder> folderStore = new ConcurrentHashMap<String, Folder>();
	private Map<String, Meta> msgStore = new ConcurrentHashMap<String, Meta>();
	private Map<String, byte[]> fileStore = new ConcurrentHashMap<String, byte[]>();
	
	//cache for complete file
	private Map<String, FileData> fileCache = new ConcurrentHashMap<String, FileData>();

	private Folder getFolderInner( String folderId ){
		Folder instore = folderStore.get(folderId);
		if (null == instore) {
			synchronized (folderStore) {
				if( !folderStore.containsKey(folderId) ){
					instore = new Folder(folderId);
					instore.maxChildId = 0;
					folderStore.put(folderId, instore);	
				}
			}
		}
		return instore;
	}
	
	private SortedSet<FolderChange> getFolderChangesInner( String folderId ){
		Folder instore = getFolderInner( folderId );
		if( null == instore.changes ){
			synchronized( instore ){
				if( null == instore.changes ){
					instore.changes = new ConcurrentSkipListSet<FolderChange>();
				}
			}
		}
		return instore.changes;
	}
	
	private SortedSet<FolderChild> getChildrenInner( String folderId ){
		Folder instore = getFolderInner( folderId );
		if( null == instore.children ){
			synchronized( instore ){
				if( null == instore.children ){
					instore.children = new ConcurrentSkipListSet<FolderChild>();
				}
			}
		}
		return instore.children;
	}
	
	@Override
	public Meta getMetaMessage(String metaMsgId) {
		return msgStore.get(metaMsgId);
	}

	@Override
	public boolean addMetaMessage(Meta msg) {
		msgStore.put(msg.getId(), msg);
		return true;
	}

	@Override
	public boolean removeMetaMessage(Meta msg) {
		msgStore.remove(msg.getId());
		return true;
	}

	@Override
	public FileData storeFile(FileData fileData) {
		FileData.Builder builder = FileData.newBuilder().mergeFrom(fileData);
		
		FileData prevData = fileCache.get( fileData.getId() );
		if( null != prevData ){
			builder.addAllSlice( prevData.getSliceList() );
		}
		
		FileData data = builder.build();
		if( FileDataHelper.isSane(data) ) {
			FileData fixed = FileDataHelper.removeDuplicate(data);
			fileStore.put(fileData.getId(), fixed.toByteArray() );
			
			//This cache can reserved for performance
			fileCache.remove(fileData.getId());
		}else{
			fileCache.put(fileData.getId(), data);
			
			if( FileDataHelper.nearComplete(data) ){
				List<DataSlice> missingSlices = FileDataHelper.findMissing(data);
				FileData missing = FileData.newBuilder().setId(data.getId()).addAllSlice(missingSlices).build();
				return missing;
			}
		}
		
		return FileData.newBuilder().setId(data.getId()).build();
	}

	@Override
	public FileData getFileByIndex(FileData fileIndex) {
		FileData.Builder builder = FileData.newBuilder()
				.setId( fileIndex.getId() );
		
		FileData data = getFileById( fileIndex.getId() );
		if( null != data ) {
			List<DataSlice> slices = FileDataHelper.extract(data, fileIndex); 
			builder.addAllSlice(slices);
		}
		
		return builder.build();
	}

	@Override
	public FileData getFileById(String fileId) {
		FileData data = fileCache.get(fileId);
		if( null == data ){
			//File might be stored persistently
			try {
				data = FileData.parseFrom( fileStore.get(fileId) );
			} catch (InvalidProtocolBufferException e) {
				log.warn("Spoiled file data, id: " + fileId );
			}
		}
		return data;
	}

	@Override
	public SortedSet<FolderChange> getFolderChanges(String folderId) {
		//Redis command: ZRANGE
		return getFolderChangesInner( folderId );
	}

	@Override
	public boolean addFolderChange(String folderId, FolderChange change) {
		//Redis command: ZADD
		SortedSet<FolderChange> changes = getFolderChanges( folderId );
		
		if( FolderID.getType(folderId).equals( FolderID.Type.Root )
				&& changes.contains(change) ){
			return true;
		}
		
		return changes.add(change);
	}

	@Override
	public boolean removeFolderChange(String folderId, FolderChange change) {
		//Redis command: ZREM
		SortedSet<FolderChange> changes = getFolderChanges( folderId );
		return changes.remove(change);
	}

	@Override
	public int numberOfFolderChanges(String folderId) {
		//Redis command: ZCARD
		return getFolderChanges( folderId ).size(); 
	}

	@Override
	public SortedSet<FolderChild> getChildren(String folderId) {
		//Redis command: ZRANGE
		return getChildrenInner(folderId);
	}

	@Override
	public boolean addChild(String folderId, String childId, long score) {
		//Redis command: ZADD + ZRANK
		SortedSet<FolderChild> children = getChildrenInner( folderId );
		
		FolderChild child = new FolderChild(childId, score);
		if( FolderID.getType(folderId).equals( FolderID.Type.Root )
				&& children.contains(child) ){
			return true;
		}
		
		return children.add(child);
	}

	private FolderChild findChild(String folderId, String childId){
		SortedSet<FolderChild> children = getChildrenInner( folderId );

		for(FolderChild fc : children ){
			if( fc.id.equals(childId) ){
				return fc;
			}
		}
		return null;
	}
	
	@Override
	public boolean removeChild(String folderId, String child) {
		//Redis command: ZREM
		FolderChild fc = findChild( folderId, child);
		if( null == fc ) return false;
		
		return getChildren(folderId).remove(fc);
	}

	@Override
	public int numberOfChildren(String folderId) {
		//Redis command: ZCARD
		return getChildrenInner( folderId ).size();
	}

	@Override
	public int getMaxChildId(String folderId) {
		//Redis command: GET
		Folder instore = folderStore.get(folderId);
		if (null == instore) {
			return -1;
		}else{
			return instore.maxChildId;
		}
	}

	@Override
	public long reserveChildId(String folderId) {
		//Redis command: INCR
		Folder instore = getFolderInner( folderId );
		
		long newId =  ++instore.maxChildId;
		// Multi-idc support
		long idWithIDC = newId * IDC_NUMBER + IDC_INDEX;

		// Add DateStamp for shard in DB, the MMDD.
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		int stamp = cal.get(Calendar.YEAR) % 100 * 10000
				+ (cal.get(Calendar.MONTH) + 1) * 100
				+ cal.get(Calendar.DAY_OF_MONTH);

		return idWithIDC * 1000000 + stamp;
	}

	@Override
	public boolean createFolder(String folderId) {
		//Redis command: SET
		getFolderInner( folderId );
		return true;
	}

	@Override
	public SortedSet<FolderChange> getFolderChanges(String folderId, int beginIndex, int endIndex) {
		SortedSet<FolderChange> changes = getFolderChanges(folderId);
		return getElementsByRange(changes, beginIndex, endIndex);
	}

	private <T> SortedSet<T> getElementsByRange(SortedSet<T> total, int beginIndex, int endIndex){
		//Redis command: ZRANGE
		T beginElement = getElementByIndex( total, beginIndex );
		T endElement = getElementByIndex( total, endIndex );
		
		SortedSet<T> newSet = new ConcurrentSkipListSet<T>();
		SortedSet<T> subSet = null;
		if( null == beginElement && null == endElement ) {
			return null;
		}else if ( null == beginElement ){
			subSet = total.headSet(endElement);
			newSet.add(endElement);
		}else if ( null == endElement ){
			subSet = total.tailSet(beginElement);
		}else{
			subSet = total.subSet(beginElement, endElement);
			newSet.add(endElement);
		}
		if (null != subSet) newSet.addAll(subSet);
		
		return newSet;
	}
	
	private <T> T getElementByIndex(SortedSet<T> set, int index ){
		//Emulate Redis operation, where the minus index means traverse the set reversely
		if( set.isEmpty() ) return null;
				
		index = (index < 0 ? set.size()+index : index);
		if( index < 0 ) return null;
		if( index >= set.size()  ) return null;
				
		Iterator<T> iter = set.iterator();
		while( index-- > 0 ){
			iter.next();
		}
		return iter.next();
	}
	
	@Override
	public SortedSet<FolderChild> getChildren(String folderId, int beginIndex, int endIndex) {
		SortedSet<FolderChild> children = getChildren( folderId );
		return getElementsByRange( children, beginIndex, endIndex );
	}

	@Override
	public SortedSet<FolderChange> getFollowingChanges(String folderId, FolderChange change, int n) {
		//Redis command: ZRANK + ZRANGE
		SortedSet<FolderChange> changes = getFolderChanges( folderId );
		int begin = getIndexByElement( changes, change );
		return getFolderChanges( folderId, begin+1, begin+n );
	}

	@Override
	public SortedSet<FolderChange> getPrecedingChanges(String folderId,
			FolderChange change, int n) {
		//Redis command: ZRANK + ZRANGE
		SortedSet<FolderChange> changes = getFolderChanges(folderId);
		return getPrecedingElements( changes, change, n );
	}
	
	private <T> int getIndexByElement( SortedSet<T> set, T element){
		//Redis command: ZRANK
		int index = 0;
		//FIXME maybe not exist?
//		if( !set.contains(element) ) return -1;
		
		Iterator<T> iter = set.iterator();
		while( iter.hasNext() ){
			if( iter.next().equals(element) ) break;
			index++;
		}
		return index;
	}
	
	@Override
	public SortedSet<FolderChild> getFollowingChildren(String folderId, String childId, int n) {
		//Redis command: ZRANK + ZRANGE
		SortedSet<FolderChild> children = getChildren( folderId );
		int begin = getIndexByElement( children, new FolderChild(childId) );
		return getChildren( folderId, begin+1, begin+n );
	}

	@Override
	public List<Unread> numberOfFolderChanges(List<String> folderIds) {
		List<Unread> unreadList = new LinkedList<Unread>();
		Unread.Builder builder = Unread.newBuilder();
		for( String fid: folderIds ){
			builder.setFolderId(fid);
			builder.setNum( numberOfFolderChanges(fid) );
			unreadList.add( builder.build() );
		}
		return unreadList;
	}

	@Override
	public boolean removePrecedingFolderChange(String folderId,FolderChange change) {
		//Redis command: ZREMRANGEBYRANK
		if( null == change ) return false;
		
		SortedSet<FolderChange> changes = getFolderChanges(folderId);
		return removePrecedingElements( changes, change );
	}

	@Override
	public boolean removePrecedingChildren(String folderId, FolderChild child) {
		// Redis command: ZREMRANGEBYRANK
		if( null == child ) return false;
		
		SortedSet<FolderChild> children = getChildrenInner(folderId);
		return removePrecedingElements(children, child);
	}

	private <T> boolean removePrecedingElements( SortedSet<T> set, T element){
		set.headSet(element).clear();
		set.remove(element);
		return true;
	}
	@Override
	public SortedSet<FolderChange> removeAllFolderChanges(String folderId) {
		//Redis command: ZREM
		SortedSet<FolderChange> changes = getFolderChanges(folderId);
		SortedSet<FolderChange> ret = new ConcurrentSkipListSet<FolderChange>(changes);
		changes.clear();
		return ret;
	}

	@Override
	public boolean removeAllChildren(String folderId) {
		//Redis command: ZREMRANGEBYSCORE or ZREM
		SortedSet<FolderChild> children = getChildrenInner(folderId);
		children.clear();
		return true;
	}

	@Override
	public SortedSet<FolderChild> getPrecedingChildren(String folderId, String childId, int n) {
		//Redis command: ZRANK + ZRANGE
		SortedSet<FolderChild> children = getChildren(folderId);
		return getPrecedingElements( children, new FolderChild(childId), n );
	}
	
	private <T> SortedSet<T> getPrecedingElements(SortedSet<T> all, T element, int n){
		int flag = getIndexByElement( all, element );
		int end = flag-1;
		if( end < 0 ) return null;
		
		int begin = flag-n;
		if( begin < 0 ) begin = 0;
		return getElementsByRange(all, begin, end);
	}

	@Override
	public boolean isChild(String childId, String folderId) {
		//Redis command: ZRANK
		return findChild(folderId, childId) != null;
	}

	@Override
	public boolean destroyFolder(String folderId) {
		Folder instore = folderStore.remove(folderId);
		if (null != instore) {
			if( null != instore.changes) instore.changes.clear();
			if( null != instore.children) instore.children.clear();
			instore.maxChildId = -1;
		}
		return true;
	}

	@Override
	public boolean removeFolderChanges(String folderId, int num) {
		//Redis command: ZREM or ZREMRANGEBYRANK for performance
		SortedSet<FolderChange> changes = getFolderChanges( folderId );
		
		while( num-- > 0 ){
			FolderChange fc = changes.first();
			if( fc != null )  changes.remove(fc);
		}
		return true;
	}

	@Override
	public boolean removeChildren(String folderId, int num) {
		//Redis command: ZREM or ZREMRANGEBYRANK for performance
		SortedSet<FolderChild> children = getChildren( folderId );
				
		while( num-- > 0 ){
			FolderChild fc = children.first();
			if( fc != null )  children.remove(fc);
		}
		return true;
	}
}
