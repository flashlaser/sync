package com.weibo.wesync.data;

/**
 * 
 * Child of the Folder, which is an id with the score for comparison.
 * 
 * @author Eric Liang
 */
public class FolderChild implements Comparable<FolderChild>{
	public final static char SPLIT = FolderID.FOLDER_SPLIT;
	
	public long score;
	public String id;
	
	private void init(String id, long score){
		this.id = id;
		this.score = score;
	}
	
	public FolderChild(String id){
		long score = getScore(id);
		init(id, score);
	}
	
	public FolderChild(String id, long score){
		init(id, score);
	}
	
	public static String generateId(String folderId, long score){
		return folderId+SPLIT+score;
	}
	
	public static long getScore(String childId){
		int idx = childId.lastIndexOf(SPLIT);
		if( idx > 0 && idx+1 < childId.length() ){
			try {
				return Long.parseLong(childId.substring(idx+1));
			} catch (NumberFormatException nf) {
				// Do nothing
			}
		}
		return 0;
	}

	//Order by score in SortedSet
	@Override
	public int compareTo(FolderChild other) {
		long delta = this.score - other.score;
		
		if( 0 == delta ){
			return this.id.compareTo(other.id); 
		}else if(delta > 0){
			return 1;
		}else{
			return -1;
		}
	}
	
	//Only id matters since we will retrieve children by id
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (obj.getClass() != this.getClass()))
			return false;
		
		FolderChild c = (FolderChild) obj;
		return this.id.equals(c.id);
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + (null == id ? 0 : id.hashCode());
		return hash;
	}
}
