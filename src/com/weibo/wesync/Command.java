package com.weibo.wesync;

/**
 * @author Eric Liang
 */
public enum Command {
	Sync ((byte)0x0),
	SendFile ((byte)0x1),
	FolderSync ((byte)0x2),
	FolderCreate ((byte)0x3),
	FolderDelete ((byte)0x4),
	GetItemUnread ((byte)0x5),
	ItemOperations ((byte)0x6),
	Provision ((byte)0x7),
	Settings ((byte)0x8),
	GetFile((byte)0x9),
	Unknown ((byte)0xFF);
	
	private final byte code;
	private Command(byte code){
		this.code = code;
	}
	public byte toByte(){
		return code;
	}
	
	public static Command valueOf(final byte code){
		for( Command c: Command.values() ){
			if ( code == c.code ) return c;
		}
		return Unknown;
	}
}
