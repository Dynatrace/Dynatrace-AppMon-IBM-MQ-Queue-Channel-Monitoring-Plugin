
package com.dynatrace.plugin;

import java.util.Date;

public class ChannelData {
public String eChannelName,eConnName,eChlastMsgDate,eChllastMsgTime,eChlStartDate,eChlStartTime,MCAStatus,eMCAJob,eMCAUserId;
public String eNpmSpeed,eCurrentChl,eCurrentChlMax,eActiveChl,eActiveChlPaused,eMaxClient;
public int eXmitMsg = 0,eXmitMaxSize = 0,eMaxInstances = 0,eChannelStatus = 0,eChannelSubStatus = 0;
public int eChannelMsgs = 0,eBytesSent = 0,eBytesReceived = 0,eBufferSent = 0,eBufferReceived = 0;
public int eChannelType;
public int eCurrentSharingConvs;
public Date eLastMsgDate = null;


private int connections = 1;

public int getConnections() {
	return connections;
}

public void setConnections(int connections) {
	this.connections = connections;
}

}
