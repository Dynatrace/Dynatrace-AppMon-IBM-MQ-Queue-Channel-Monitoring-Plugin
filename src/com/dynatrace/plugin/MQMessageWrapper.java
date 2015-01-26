package com.dynatrace.plugin;

import com.ibm.mq.*;

public class MQMessageWrapper implements MQC {
 private String message,messageFormat,userID,putDateTime;
 private String putAppName,replyToQueueManager,replyToQueue,accountingToken;
 private int BackOutCount,AppType,expiry,msgType,characterSet;
 private int  offset,persistanceMsg; 
 private byte[] messageID,CorrID,GroupID; 
 private final String FORMAT = "MM/dd/yy hh:mm aaa";
 /**
  * 
  * @param message
  */ 
 public void setMessage(String message ){
 	this.message = message;
 }
 
 /**
  * 
  * @return message
  */
 public String getMessage(){
 	return message;
 }
 
 /**
  * 
  * @param messageFormat
  */ 
 public void setMsgFormat(String messageFormat ){
 	this.messageFormat = messageFormat;
 }
 
 /**
  * 
  * @return messageFormat
  */
 public String getMsgFormat(){
 	return messageFormat;
 }
 
 /**
  * 
  * @param userID
  */ 
 public void setUserID(String userID ){
 	this.userID = userID;
 }
 
 /**
  * 
  * @return userID
  */
 public String getUserID(){
 	return userID;
 }
 
 /**
  * 
  * @param AppType
  */ 
 public void setAppType(int AppType ){
 	this.AppType = AppType;
 }
 
 /**
  * 
  * @return AppType
  */
  		 
 public String getAppType(){
 	switch (AppType){
 	 case 1 :
 	 	return "CICS";
 	 case 2: 
 	 	return "MVS";
 	 case 3:
 	 	 return "IMS";
	 case 4: 
 	 	  return "OS2";
 	 case 5:
 	 	 return "DOS";
 	 case 6: 
 	 	  return "AIX";
 	 case 7: 
 	 	  return "UNIX";
 	 case 8:
 	 	  return "QMGR";
 	 case 9:
 	 	  return "OS400";
 	 case 10:
 	 	  return "WINDOWS";
 	 default:
 	 	return "";
 	 	  
 	}
 	
 }
 
 /**
  * 
  * @param putDateTime
  */ 
 public void setPutDateTime(java.util.Date putDateTime ){
 	java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(FORMAT);
   	this.putDateTime = format.format(putDateTime);
 }
 
 /**
  * 
  * @return putDateTime
  */
 public String getPutDateTime(){
 	return putDateTime;
 }
 
  
 /**
  * 
  * @param putAppName
  */ 
 public void setPutAppName(String putAppName ){
 	this.putAppName = putAppName;
 }
 
 /**
  * 
  * @return putAppName
  */
 public String getPutAppName(){
 	return putAppName;
 }
 
 /**
  * 
  * @param replyToQueueManager
  */ 
 public void setReplyToQMgr(String replyToQueueManager ){
 	this.replyToQueueManager = replyToQueueManager;
 }
 
 /**
  * 
  * @return replyToQueueManager
  */
 public String getReplyToQMgr(){
 	return replyToQueueManager;
 }
 
 /**
  * 
  * @param replyToQueue
  */ 
 public void setReplyToQueue(String replyToQueue ){
 	this.replyToQueue = replyToQueue;
 }
 
 /**
  * 
  * @return replyToQueue
  */
 public String getReplyToQueue(){
 	return replyToQueue;
 }
 
 /**
  * 
  * @param accountingToken
  */ 
 public void setAccoutingToken(String accountingToken ){
 	this.accountingToken = accountingToken;
 }
 
 /**
  * 
  * @return accountingToken
  */
 public String getAccoutingToken(){
 	return accountingToken;
 }
 
  
 /**
  * 
  * @param BackOutCount
  */ 
 public void setBackOutCount(int BackOutCount ){
 	this.BackOutCount = BackOutCount;
 }
 
 /**
  * 
  * @return BackOutCount
  */
 public int getBackOutCount(){
 	return BackOutCount;
 }
 
 /**
  * 
  * @param expiry
  */ 
 public void setExpiry(int expiry ){
 	this.expiry = expiry;
 }
 
 /**
  * 
  * @return expiry
  */
 public int getExpiry(){
 	return expiry;
 }
 
 /**
  * 
  * @param msgType
  */ 
 public void setMsgType(int msgType ){
 	this.msgType = msgType;
 }
 
 /**
  * 
  * @return msgType
  */
 public String getMsgType(){
 	switch (msgType){
 	  case 1:
	  	return "Request";
	  case 2:
	  	return "Response";
	  case 8:
	  	return "DataGram";
	  default:
	  	return "";
 	}
	
 }
 
 /**
  * 
  * @param characterSet
  */ 
 public void setCharacterSet(int characterSet ){
 	this.characterSet = characterSet;
 }
 
 /**
  * 
  * @return characterSet
  */
 public int getCharacterSet(){
 	return characterSet;
 }
 
 /**
  * 
  * @param messageID
  */ 
 public void setMsgID(byte[] messageID ){
 	this.messageID = messageID;
 }
 
 /**
  * 
  * @return messageID
  */
 public byte[] getMsgID(){
 	return messageID;
 }
 
 /**
  * 
  * @param CorrID
  */ 
 public void setCorrID(byte[] CorrID ){
 	this.CorrID = CorrID;
 }
 
 /**
  * 
  * @return CorrID
  */
 public byte[] getCorrID(){
 	return CorrID;
 } 
 
 /**
  * 
  * @param GroupID
  */ 
 public void setGroupID(byte[] GroupID ){
 	this.GroupID = GroupID;
 }
 
 /**
  * 
  * @return GroupID
  */
 public byte[] getGroupID(){
 	return GroupID;
 } 
 
 
 
 /**
  * 
  * @param offset
  */ 
 public void setOffset(int offset ){
 	this.offset = offset;
 }
 
 /**
  * 
  * @return offset
  */
 public int getOffset(){
 	return offset;
 } 
 
 
 
 /**
  * 
  * @param persistanceMsg
  */ 
 public void setPersistanceMsg(int persistanceMsg ){
 	this.persistanceMsg = persistanceMsg;
 }
 
 /**
  * 
  * @return persistanceMsg
  */
 public int getPersistanceMsg(){
 	return persistanceMsg;
 } 
 
 
}
