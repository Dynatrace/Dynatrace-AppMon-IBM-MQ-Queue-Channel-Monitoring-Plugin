package com.dynatrace.plugin;




public class QMPropWrapper {
    
    private int msgLength,noHandles,currentVersion,platformValue;
	private int connCount;
	private int qmStatus;
	
	private String  queueManageName,alterTime,createTime,alterationDate,creationDate;
	private String DeadLetterQueueName;
	
	public int getMsgLength() {
		return msgLength;
	}
	public void setMsgLength(int msgLength) {
		this.msgLength = msgLength;
	}
	public int getNoHandles() {
		return noHandles;
	}
	public void setNoHandles(int noHandles) {
		this.noHandles = noHandles;
	}
	public int getCurrentVersion() {
		return currentVersion;
	}
	public void setCurrentVersion(int currentVersion) {
		this.currentVersion = currentVersion;
	}
	public int getPlatformValue() {
		return platformValue;
	}
	public void setPlatformValue(int platformValue) {
		this.platformValue = platformValue;
	}
	public int getConnCount() {
		return connCount;
	}
	public void setConnCount(int connCount) {
		this.connCount = connCount;
	}
	public int getQmStatus() {
		return qmStatus;
	}
	public void setQmStatus(int qmStatus) {
		this.qmStatus = qmStatus;
	}
	public String getQueueManageName() {
		return queueManageName;
	}
	public void setQueueManageName(String queueManageName) {
		this.queueManageName = queueManageName;
	}
	public String getAlterTime() {
		return alterTime;
	}
	public void setAlterTime(String alterTime) {
		this.alterTime = alterTime;
	}
	public String getCreateTime() {
		return createTime;
	}
	public void setCreateTime(String createTime) {
		this.createTime = createTime;
	}
	public String getAlterationDate() {
		return alterationDate;
	}
	public void setAlterationDate(String alterationDate) {
		this.alterationDate = alterationDate;
	}
	public String getCreationDate() {
		return creationDate;
	}
	public void setCreationDate(String creationDate) {
		this.creationDate = creationDate;
	}
	public String getDeadLetterQueueName() {
		return DeadLetterQueueName;
	}
	public void setDeadLetterQueueName(String deadLetterQueueName) {
		DeadLetterQueueName = deadLetterQueueName;
	}
      
    
}