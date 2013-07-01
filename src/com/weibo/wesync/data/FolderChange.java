package com.weibo.wesync.data;

/**
 * @author Eric Liang
 */
public class FolderChange implements Comparable<FolderChange>{
	public final String childId; 
	public final boolean isAdd;
	
	public FolderChange(String childId, boolean isAdd){
		this.childId = childId;
		this.isAdd = isAdd;
	}
	
	public static FolderChange fromString(String changeStr){
		if( null == changeStr || changeStr.length()<=1 ) return null;
		
		boolean isAdd;
		char pre = changeStr.charAt(0);
		if( '+' == pre ) isAdd = true;
		else if( '-' == pre ) isAdd = false;
		else return null;
		
		return new FolderChange( changeStr.substring(1), isAdd);
	}
	
	public String toString(){
		return (isAdd?'+':'-')+childId;
	}
	
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (obj.getClass() != this.getClass()))
			return false;
		
		FolderChange c = (FolderChange) obj;
		return isAdd == c.isAdd
				&& (childId == c.childId || (childId != null && childId
						.equals(c.childId)));
	}

	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + (isAdd? 1 : 0);
		hash = 31 * hash + (null == childId ? 0 : childId.hashCode());
		return hash;
	}

	@Override
	public int compareTo(FolderChange other) {
		try{
			long myScore = Long.parseLong(this.childId);
			long otherScore = Long.parseLong(other.childId);
			if( myScore != otherScore ){
				int ret = myScore > otherScore ? 1 : -1;
				return ret;
			}
		} catch (NumberFormatException nf) {
			//for root folder
			if( !this.childId.equals(other.childId) ){
				return this.childId.compareTo(other.childId);
			}
		}
		
		if( this.isAdd == other.isAdd ) return 0;
		//Add operation should be in front of delete
		return this.isAdd ? 1 : -1;
	}
}
