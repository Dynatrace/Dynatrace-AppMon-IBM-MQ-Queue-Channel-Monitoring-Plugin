/**
 *  PLUGIN FOR MQ CHANNEL MONITORING 
 *  
 *  The Plugin monitors the Unix Queue Managers , Channels and queues and captures the status in metrics.
 *  The metrics are then used in Dynatrace MQ Dashboard
 *
 *
 **/

package com.dynatrace.plugin;

import com.dynatrace.diagnostics.pdk.*;
import com.dynatrace.diagnostics.pdk.Status.StatusCode;
import com.dynatrace.plugin.domain.HostImpl;
import com.dynatrace.plugin.util.EnvEmulator;
import com.dynatrace.plugin.util.MQQueueChannelMonitorConstants;

import java.io.*;

import com.ibm.mq.MQC;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MQQueueChannelMonitorUpdated implements Monitor, MQQueueChannelMonitorConstants {
	
	public enum Rate {
		per_millisecond,
		per_second,
		per_minute,
		per_hour
	}
	
	Rate rate;
	private boolean indResetCmd;
	
	private static final Logger log = Logger.getLogger(MQQueueChannelMonitorUpdated.class.getName());

	private String[] METRICS_QM =  {"Status","Connection Count"};
	private String[] METRICS_CHANNEL =  {"STATUS","SUBSTATE","MSGS","BYTES_SENT","BYTES_RECEIVED","BUFFERS_SENT","BUFFERS_RECEIVED", "CONNECTIONS", "CHANNEL_TYPE", "LAST_MSG_DATE", "LAST_MSG_TIME", "CURRENT_SHARING_CONVS"};
	private String[] METRICS_QUEUE =  {	"CURRENT_Q_DEPTH","DEF_PRIORITY", "DEQUEUE_COUNT", "ENQUEUE_COUNT", "INHIBIT_GET", 
			"INHIBIT_PUT", "MAX_MSG_LENGTH", "MAX_Q_DEPTH", "OPEN_INPUT_COUNT", "OPEN_OUTPUT_COUNT",
			"OLDEST_MSG_AGE", "UNCOMMITTED_MSGS", "HIGH_Q_DEPTH", "INT_LAST_GET", "INT_LAST_PUT", 
			"Q_TIME_SHORT", "Q_TIME_LONG", "DEQUEUE_RATE", "ENQUEUE_RATE", "PERCENTAGE_Q_DEPTH"};
	private static final String PARAM_QMNAME = "QMName";
	private static final String PARAM_PORT = "Port";
	private static final String PARAM_CHANNEL = "ChannelName";
	private static final String PARAM_MODEL_QUEUE_NAME = "modelQueueName";
	private static final String PARAM_REPLY_QUEUE_PREFIX = "replyQueuePrefix";
	private static final String PARAM_AUTHENTICATE ="Authenticate";
	private static final String PARAM_USER = "User";
	private static final String PARAM_PASSWORD = "Password";
	private static final String PARAM_COLLECT_QUEUE_INFORMATION = "Collect Queue Information";
	private static final String PARAM_COLLECT_CHANNEL_INFORMATION = "Collect Channel Information";
	private static final String PARAM_QUEUE_NAME_FILTER = "Queue Name Filter";
	private static final String PARAM_CHANNEL_NAME_FILTER = "Channel Name Filter";
	private static final String PARAM_SYSTEM_OBJECT_FILTER = "ignoreSystemObject";
	private static final String PARAM_USE_SSL_CONNECTION = "Use SSL Connection";
	private static final String PARAM_DATE_FORMAT = "dateFormat";
	private static final String PARAM_TIME_FORMAT = "timeFormat";
	private static final String PARAM_RATE = "rate";
	private static final String PARAM_RESET_COMMAND_INDICATOR = "indResetCmd";
	private static final String PARAM_DEBUGGING_QUEUES = "debuggingQueues";
//	private static final HashMap<String, ChannelPropWrapper> allChannels = new HashMap<String, ChannelPropWrapper>(); 
	
	private String qMName = null;
	private Collection<MonitorMeasure>  measures  = null;
	private MQQueueManager qMgr = null;
	private PCFMessageAgent  msgAgent;
	private String modelQueueName = null;
	private String replyQueuePrefix = null;
	private boolean collectQueueInfo=false;
	private boolean collectChannelInfo=false;
	private String queueNameFilter;
//	private String channelNameFilter;
	private boolean ignoreSystem = true;
	private boolean useSSL=false;
	private Hashtable<String, Comparable<?>> environment;
	private int perfEvent;
	private int platform;
	private String[] queueNameFilters;
	private String[] channelNameFilters;
	public static final String[] ALL_QUEUE_NAMES_FILTER = {"*"};
	public static final String[] ALL_CHANNEL_NAMES_FILTER = {"*"};
	public static final String[] EMPTY_ARRAY_STRING = {};
	private String[] debuggingQueues;
	private Map<String, String> debuggingQueuesMap = new HashMap<String, String>();
	
	private static final String PARAM_HOST = "Host";
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String TIME_FORMAT = "HH.mm.ss";
	public static final String DATE_TIME_FORMAT = DATE_FORMAT + " " + TIME_FORMAT;
	private String dateFormat;
	private String timeFormat;
	private SimpleDateFormat sdf;
	private String formatString;
	private String dateString;
	public static final SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy");
	public static final SimpleDateFormat sdfMonth = new SimpleDateFormat("MM");
	public static final SimpleDateFormat sdfDay = new SimpleDateFormat("dd");
	public static final SimpleDateFormat sdfHours = new SimpleDateFormat("HH");
	public static final SimpleDateFormat sdfMinutes = new SimpleDateFormat("mm");
	public static final SimpleDateFormat sdfSeconds = new SimpleDateFormat("ss");
	public static final String SCOLON = ";";
	
	/*
	 * SSL config
	 */
	private String ssl_cipher = null;

	private static final String PARAM_SSL_KEYSTORE = "SSL Keystore Location";
	private static final String PARAM_SSL_KEYSTORE_PWD = "SSL Keystore Password";
	private static final String PARAM_SSL_CIPHER = "SSL Cipher";
	
//	private long rateTimestamp = -1;
	
	/**
	 * Initializes the Plugin. This method is called in the following cases:
	 * <ul>
	 * <li>before <tt>execute</tt> is called the first time for this
	 * scheduled Plugin</li>
	 * <li>before the next <tt>execute</tt> if <tt>teardown</tt> was called
	 * after the last execution</li>
	 * </ul>
	 * <p>
	 * If the returned status is <tt>null</tt> or the status code is a
	 * non-success code then {@link Plugin#teardown() teardown()} will be called
	 * next.
	 * <p>
	 * Resources like sockets or files can be opened in this method.
	 * @param env
	 *            the configured <tt>MonitorEnvironment</tt> for this Plugin;
	 *            contains subscribed measures, but <b>measurements will be
	 *            discarded</b>
	 * @see Plugin#teardown()
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */

	public Status setup(MonitorEnvironment env) throws Exception {
		environment = new java.util.Hashtable<String, Comparable<?>>();
		PCFMessage   request;
		PCFMessage[] responses;
		log.finer("setup method: entering...");
		
		// Queue Manager Name
		qMName = env.getConfigString(PARAM_QMNAME);
		log.finer("setup method: QMName is '" + qMName + "'");
		
		// Get Host
		String hostName = env.getHost().getAddress();
		log.finer("setup method: hostName is '" + hostName + "'");
		
		// Get Port
		String port = env.getConfigString(PARAM_PORT);
		log.finer("setup method: port is '" + port + "'");
		
		// Get Channel
		String channel = env.getConfigString(PARAM_CHANNEL);
		log.finer("setup method: hostName = '" + hostName + "', port = '" + port + "', channel = '" + channel + "'");
		
		// Set model queue name
		modelQueueName = (modelQueueName = env.getConfigString(PARAM_MODEL_QUEUE_NAME)) != null ? modelQueueName.trim() : null;
		log.finer("setup method: modelQueueName = '" + modelQueueName + "'");
		
		// Set reply queue prefix
		replyQueuePrefix = (replyQueuePrefix = env.getConfigString(PARAM_REPLY_QUEUE_PREFIX)) != null ? replyQueuePrefix.trim() : null;
		log.finer("setup method: replyQueuePrefix = '" + replyQueuePrefix + "'");
		
		if (modelQueueName != null && !modelQueueName.isEmpty() && (replyQueuePrefix == null || replyQueuePrefix.isEmpty())) {
			String msg = "setup method: modelQueueName is set to '" + modelQueueName + "', while replyQueuePrefix is null or empty string.";
			log.severe(msg);
			return new Status(Status.StatusCode.ErrorInternalConfigurationProblem, msg);
		}
		
		// Get Collect Queue Information
		collectQueueInfo = env.getConfigBoolean(PARAM_COLLECT_QUEUE_INFORMATION).booleanValue();
		
		// Get Collect Channel Info
		collectChannelInfo = env.getConfigBoolean(PARAM_COLLECT_CHANNEL_INFORMATION).booleanValue();

		if ( qMName == null || qMName.equals("") || port == null || port.equals("") || channel == null || channel.equals("")) {
			log.severe("setup method: All the required parameters are not defined.");
			return new Status(Status.StatusCode.ErrorInternalConfigurationProblem, "All the required parameters are not defined.");
		}
		
		// Get date format
		dateFormat = (env.getConfigString(PARAM_DATE_FORMAT) == null ? DATE_FORMAT : env.getConfigString(PARAM_DATE_FORMAT));
		log.finer("setup method: dateFormat is '" + dateFormat + "'");
		
		// Get time format
		timeFormat = (env.getConfigString(PARAM_TIME_FORMAT) == null ? TIME_FORMAT : env.getConfigString(PARAM_TIME_FORMAT));
		log.finer("setup method: timeFormat is '" + timeFormat + "'");
		
		try {
			sdf = new SimpleDateFormat(formatString = new StringBuilder().append(dateFormat)
					.append(" ").append(timeFormat).toString());
		} catch (Exception e) {
			sdf = new SimpleDateFormat(DATE_TIME_FORMAT);
		}
		
		// get rate
		String value = null;
		try {
			rate = Rate.valueOf((value = env.getConfigString(PARAM_RATE)) != null ? value : Rate.per_millisecond.name());
		} catch (IllegalArgumentException e) {
			log.warning("setup method: rate configuration parameter '" + (value == null ? "null" : value) + "' threw IllegalArgumentException. Rate.per_millisecond will be used. Stacktrace is '" + getExceptionAsString(e) + "'");
			rate = Rate.per_millisecond;
		} catch (NullPointerException e) {
			log.warning("setup method: rate configuration parameter '" + (value == null ? "null" : value) + "' threw IllegalArgumentException. Rate.per_millisecond will be used. Stacktrace is '" + getExceptionAsString(e) + "'");
			rate = Rate.per_millisecond;
		}
		
		// set Reset Command Ind
		indResetCmd = env.getConfigBoolean(PARAM_RESET_COMMAND_INDICATOR);
		log.finer("setup method: Reset Command Indicator is '" + indResetCmd + "'");
		// Get Queue Name Filter 
		queueNameFilter = env.getConfigString(PARAM_QUEUE_NAME_FILTER);
		if ( queueNameFilter == null || queueNameFilter.isEmpty()) {
			queueNameFilter = "*";
		}
		
		//Added filtering for queue names
		// Set channelNameFilters
		queueNameFilters = trimArray((value = env.getConfigString(PARAM_QUEUE_NAME_FILTER)) != null && !value.trim().isEmpty() ? value.split(SCOLON) : ALL_QUEUE_NAMES_FILTER);
				
		log.finer("setup method: queueNameFilters are '" + Arrays.toString(queueNameFilters) + "'");
		//Done adding filtering for queue names
		
		log.finer("setup method: Queue Name Filter is '" + queueNameFilter + "'");

		// Get Channel Name Filter
		/*
		channelNameFilter = env.getConfigString(PARAM_CHANNEL_NAME_FILTER);
		if ( channelNameFilter == null || channelNameFilter.isEmpty()) {
			channelNameFilter = "*";
		}
		log.finer("setup method: Channel Name Filter is '" + channelNameFilter + "'");
		*/
		// Set channelNameFilters
		channelNameFilters = trimArray((value = env.getConfigString(PARAM_CHANNEL_NAME_FILTER)) != null && !value.trim().isEmpty() ? value.split(SCOLON) : ALL_CHANNEL_NAMES_FILTER);
		
		log.finer("setup method: channelNameFilters are '" + Arrays.toString(channelNameFilters) + "'");
		
		// Get Use SSL Connection
		useSSL = env.getConfigBoolean(PARAM_USE_SSL_CONNECTION).booleanValue();
		if (useSSL){
			log.finer("Setting up java SSL");					
			ssl_cipher = env.getConfigString(PARAM_SSL_CIPHER);
			String cipherEquivalent;
			environment.put(MQC.SSL_CIPHER_SUITE_PROPERTY, cipherEquivalent = getCipherEquiv(ssl_cipher));
			log.finer("setup method: original cipher is '" + ssl_cipher + "', cipher equivalent is '" + cipherEquivalent + "'");
			System.setProperty("javax.net.ssl.keyStore",env.getConfigString(PARAM_SSL_KEYSTORE)); 
			System.setProperty("javax.net.ssl.keyStorePassword",env.getConfigPassword(PARAM_SSL_KEYSTORE_PWD));				
			System.setProperty("javax.net.ssl.trustStore",env.getConfigString(PARAM_SSL_KEYSTORE)); 
			System.setProperty("javax.net.ssl.trustStorePassword",env.getConfigPassword(PARAM_SSL_KEYSTORE_PWD));				
			log.finer("Setting up java SSL - Done!");
		}

		// get Authenticate parameter
		//TODO check if Authenticate parameter is not null
		if (env.getConfigBoolean(PARAM_AUTHENTICATE)) {
			//TODO make sure that userid and password are not null or empty
			environment.put("userID", env.getConfigString(PARAM_USER));
			environment.put("password", env.getConfigPassword(PARAM_PASSWORD));
		}
		
		// Get Ignore System Object
		ignoreSystem = env.getConfigBoolean(PARAM_SYSTEM_OBJECT_FILTER);
		
		// Get Debugging Queues
		debuggingQueues = trimArray((value = env.getConfigString(PARAM_DEBUGGING_QUEUES)) != null && !value.trim().isEmpty() ? value.split(SCOLON) : EMPTY_ARRAY_STRING);
		log.finer("setup method: debuggingQueue is " + Arrays.toString(debuggingQueues));
		debuggingQueuesMap = getMapFromArray(debuggingQueues);
		
		// Populate environment hash table
		environment.put("hostname", hostName);
		environment.put("port", Integer.parseInt(port));
		environment.put("channel", channel);
		log.finer("HashTable environment is '" + Arrays.toString(environment.entrySet().toArray()) + "'");
		
		// get platform to separate logic for z/OS from the distributed platforms
		try {
			qMgr = new MQQueueManager(qMName, environment);
			if (modelQueueName != null && !modelQueueName.isEmpty()) {
				log.finer("setup method: modelQueueName is '" + modelQueueName + "'");
				msgAgent = new PCFMessageAgent();
				msgAgent.setModelQueueName(modelQueueName);
				msgAgent.setReplyQueuePrefix(replyQueuePrefix);
				msgAgent.connect(qMgr);
			} else {
				log.finer("setup method: model queue is not used");
				msgAgent = new PCFMessageAgent(qMgr);
			}
				
			request = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q_MGR);
				
			request.addParameter(MQConstants.MQIACF_Q_MGR_ATTRS, new int [] {MQConstants.MQIA_PERFORMANCE_EVENT, MQConstants.MQIA_PLATFORM});
			responses = msgAgent.send(request);
				
			int cc;
			if ((cc = responses[0].getCompCode()) == MQConstants.MQCC_OK) {
				perfEvent = responses[0].getIntParameterValue(CMQC.MQIA_PERFORMANCE_EVENT);
				log.finer("perfEvent is '" + getPerformanceEventAsString(perfEvent) + "'");
				platform = responses[0].getIntParameterValue(CMQC.MQIA_PLATFORM);
				log.finer("platform where WMQ is running on is '" + getPlatformAsString(platform) + "'");
			} else {
				String s = new StringBuilder(
						"setup method: PCFMessage: Completion Code is '").append(cc).append("' which is not OK('").append(MQConstants.MQCC_OK).append("')").toString();
				log.severe(s);
				return new Status(Status.StatusCode.ErrorInfrastructure, s, s);
			}
		} catch (PCFException e) {
			String s = "setup method: " + getMQDataExceptionAsString((MQDataException)e);
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		} catch (MQDataException e) {
			String s = "setup method: " + getMQDataExceptionAsString(e);
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		} catch (MQException e) {
			String s = "setup method: " + getMQExceptionAsString(e);
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		} catch (IOException e) {
			String s = "setup method: " + getExceptionAsString(e);
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		} catch (Exception e) {
			String s = "setup method: " + getExceptionAsString(e);
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		}

		log.finer("setup: exiting...");		
		return new Status(Status.StatusCode.Success);
	}
	
	public static Map<String, String> getMapFromArray(String[] as) {
		if (as == null) {
			return null;
		}
		
		Map<String, String> map = new HashMap<String, String>();
		for (String s : as) {
			map.put(s, s);
		}
		
		return map;
	}
	
	public static String[] trimArray(String[] array) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering trimArray method");
		}
		if (array != null) {
			List<String> list = new ArrayList<String>();
			for (String element : array) {
				if ((element = element.trim()).isEmpty()) {
					continue;
				} else {
					list.add(element);
				}
			}
			return list.toArray(new String[list.size()]);
		} else {
			return null;
		}
	}

	/**
	 * Get the equivalent cipher
	 * 
	 */
	private String getCipherEquiv(String inCipher){
		String outCipher = "";
		
		if (inCipher.equalsIgnoreCase("NULL_MD5")) outCipher = "SSL_RSA_WITH_NULL_MD5";
		else if (inCipher.equalsIgnoreCase("NULL_SHA")) outCipher  = "SSL_RSA_WITH_NULL_SHA";
		else if (inCipher.equalsIgnoreCase("RC4_MD5_EXPORT")) outCipher  = "SSL_RSA_EXPORT_WITH_RC4_40_MD5";
		else if (inCipher.equalsIgnoreCase("RC4_MD5_US")) outCipher  = "SSL_RSA_WITH_RC4_128_MD5";
		else if (inCipher.equalsIgnoreCase("RC4_SHA_US")) outCipher  = "SSL_RSA_WITH_RC4_128_SHA";
		else if (inCipher.equalsIgnoreCase("RC2_MD5_EXPORT")) outCipher  = "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5";
		else if (inCipher.equalsIgnoreCase("DES_SHA_EXPORT")) outCipher  = "SSL_RSA_WITH_DES_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("RC4_56_SHA_EXPORT1024")) outCipher  = "SSL_RSA_EXPORT1024_WITH_RC4_56_SHA";
		else if (inCipher.equalsIgnoreCase("DES_SHA_EXPORT1024")) outCipher  = "SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("TRIPLE_DES_SHA_US")) outCipher  = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("TLS_RSA_WITH_AES_128_CBC_SHA")) outCipher  = "SSL_RSA_WITH_AES_128_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("TLS_RSA_WITH_AES_256_CBC_SHA5")) outCipher  = "SSL_RSA_WITH_AES_256_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("AES_SHA_US2")) outCipher  = ""; 	  	  	 
		else if (inCipher.equalsIgnoreCase("TLS_RSA_WITH_DES_CBC_SHA")) outCipher  = "SSL_RSA_WITH_DES_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("TLS_RSA_WITH_3DES_EDE_CBC_SHA")) outCipher  = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("FIPS_WITH_DES_CBC_SHA")) outCipher  = "SSL_RSA_FIPS_WITH_DES_CBC_SHA";
		else if (inCipher.equalsIgnoreCase("FIPS_WITH_3DES_EDE_CBC_SHA")) outCipher  = "SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA";		
		log.finer(outCipher);		
		return outCipher;
	}
	
	/**
	 * Executes the Monitor Plugin to retrieve subscribed measures and store
	 * measurements.
	 *
	 * <p>
	 * This method is called at the scheduled intervals. If the Plugin execution
	 * takes longer than the schedule interval, subsequent calls to
	 * {@link #execute(MonitorEnvironment)} will be skipped until this method
	 * returns. After the execution duration exceeds the schedule timeout,
	 * {@link TaskEnvironment#isStopped()} will return <tt>true</tt>. In this
	 * case execution should be stopped as soon as possible. If the Plugin
	 * ignores {@link TaskEnvironment#isStopped()} or fails to stop execution in
	 * a reasonable timeframe, the execution thread will be stopped ungracefully
	 * which might lead to resource leaks!
	 *
	 * @param env
	 *            a <tt>MonitorEnvironment</tt> object that contains the
	 *            Plugin configuration and subscribed measures. These
	 *            <tt>MonitorMeasure</tt>s can be used to store measurements.
	 * @return a <tt>Status</tt> object that describes the result of the
	 *         method call
	 */


	public Status execute(MonitorEnvironment env) throws Exception {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering execute method");
			log.finer("execute method: MQQueueChannelMonitorUpdated hash code is '" + this.hashCode() + "'");
		}		
		Status status = new Status(StatusCode.Success);
		
		try {
			// disconnect qMgr and msgAgent on zOS or if at least one of qMgr or msgAgent is null
			if (/*platform == CMQC.MQPL_ZOS ||*/ qMgr == null || msgAgent == null) {
				log.finer("execute method: disconnect qMgr and msgAgent if at least one of qMgr or msgAgent is null");
				disconnect();
			}
			
			// re-connect qMgr and msgAgent
			if (qMgr == null || msgAgent == null) {
				log.finer("execute method: re-connect qMgr and msgAgent");
				log.finer("execute method: environment is " + Arrays.toString(environment.entrySet().toArray()));
				qMgr = new MQQueueManager(qMName, environment);
				if (modelQueueName != null && !modelQueueName.isEmpty()) {
					log.finer("execute method: modelQueueName is '" + modelQueueName + "'");
					msgAgent = new PCFMessageAgent();
					msgAgent.setModelQueueName(modelQueueName);
					msgAgent.setReplyQueuePrefix(replyQueuePrefix);
					msgAgent.connect(qMgr);
				} else {
					log.finer("execute method: model queue is not used");
					msgAgent = new PCFMessageAgent(qMgr);
				}
			}
			
			log.finer("execute method: before calling populateQmMetrics method");
			if ((status = populateQmMetrics(env)).getStatusCode().getCode() !=  Status.StatusCode.Success.getCode()) {
				log.severe("execute method: populateQmMetrics method completed with status code '" + status.getStatusCode().getCode() + "' and message '" + status.getMessage() + "'");
				disconnect();
				return status;
			}
			
			log.finer("execute method: collectChannelInfo value is '" + collectChannelInfo + "'");
			if (collectChannelInfo) {
//				Map<String, ChannelData> channels = new HashMap<String, ChannelData>(); 
				log.finer("execute method: before calling getChannelNames method");
//				getChannelNames(allChannels);
				log.finer("execute method: before calling getCompleteChProp method");
				Map<String, String> channelsDups = new HashMap<String, String>();
				for(String channelNameFilter : channelNameFilters) {
					log.finer("execute method: channelsDups is '" + Arrays.toString(channelsDups.entrySet().toArray()) + "'");
					Status stat;
					stat = getCompleteChProp(env/*, channels*/, channelNameFilter, channelsDups);
					if (stat.getStatusCode().getBaseCode() > status.getStatusCode().getBaseCode()) {
						status = stat;
					}
				}
			}
			
			log.finer("execute method: collectQueueInfo value is '" + collectQueueInfo + "'");
			if (collectQueueInfo) {
				/*
				Old code to execute for queues
				
				log.finer("execute method: before calling getQueueProp method");
				status = getQueueProp(env);
				log.finer("execute method: after calling getQueueProp method");*/
				
				//New code to execute for queue filtering
				log.finer("execute method: before calling getQueueProp method");
				for(String filteredqueueNameFilter : queueNameFilters)
				{
					Status stat;
					log.finer("Entering getQueueProp for queue filter" + filteredqueueNameFilter);
					stat = getQueueProp(env, filteredqueueNameFilter);
					if (stat.getStatusCode().getBaseCode() > status.getStatusCode().getBaseCode()) {
						status = stat;
					}
					log.finer("Exiting getQueueProp for queue filter" + filteredqueueNameFilter);
				}
				log.finer("execute method: after calling getQueueProp method");
			}
		} catch(PCFException e) {
			String s = new StringBuilder(
					"execute method: PCFException Occurred. Reason Code = ")
					.append(e.getReason()).append("; Error code = ")
					.append(e.getErrorCode()).append("; Message = '")
					.append(e.getMessage()).append("'; Stacktrace is '")
					.append(getStackTraceString(e)).append("'").toString();
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInternalException, s, s, e);
		} catch(MQException e) {
			String s = getMQExceptionAsString(e); 
			log.severe("execute method: " + s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInternalException, s, s, e);

		} catch (Exception e) {
			String s = getExceptionAsString(e); 
			log.severe(s);
			disconnect();
			return new Status(Status.StatusCode.ErrorInfrastructure, s, s, e);
		}
		
		return status;
	}
	
	public String getMQExceptionAsString(MQException e) {
		return new StringBuilder(
				e.getClass().getCanonicalName() + " exception occured. Reason Code = ")
				.append(e.getReason()).append("; Error code = ")
				.append(e.getErrorCode()).append("; Message = '")
				.append(e.getMessage()).append("'; Stacktrace is '")
				.append(getStackTraceString(e)).append("'").toString();
	}
	
	public String getMQDataExceptionAsString(MQDataException e) {
		return new StringBuilder(
				e.getClass().getCanonicalName() + " exception occured. Reason Code = ")
				.append(e.getReason()).append("; Error code = ")
				.append(e.getErrorCode()).append("; Message = '")
				.append(e.getMessage()).append("'; Stacktrace is '")
				.append(getStackTraceString(e)).append("'").toString();
	}
	
	public String getExceptionAsString(Exception e) {
		return new StringBuilder(
				e.getClass().getCanonicalName() + " exception occurred. Message = '")
				.append(e.getMessage()).append("'; Stacktrace is '")
				.append(getStackTraceString(e)).append("'").toString();
	}

	private Status populateQmMetrics(MonitorEnvironment env) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("populateQmMetrics method: calling populateQmMetrics method");
		}
		Status status = new Status(StatusCode.Success);
		
		PCFMessage[] responses;
		int qmStatus = Integer.MIN_VALUE;
		int connectionsCount = Integer.MIN_VALUE;
		
		// get Queue Manager status and # of connections
		if (platform == CMQC.MQPL_ZOS) {
			// handling z/OS platform
			qmStatus = MQConstants.MQRC_Q_MGR_ACTIVE;
			try {
				PCFMessage request = new PCFMessage(MQConstants.MQCMD_INQUIRE_CONNECTION);
				request.addParameter(CMQCFC.MQBACF_GENERIC_CONNECTION_ID, new byte[0]);
				request.addParameter(CMQCFC.MQIACF_CONNECTION_ATTRS, new int[] { CMQCFC.MQIACF_ALL }); 
				responses = msgAgent.send(request);
				
				// get connections count
				if (responses != null && responses.length > 0 && (responses[0]).getCompCode() == MQConstants.MQCC_OK) {
					connectionsCount = responses.length;
					log.finer("execute method: connectionsCount is '" + connectionsCount + "'");
				}
			} catch (MQDataException e) {
				qmStatus = e.getReason();
				String s = getMQDataExceptionAsString(e);
				// this block contains only two metrics which we are capturing, i.e. qmStatus and connections count
				// we will continue execution of the plugin even if exception happened here
				// just log exception
				log.severe(s);
				disconnect();
				status = new Status(StatusCode.PartialSuccess, s, s, e);
			} catch (IOException e) {
				qmStatus = Integer.MIN_VALUE;
				String s = getExceptionAsString(e);
				// this block contains only two metrics which we are capturing, i.e. qmStatus and connections count
				// we will continue execution of the plugin even if exception happened here
				// just log exception continue exception: will exit later on of we have other exceptions
				log.severe(s);
				disconnect();
				status = new Status(StatusCode.PartialSuccess, s, s, e);
			}
		} else {
			// handling distributed platforms
			try {
				PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_MGR_STATUS);
				request.addParameter(CMQCFC.MQIACF_Q_MGR_STATUS_ATTRS, new int [] {CMQCFC.MQIACF_ALL});
				responses = msgAgent.send(request);
				
				// get qmStatus and connections count
				if (responses != null && responses.length > 0 && (responses[0]).getCompCode() == MQConstants.MQCC_OK) {
					qmStatus = responses[0].getIntParameterValue(CMQCFC.MQIACF_Q_MGR_STATUS);
					connectionsCount = responses[0].getIntParameterValue(CMQCFC.MQIACF_CONNECTION_COUNT);
				}
			} catch (PCFException e) {
				qmStatus = e.getReason();
				String s = getMQDataExceptionAsString((MQDataException)e);
				// this block contains only two metrics which we are capturing, i.e. qmStatus and connections count
				// we will continue execution of the plugin even if exception happened here
				// just log exception
				log.severe(s);
				disconnect();
				status = new Status(StatusCode.PartialSuccess, s, s, e);
			} catch (MQDataException e) {
				qmStatus = e.getReason();
				String s = getMQDataExceptionAsString(e);
				// this block contains only two metrics which we are capturing, i.e. qmStatus and connections count
				// we will continue execution of the plugin even if exception happened here
				// just log exception
				log.severe(s);
				disconnect();
				status = new Status(StatusCode.PartialSuccess, s, s, e);
			} catch (IOException e) {
				String s = getExceptionAsString(e);
				// this block contains only two metrics which we are capturing, i.e. qmStatus and connections count
				// we will continue execution of the plugin even if exception happened here
				// just log exception continue exception: will exit later on of we have other exceptions
				log.severe(s);
				disconnect();
				status = new Status(StatusCode.PartialSuccess, s, s, e);
			}
		}
			
		// Set measures for qmStatus
		if ((measures = env.getMonitorMeasures("Queue Manager Group", METRICS_QM[0])) != null && !measures.isEmpty()) {
			for (MonitorMeasure measure : measures) {
				measure.setValue(qmStatus != Integer.MIN_VALUE ? qmStatus : Double.NaN);
			}
		}
		
		// set measures for connectionsCount
		if ((measures = env.getMonitorMeasures("Queue Manager Group", METRICS_QM[1])) != null && !measures.isEmpty()) {
			for (MonitorMeasure measure : measures){
				measure.setValue(connectionsCount != Integer.MIN_VALUE ? connectionsCount : Double.NaN);
			}
		}
		
		return status;
	}
	
	public static String getPerformanceEventAsString(int perfEvent) {
		String perfEventString = "UNKNOWN";
		switch(perfEvent) {
		case CMQCFC.MQEVR_DISABLED:
			perfEventString = "DISABLED";
			break;
		case CMQCFC.MQEVR_ENABLED:
			perfEventString = "ENABLED";
			break;
		}
		
		return perfEventString;
	}
	
	public static String getPlatformAsString(int platform) {
		String platformString = "UNKNOWN";
		switch(platform) {
		case CMQC.MQPL_NSK:
			platformString = "NSK";
			break;
		case CMQC.MQPL_OS400:
			platformString = "OS400";
			break;
		case CMQC.MQPL_UNIX:
			platformString = "UNIX";
			break;
		case CMQC.MQPL_VMS:
			platformString = "VMS";
			break;
		case CMQC.MQPL_WINDOWS_NT:
			platformString = "WINDOWS_NT";
			break;
		case CMQC.MQPL_ZOS:
			platformString = "ZOS";
			break;
		}
		
		return platformString;
	}
	
	private String getStackTraceString(Exception e) {
		ByteArrayOutputStream ba = new ByteArrayOutputStream();
		e.printStackTrace(new  PrintStream(ba));
		return ba.toString();
	}


	public Status getCompleteChProp(MonitorEnvironment env/*Map<String, ChannelData> channels*/, String channelNameFilter, Map<String, String> channelsDups) throws Exception{
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getCompleteChProp method");
		}
		Status status = new Status(StatusCode.Success);
		
//		Collection<ChannelData> channelPropWrappers = new ArrayList<ChannelData>();
		Map<String, ChannelData> channelConnections = new HashMap<String, ChannelData>();
		try {

			PCFMessage   request; //,request1;
			PCFMessage[] responses; //,responses1;
			request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS);
		    // add a parameter designating the name of the channel for which status is requested 
		    request.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, channelNameFilter); 
		    // add a parameter designating the instance type (current) desired 
		    request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, CMQC.MQOT_CURRENT_CHANNEL);
		    // add a parameter designating the attributes desired, but first... 
		    // ...build an array list of attributes desired 
//		    int[] attrs = {CMQCFC.MQCACH_CHANNEL_NAME, 
//		                     CMQCFC.MQIACH_MSGS, 
//		                     CMQCFC.MQCACH_LAST_MSG_DATE, 
//		                     CMQCFC.MQCACH_LAST_MSG_TIME, 
//		                     CMQCFC.MQIACH_CHANNEL_STATUS,
//		                     CMQCFC.MQIACH_CHANNEL_TYPE, // added ET: 02-19-2014
//		                     CMQCFC.MQIACH_CURRENT_SHARING_CONVS, // added ET: 02-19-2014
//		                     CMQCFC.MQCACH_CHANNEL_START_TIME,
//		                     CMQCFC.MQIACH_CHANNEL_SUBSTATE,
//		                     CMQCFC.MQIACH_BYTES_RECEIVED,
//		                     CMQCFC.MQIACH_BYTES_SENT,
//		                     CMQCFC.MQIACH_BUFFERS_RECEIVED,
//		                     CMQCFC.MQIACH_BUFFERS_SENT};
//		    request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_ATTRS, attrs);
		    
		    log.finer("getCompleteChProp method: before calling msgAgent.send(request) method");
			responses = msgAgent.send(request);
			log.finer("getCompleteChProp method: after calling msgAgent.send(request) method");
			if (responses != null) {
				log.finer("getCompleteChProp method: size of the 'responses' array is " + responses.length);
				
			int l = responses.length;
			
			for (int i = 0; i < l; i++)	{
				log.finer("getCompleteChProp method: response i = " + i + " before extracting values");
				try{
					log.finer("getCompleteChProp method: before checking MQCC_OK");
					if ((responses[i]).getCompCode() == MQConstants.MQCC_OK)
					{
						log.finer("getCompleteChProp method: after checking MQCC_OK");
						ChannelData channel = new ChannelData();
						try {
							log.finer("getCompleteChProp method: get obj");
							Object obj = responses[i].getParameterValue(MQConstants.MQIACH_CHANNEL_STATUS);
							//TODO check with IBM when obj can be null
							if (obj == null) {
								// skip response
								log.finer("getCompleteChProp method: skip response");
								continue;
							}
							log.finer("getCompleteChProp method: before setting channel fields");
							channel.eChannelStatus = (Integer) obj;
							channel.eChannelName = responses[i].getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
							channel.eConnName = (String) responses[i].getStringParameterValue(MQConstants.MQCACH_CONNECTION_NAME).trim();
							log.finer("getCompleteChProp method: #" + i + " channel.eChannelName is '" + channel.eChannelName + "', connection name is '" + channel.eConnName + "'");
							channel.eChannelType = responses[i].getIntParameterValue(CMQCFC.MQIACH_CHANNEL_TYPE); // added ET: 02-19-2014 
							if (channel.eChannelType == MQConstants.MQCHT_SVRCONN) {
								try {
									channel.eCurrentSharingConvs = responses[i].getIntParameterValue(CMQCFC.MQIACH_CURRENT_SHARING_CONVS); // added ET: 02-19-2014 
								} catch (Exception e) {
									if (log.isLoggable(Level.FINER)) {
										String s = getExceptionAsString(e); 
										log.finer("getCompleteChProp method: " + s);
									}
									channel.eCurrentSharingConvs = Integer.MIN_VALUE;
								}
							} else {
								channel.eCurrentSharingConvs = Integer.MIN_VALUE;
							}
							log.finer("getCompleteChProp method: #" + i + " channel.eChannelName is '" + channel.eChannelName + 
									"', connection name is '" + channel.eConnName + 
									"', channel type is " + channel.eChannelType + 
									", current sharing conversations " + (channel.eCurrentSharingConvs == Integer.MIN_VALUE ? "-" : channel.eCurrentSharingConvs));
							channel.eChannelSubStatus = responses[i].getIntParameterValue(CMQCFC.MQIACH_CHANNEL_SUBSTATE);
							channel.eChannelMsgs = responses[i].getIntParameterValue(CMQCFC.MQIACH_MSGS);
							channel.eChlastMsgDate = responses[i].getStringParameterValue(CMQCFC.MQCACH_LAST_MSG_DATE).trim() ;
							log.finer("getCompleteChProp method: #" + i + " channel.eChlastMsgDate is '" + channel.eChlastMsgDate + "'");
							channel.eChllastMsgTime = responses[i].getStringParameterValue(CMQCFC.MQCACH_LAST_MSG_TIME).trim();
							log.finer("getCompleteChProp method: #" + i + " channel.eChllastMsgTime is '" + channel.eChllastMsgTime + "'");
							// convert CMQCFC.MQCACH_LAST_MSG_DATE string into Date object
							synchronized (sdf) {
								
								try {
									if (channel.eChlastMsgDate != null && channel.eChllastMsgTime != null) {
										channel.eLastMsgDate = sdf
											.parse(dateString = new StringBuilder()
													.append(channel.eChlastMsgDate)
													.append(" ")
													.append(channel.eChllastMsgTime)
													.toString());
									} else {
										channel.eLastMsgDate = null;
									}
								} catch (Exception e) {
									
									channel.eLastMsgDate = null;
								}
							}
							log.finer("getCompleteChProp method: #" + i + " channel.eLastMsgDate is '" 
									+ channel.eLastMsgDate + "', date format is '" + formatString + "', date string is '"
									+ dateString);
							
							channel.eChlStartTime = responses[i].getStringParameterValue(CMQCFC.MQCACH_CHANNEL_START_TIME).trim() ;
							log.finer("getCompleteChProp method: #" + i + " channel.eChlStartTime is '" + channel.eChlStartTime + "'");
							channel.eBytesSent = responses[i].getIntParameterValue(CMQCFC.MQIACH_BYTES_SENT);
							channel.eBytesReceived = responses[i].getIntParameterValue(CMQCFC.MQIACH_BYTES_RECEIVED) ;
							channel.eBufferSent = responses[i].getIntParameterValue(CMQCFC.MQIACH_BUFFERS_SENT) ;
							channel.eBufferReceived = responses[i].getIntParameterValue(CMQCFC.MQIACH_BUFFERS_RECEIVED) ;
							log.finer("getCompleteChProp method: after setting channel` fields");
							// save and aggregate channel's data 
							//aggregate channels first
							aggregateChannelStats(channelConnections, channel);
							log.finer("getCompleteChProp method: aggregateChannelStats #1: channelConnections is '" + Arrays.toString(channelConnections.entrySet().toArray()) + "'");
							// save channel connections
							String key = new StringBuilder(channel.eChannelName).append("|").append(channel.eConnName).toString();
							if (!channelConnections.containsKey(key)) {
								channelConnections.put(key, channel);
							} else {
								String newKey = getUniqueKey(channelConnections, key);
								channelConnections.put(newKey, channel);
								log.finer("getCompleteChProp method: added channel with a new key '" + newKey + "'");
								
//								String msg = new StringBuilder("getCompleteChProp method: channel '").append(channel.eChannelName)
//										.append("' and connection '")
//										.append(channel.eConnName)
//										.append("' are already exists in the channelConnections map under the key '")
//										.append(key)
//										.append("'. Duplicate record skipped.")
//										.toString();
//								log.severe(msg);
//								status = new Status(StatusCode.PartialSuccess, msg, msg);								
							}
							log.finer("getCompleteChProp method: aggregateChannelStats #2: channelConnections is '" + Arrays.toString(channelConnections.entrySet().toArray()) + "'");
						} catch(Exception ex) {
							ex.printStackTrace();
							log.severe("getCompleteChProp method: first catch block '" + getExceptionAsString(ex) + "'");
							String s = getExceptionAsString(ex); 
							log.severe("getCompleteChProp method: " + s);
							disconnect();
							throw new Exception(ex.getMessage());
						}
						log.finer("getCompleteChProp method: response i = " + i + " after extracting values");
						
						// check if we have duplicates
						Set<String> keys = channelConnections.keySet();
						log.finer("getCompleteChProp method: channelsDups before checking dups is '" + Arrays.toString(channelsDups.entrySet().toArray()) + "'");
						for (String key : keys) {
							log.finer("getCompleteChProp method: channelsDups loop key is '" + key + "'"); 
							if (!isAggregate(key)) { 
								continue;
							}
							if (channelsDups.containsKey(key)) {
								// check if they have different filters
								if (!channelsDups.get(key).equals(channelNameFilter)) {
									String msg = "getCompleteChProp method: duplicate channel - '" + key + "', original filter for the channel is '" 
											+ channelsDups.get(key) + "', filter which caused duplicates is "+ channelNameFilter + "'. Adjust filers for channel names that they do not overlap.";
									log.severe(msg);
									disconnect();
									throw new RuntimeException(msg);
								}
								
							} else {
								channelsDups.put(key,  channelNameFilter);
							}
						}
						
						log.finer("getCompleteChProp method: channelsDups after checking dups is '" + Arrays.toString(channelsDups.entrySet().toArray()) + "'");
						
					}
				} catch(Exception ex){
					log.severe("Exception Occurred=" + ex.getMessage());
					ex.printStackTrace();
					log.severe("getCompleteChProp method: second catch block '" + getExceptionAsString(ex) + "'");
					String s = getExceptionAsString(ex); 
					log.severe("getCompleteChProp method: " + s);
					disconnect();
					throw new Exception(ex.getMessage(), ex);

				}
			}
			log.finer("getCompleteChProp method: number of individual channels and channels with multiple connections is " + channelConnections.size());
			} else {
				log.finer("getCompleteChProp method: size of the 'responses' array is null");
			}
			
//			log.finer("getCompleteChProp method: process channels");
			if(channelConnections != null ){
//				log.finer("getCompleteChProp method: process channelConnections");
//				if (channelConnections != null) {
//					//Replace the actual value of the channel properties from channelConnections into allChannels
//					log.finer("getCompleteChProp method: size of the channelConnections is " + channelConnections.size());
//					for (ChannelData channelPropWrapper : channelConnections) {
//						String channelName = channelPropWrapper.eChannelName;
//						log.finer("getCompleteChProp method: size of the channelConnections is " + channelConnections.size());
//						if (channels.containsKey(channelName)) {
//							log.finer("getCompleteChProp method: replacing content for channel '" + channelName + "' with the channelPropWrapper '" + channelPropWrapper.toString() + "'");
////							allChannels.remove(channelName);
//							channels.put(channelName, channelPropWrapper);
//						} else {
//							log.finer("getCompleteChProp method: channel + '" + channelName + "' is not found in the allChannels map");
//						}
//					}
//				}

				log.finer("getCompleteChProp method: set dynamic measures for channels");
				Set<String> keys = channelConnections.keySet();
				for (int index = 0; index < METRICS_CHANNEL.length; index++) {
					log.finer("getCompleteChProp method: index is " + index);
					for (String key : keys) {
						log.finer("getCompleteChProp method: index is " + index + " channelName (possibly including connection name) is '" + key + "'");
						ChannelData channel = channelConnections.get(key);
						if (channel == null || (key.startsWith("SYSTEM") && ignoreSystem)) {
							String s = channel == null ? "channelPropWrapper is null" : "this channel starts with 'SYSTEM'";
							log.finer("getCompleteChProp method: index is " + index + " channelName is '" + key + "' move to next channel because " + s);
							continue;
						}
						Collection<MonitorMeasure> monitorMeasures = env.getMonitorMeasures("Channel Group", METRICS_CHANNEL[index]);
						for (MonitorMeasure subscribedMonitorMeasure : monitorMeasures) {
							MonitorMeasure dynamicMeasure = env.createDynamicMeasure(subscribedMonitorMeasure, "Channel Name", /*channel.eChannelName*/key);
							switch (index) {
							case 0:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eChannelStatus with value " + channel.eChannelStatus);
								if (channel.eChannelStatus == Integer.MIN_VALUE) {
									dynamicMeasure.setValue(Double.NaN);
								} else {
									dynamicMeasure.setValue(channel.eChannelStatus);
								}
								break;
							case 1:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eChannelSubStatus with value " + channel.eChannelSubStatus);
								if (channel.eChannelSubStatus == Integer.MIN_VALUE) {
									dynamicMeasure.setValue(Double.NaN);
								} else {
									dynamicMeasure.setValue(channel.eChannelSubStatus);
								}
								break;
							case 2:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eChannelMsg with value " + channel.eChannelMsgs);
								dynamicMeasure.setValue(channel.eChannelMsgs);
								break;
							case 3:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eBytesSent with value " + channel.eBytesSent);
								dynamicMeasure.setValue(channel.eBytesSent);
								break;
							case 4:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eBytesReceived with value " + channel.eBytesReceived);
								dynamicMeasure.setValue(channel.eBytesReceived);
								break;
							case 5:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eBufferSent with value " + channel.eBufferSent);
								dynamicMeasure.setValue(channel.eBufferSent);
								break;
							case 6:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eBufferReceived with value " + channel.eBufferReceived);
								dynamicMeasure.setValue(channel.eBufferReceived);
								break;
							case 7:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.connections with value " + channel.getConnections());
								dynamicMeasure.setValue(channel.getConnections());
								break;
							case 8:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eChannelType with value " + channel.eChannelType);
								dynamicMeasure.setValue(channel.eChannelType);
								break;
							case 9:
								Double dDate = getIntDate(channel.eLastMsgDate);
								log.finer("getCompleteChProp method: index is " + index + " setting up last message date with value " + dDate);
								
								dynamicMeasure.setValue(dDate);
								break;
							case 10:
								Double dTime = getIntTime(channel.eLastMsgDate);
								log.finer("getCompleteChProp method: index is " + index + " setting up last message date with value " + dTime);
								dynamicMeasure.setValue(dTime);
								break;
							case 11:
								log.finer("getCompleteChProp method: index is " + index + " setting up channel.eCurrentSharingConvs with value " + channel.eCurrentSharingConvs);
								dynamicMeasure.setValue(channel.eCurrentSharingConvs == Integer.MIN_VALUE ? Double.NaN : channel.eCurrentSharingConvs);
								break;
							default:
								log.finer("getCompleteChProp method: index is " + index + " is unknown");
							}		
						}
					}
				}
			}
		} catch (Exception ex) {
			PCFException pcfEx = null;
			if (ex instanceof PCFException && ((pcfEx =(PCFException)ex)).getReason() == 3200 || pcfEx.getReason() == 3065) {
				String msg = "";
				if (pcfEx.getReason() == 3200) {
					msg = "getCompleteChProp method: PCF reason code is 3200: no items found matching the creteria '" + channelNameFilter + "'.";
				} else {
					msg = "getCompleteChProp method: PCF reason code is 3065: one of the channels which matches channel filter '" + channelNameFilter + "' is not in use. If channel(s) could be not in use then ignore this message, otherwise make sure that channel filter is set correctly.";
				}
				log.info(msg);
				return new Status(StatusCode.PartialSuccess, msg, msg, ex);
			}
			log.severe("Problem=" + ex.getMessage());
			ex.printStackTrace();
			String s = getExceptionAsString(ex); 
			log.severe("getCompleteChProp method: " + s);
			disconnect();
			throw new Exception(ex.getMessage());
		}


		return status;
	}
	
	private boolean isAggregate(String key) {
		if (key.indexOf('|') > 0) {
			return false;
		}
		return true;
	}
	
	public static synchronized Double getIntDate(Date date) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getIntDate method");
		}
		if (date == null) {
			return Double.NaN;
		}
		int result = Integer.valueOf(sdfYear.format(date)) * 10000 + Integer.valueOf(sdfMonth.format(date))*100 + Integer.valueOf(sdfDay.format(date));
		log.finer("getIntDate method: integer date is " + result);
		return Double.valueOf(result);
	}
	
	public static synchronized Double getIntTime(Date date) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getIntTime method");
		}
		if (date == null) {
			return Double.NaN;
		}
		int result = Integer.valueOf(sdfHours.format(date)) * 10000 + Integer.valueOf(sdfMinutes.format(date))*100 + Integer.valueOf(sdfSeconds.format(date));
		log.finer("getIntTime method: integer time is " + result);
		return Double.valueOf(result);
	}
	
	public static String getUniqueKey(Map<String, ChannelData> channels, String key) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getUniqueKey method");
		}
		int i = 0;
		String newKey = new StringBuilder(key).append("_").append(++i).toString();
		while (channels.containsKey(newKey)) {
			newKey = new StringBuilder(key).append("_").append(++i).toString();
			log.finer("getUniqueKey method: newKey is '" + newKey + "'");
		}
		
		return newKey;
	}
	
	public static void aggregateChannelStats(Map<String, ChannelData>channels, ChannelData channel) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering aggregateChannelStats method");
		}
		
		ChannelData tempChannel;
		
		log.finer("aggregateChannelStats method: channel name is '" + channel.eChannelName + "', connection name is '" + channel.eConnName + "'");
		
		if (!channels.containsKey(channel.eChannelName)) {
			log.finer("aggregateChannelStats method: first time invoking method for the channel name '" + channel.eChannelName + "'");
			channels.put(channel.eChannelName, (tempChannel = new ChannelData()));
			tempChannel.eChannelStatus = Integer.MIN_VALUE;
			tempChannel.eChannelSubStatus = Integer.MIN_VALUE;
		} else {
			tempChannel = channels.get(channel.eChannelName);
			// increase number of connections
			int i = tempChannel.getConnections();
			tempChannel.setConnections(++i);
			log.finer("aggregateChannelStats method: subsequent time invoking method for the channel name '" + channel.eChannelName + "', connections count is " + tempChannel.getConnections());
		}
		// aggregate channel's data
		tempChannel.eChannelType = channel.eChannelType; // last channel type of the channel's connection
		tempChannel.eChannelMsgs += channel.eChannelMsgs; // aggregate # of messages
		tempChannel.eBytesSent += channel.eBytesSent;
		tempChannel.eBytesReceived += channel.eBytesReceived;
		tempChannel.eBufferSent += channel.eBufferSent;
		tempChannel.eBufferReceived += channel.eBufferReceived;
		tempChannel.eCurrentSharingConvs += channel.eCurrentSharingConvs == Integer.MIN_VALUE ? 0 : channel.eCurrentSharingConvs;
		// process last message date
		if (log.isLoggable(Level.FINER)) {
			log.finer("aggregateChannelStats method: agreegated date is '" + (tempChannel.eLastMsgDate == null ? "-" : tempChannel.eLastMsgDate) + 
					"', connection channel date is '" + (channel.eLastMsgDate == null ? "-" : channel.eLastMsgDate));
		}
		if (channel.eLastMsgDate != null) {
			if (tempChannel.eLastMsgDate == null) {
				tempChannel.eLastMsgDate = channel.eLastMsgDate;
				log.finer("aggregateChannelStats method: agreegated date changed from null to '" + tempChannel.eLastMsgDate + "'");
			} else {
				if (tempChannel.eLastMsgDate.after(channel.eLastMsgDate)) {
					tempChannel.eLastMsgDate = channel.eLastMsgDate;
					log.finer("aggregateChannelStats method: agreegated date replaced with the connection channel date '" + tempChannel.eLastMsgDate + "'");
				}
			}
		}
	}


	public Status getQueueProp(MonitorEnvironment env, String filteredqueueNameFilter) throws MQException,java.io.IOException, MQDataException {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getQueueProp method");
		}

		log.finer("getQueueProp method: before setting allQueueProperties");
		List<QueuePropWrapper> allQueueProperties = new ArrayList<QueuePropWrapper>();
		//Old call to getQueueProperties for queue filtering
		//Status status = getQueueProperties(allQueueProperties);
		Status status = getQueueProperties(allQueueProperties, filteredqueueNameFilter);
		if (allQueueProperties != null) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("getQueueProp method: allQueueProperties has size " + allQueueProperties.size());
			}
		}

/*		log.finer("Here are the queues ++++++++++++++++++++++++++++++++");
		for (QueuePropWrapper queuePropWrapper: allQueueProperties) {
			String queueName = queuePropWrapper.getQueueName();
			log.finer("Q="+queueName);
		}		
		log.finer("END OF QUEUES ++++++++++++++++++++++++++++++++++");
*/		
		if(allQueueProperties != null ){
			for (int index = 0; index < METRICS_QUEUE.length; index++) {
				for (QueuePropWrapper queuePropWrapper: allQueueProperties) {
					String queueName = queuePropWrapper.getQueueName();
//					log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "'");
					Collection<MonitorMeasure> monitorMeasures = env.getMonitorMeasures("Queue Group", METRICS_QUEUE[index]);
					for (MonitorMeasure subscribedMonitorMeasure : monitorMeasures) {
						MonitorMeasure dynamicMeasure = env.createDynamicMeasure(subscribedMonitorMeasure, "Queue Name", queueName);
						switch (index) {
						case 0:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getDepth() is " + queuePropWrapper.getDepth());
							}
							dynamicMeasure.setValue(queuePropWrapper.getDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getDepth());
							break;
						case 1:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getPriorty() is " + queuePropWrapper.getPriorty());
							}
							dynamicMeasure.setValue(queuePropWrapper.getPriorty() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getPriorty());
							break;
						case 2:
							if (log.isLoggable(Level.FINE)) {
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getDeQueueCount() is " + queuePropWrapper.getDeQueueCount());
							}
							dynamicMeasure.setValue(queuePropWrapper.getDeQueueCount() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getDeQueueCount());
							break;							
						case 3:
							if (log.isLoggable(Level.FINE)) {
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getEnQueueCount() is " + queuePropWrapper.getEnQueueCount());
							}
							dynamicMeasure.setValue(queuePropWrapper.getEnQueueCount() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getEnQueueCount());
							break;							
						case 4:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getGetAllowed() is " + queuePropWrapper.getGetAllowed());
							}
							dynamicMeasure.setValue(queuePropWrapper.getGetAllowed() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getGetAllowed());
							break;
						case 5:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getPutAllowed() is " + queuePropWrapper.getPutAllowed());
							}
							dynamicMeasure.setValue(queuePropWrapper.getPutAllowed() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getGetAllowed());
							break;
						case 6:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getMsgLength() is " + queuePropWrapper.getMsgLength());
							}
							dynamicMeasure.setValue(queuePropWrapper.getMsgLength() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getMsgLength());
							break;
						case 7:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getMaxDepth() is " + queuePropWrapper.getMaxDepth());
							}
							dynamicMeasure.setValue(queuePropWrapper.getMaxDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getMaxDepth());
							break;
						case 8:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getOpenInputCount() is " + queuePropWrapper.getOpenInputCount());
							}
							dynamicMeasure.setValue(queuePropWrapper.getOpenInputCount() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getOpenInputCount());
							break;
						case 9:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getOpenOutputCount() is " + queuePropWrapper.getOpenOutputCount());
							}
							dynamicMeasure.setValue(queuePropWrapper.getOpenOutputCount() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getOpenOutputCount());
							break;
						case 10:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getOldestMsgAge() is " + queuePropWrapper.getOldestMsgAge());
							}
							dynamicMeasure.setValue(queuePropWrapper.getOldestMsgAge() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getOldestMsgAge());
							break;
						case 11:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getUncommittedMsgs() is " + queuePropWrapper.getUncommittedMsgs());
							}
							dynamicMeasure.setValue(queuePropWrapper.getUncommittedMsgs() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getUncommittedMsgs());
							break;
						case 12:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getHighQDepth() is " + queuePropWrapper.getHighQDepth());
							}
							dynamicMeasure.setValue(queuePropWrapper.getHighQDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getHighQDepth());
							break;
						case 13:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getIntervalSinceLastGet() is " + queuePropWrapper.getIntervalSinceLastGet());
							}
							dynamicMeasure.setValue(queuePropWrapper.getIntervalSinceLastGet() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getIntervalSinceLastGet());
							break;
						case 14:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getIntervalSinceLastPut() is " + queuePropWrapper.getIntervalSinceLastPut());
							}
							dynamicMeasure.setValue(queuePropWrapper.getIntervalSinceLastPut() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getIntervalSinceLastPut());
							break;
						case 15:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getOnQTime()[0] is " + queuePropWrapper.getOnQTime()[0]);
							}
							if (queuePropWrapper.getOnQTime().length > 0 && queuePropWrapper.getOnQTime()[0] != MQConstants.MQMON_NOT_AVAILABLE ) {
								dynamicMeasure.setValue(queuePropWrapper.getOnQTime()[0] * 1000);
							} else if (queuePropWrapper.getOnQTime().length > 0){
								dynamicMeasure.setValue(queuePropWrapper.getOnQTime()[0]);
							}
							break;
						case 16:
							if (log.isLoggable(Level.FINER)) {
								log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getOnQTime()[1] is " + queuePropWrapper.getOnQTime()[1]);
							}
							if (queuePropWrapper.getOnQTime().length > 1 && queuePropWrapper.getOnQTime()[1] != MQConstants.MQMON_NOT_AVAILABLE ) {
								dynamicMeasure.setValue(queuePropWrapper.getOnQTime()[1] * 1000);
							} else if (queuePropWrapper.getOnQTime().length > 1) {
								dynamicMeasure.setValue(queuePropWrapper.getOnQTime()[1]);
							}
							break;
						// add DEQUEUE_RATE
						case 17: 
							if (log.isLoggable(Level.FINE)) {
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getDeQueueRate() is " + queuePropWrapper.getDeQueueRate());
							}
							dynamicMeasure.setValue(queuePropWrapper.getDeQueueRate());
							break;
						// add ENQUEUE_RATE
						case 18: 
							if (log.isLoggable(Level.FINE)) {
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getEnQueueRate() is " + queuePropWrapper.getEnQueueRate());
							}
							dynamicMeasure.setValue(queuePropWrapper.getEnQueueRate());
							break;
						// add PERCENTAGE_Q_DEPTH
						case 19: 
							double percent_q_depth = (queuePropWrapper.getDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getDepth()) / (queuePropWrapper.getMaxDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getMaxDepth());
							percent_q_depth = percent_q_depth * 100;
							if (log.isLoggable(Level.FINE)) {
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.getDepth() is " + queuePropWrapper.getDepth());
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for queuePropWrapper.queuePropWrapper.getMaxDepth() is " + queuePropWrapper.getMaxDepth());
								log.fine("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', value for percent_q_depth is " + percent_q_depth);
							}
							//dynamicMeasure.setValue(queuePropWrapper.getDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getDepth());
							//dynamicMeasure.setValue(queuePropWrapper.getMaxDepth() == Integer.MIN_VALUE ? Double.NaN : queuePropWrapper.getMaxDepth());
							dynamicMeasure.setValue(percent_q_depth);
							break;
						default:
							log.finer("getQueueProp method: index is " + index + ", queueName is '" + queueName + "', metric is unknown");
						}		
					}
				}
			}
		}
		
		return status;
	}
	
	public Status getQueueProperties(List<QueuePropWrapper> allQueueProperties, String filteredqueueNameFilter) throws MQException,
	IOException, MQDataException {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering getQueueProperties method");
		}
		boolean isException = false;
		String errMsg = EMPTY_STRING;
		Status status = new Status(StatusCode.Success);
	
		HashMap<String, QueuePropWrapper> qObjectMap = new HashMap<String, QueuePropWrapper>(); 
		PCFMessage pcfCmd = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q_STATUS);
		pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, filteredqueueNameFilter);
		pcfCmd.addParameter(MQConstants.MQIACF_Q_STATUS_TYPE, MQConstants.MQIACF_Q_STATUS);
		
		//we should restrict to QLOCAL
//		pcfCmd.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL); there is no such request parameter 
		
		PCFMessage pcfResetCmd = new PCFMessage(MQConstants.MQCMD_RESET_Q_STATS);
		pcfResetCmd.addParameter(MQConstants.MQCA_Q_NAME, filteredqueueNameFilter);

		PCFMessage pcfInqCmd = new PCFMessage(MQConstants.MQCMD_INQUIRE_Q);
		pcfInqCmd.addParameter(MQConstants.MQCA_Q_NAME, filteredqueueNameFilter);
		pcfInqCmd.addParameter(MQConstants.MQIA_Q_TYPE, MQConstants.MQQT_LOCAL );
		
		PCFMessage[] pcfResetResponse;
		PCFMessage[] pcfResponse;
		PCFMessage[] pcfInqResponse;

		String currentQName = ""; //This is to trace on which queue an exception occurred
		

			int i = 0;
			try {
			// pcfCmd command is MQConstants.MQCMD_INQUIRE_Q_STATUS
			pcfResponse = msgAgent.send(pcfCmd);
			log.finer("getQueueProperties method: pcfCmd is MQConstants.MQCMD_INQUIRE_Q_STATUS, responses = " + pcfResponse.length);
			for (PCFMessage response : pcfResponse) {				
				// check if response should be skipped
				if (getEnumSize(response.getParameters()) <= 2) {
					// skip response
					log.warning("getQueueProperties method: pcfCmd command: skip response. Response object is " + response.toString());
					Enumeration<?> e = response.getParameters();
					int j = 0;
					while (e.hasMoreElements()) {
						Object obj = e.nextElement();
						log.finer("getQueueProperties method: pcfCmd command: element "
								+ (j++) + ", class is '"
								+ obj.getClass().getCanonicalName()
								+ "', String is '" + obj.toString()
								+ "'");
					}
					continue;
				}
				
				if (log.isLoggable(Level.FINER)) {
					log.finer("getQueueProperties method: pcfCmd command: QName of the response is '" + response.getStringParameterValue(MQConstants.MQCA_Q_NAME) + "', response # " + (i++)
							+ ", reasone code " + response.getReason()
							+ ", parameters count "
							+ response.getParameterCount());
				}
				
				QueuePropWrapper q  = new QueuePropWrapper();
				currentQName = response.getStringParameterValue(MQConstants.MQCA_Q_NAME);
				// Added in the .trim()
				currentQName = currentQName.trim();
				q.setQueueName(currentQName);
				if (currentQName.startsWith("SYSTEM") && ignoreSystem == true){
					//do nothing
					log.finer("getQueueProperties method:  pcfCmd command: skip response with the queueName '" + currentQName + "' because ignoreSystem is " + ignoreSystem);
				}else{
					try {
						q.setDepth(response.getIntParameterValue(MQConstants.MQIA_CURRENT_Q_DEPTH));
					} catch (Exception e) {
						q.setDepth(-1);
						errMsg = "getQueueProperties method: get CURRENT_Q_DEPTH for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setOpenInputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_INPUT_COUNT));
					} catch(Exception e) {
						q.setOpenInputCount(-1);
						errMsg = "getQueueProperties method: get OPEN_INPUT_COUNT for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setOpenOutputCount(response.getIntParameterValue(MQConstants.MQIA_OPEN_OUTPUT_COUNT));
					} catch(Exception e) {
						q.setOpenOutputCount(-1);
						errMsg = "getQueueProperties method: get OPEN_OUTPUT_COUNT for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setLastGetDate(response.getStringParameterValue(MQConstants.MQCACF_LAST_GET_DATE));
					} catch (Exception e) {
						q.setLastGetDate(EMPTY_STRING);
						errMsg = "getQueueProperties method: get LAST_GET_DATE for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setLastGetTime(response.getStringParameterValue(MQConstants.MQCACF_LAST_GET_TIME));
					} catch (Exception e) {
						q.setLastGetTime(EMPTY_STRING);
						errMsg = "getQueueProperties method: get LAST_GET_TIME for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setLastPutDate(response.getStringParameterValue(MQConstants.MQCACF_LAST_PUT_DATE));
					} catch (Exception e) {
						q.setLastPutDate(EMPTY_STRING);
						errMsg = "getQueueProperties method: get LAST_PUT_DATE for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setLastPutTime(response.getStringParameterValue(MQConstants.MQCACF_LAST_PUT_TIME)); // fixed from MQCACF_LAST_GET_TIME to MQCACF_LAST_PUT_TIME
					} catch (Exception e) {
						q.setLastPutTime(EMPTY_STRING);
						errMsg = "getQueueProperties method: get LAST_PUT_TIME for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setOldestMsgAge(response.getIntParameterValue(MQConstants.MQIACF_OLDEST_MSG_AGE));
					} catch (Exception e) {
						q.setOldestMsgAge(-1);
						errMsg = "getQueueProperties method: get OLDEST_MSG_AGE for queue '" + q.getQueueName() + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setUncommittedMsgs(response.getIntParameterValue(MQConstants.MQIACF_UNCOMMITTED_MSGS));
					} catch (Exception e) {
						q.setUncommittedMsgs(-1);
						errMsg = "getQueueProperties method: get UNCOMMITTED_MSGS for queue '" + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					try {
						q.setOnQTime(response.getIntListParameterValue(MQConstants.MQIACF_Q_TIME_INDICATOR));
					} catch (Exception e) {
						int[] aint = new int[]{-1};
						q.setOnQTime(aint);
						errMsg = "getQueueProperties method: get Q_TIME_INDICATOR for queue '" + "' threw exception '" + getExceptionAsString(e) + "'";
						log.severe(errMsg);
						isException = true;
					}
					if (log.isLoggable(Level.WARNING) && debuggingQueuesMap.containsKey(q.getQueueName())) {
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIA_CURRENT_Q_DEPTH is '" + q.getDepth() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIA_OPEN_INPUT_COUNT is '" + q.getOpenInputCount() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIA_OPEN_OUTPUT_COUNT is '" + q.getOpenOutputCount() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQCACF_LAST_GET_DATE is '" + q.getLastGetDate() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQCACF_LAST_GET_TIME is '" + q.getLastGetTime() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQCACF_LAST_PUT_DATE is '" + q.getLastPutDate() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQCACF_LAST_PUT_TIME is '" + q.getLastPutTime() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIACF_OLDEST_MSG_AGE is '" + q.getOldestMsgAge() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIACF_UNCOMMITTED_MSGS is '" + q.getUncommittedMsgs() + "'");
						log.warning("getQueueProperties method: Queue Name is '" + currentQName + "', MQIACF_Q_TIME_INDICATOR is '" + Arrays.toString(q.getOnQTime()) + "'");
					}
				
					qObjectMap.put(q.getQueueName(), q);
				}
			}
			} catch (PCFException e) {
				String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			} catch (MQDataException e) {
				String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			} catch (IOException e) {
				String msg = "getQueueProperties method: " + getExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			}
			if (indResetCmd) {
				try {
					// pcfResetCmd command is MQConstants.MQCMD_RESET_Q_STATS
	//				long currentTimestamp = System.currentTimeMillis();
					pcfResetResponse = msgAgent.send(pcfResetCmd);
					if (log.isLoggable(Level.FINER)) {
						log.finer("getQueueProperties method: pcfResetCmd command is MQConstants.MQCMD_RESET_Q_STATS, responses = "
								+ pcfResetResponse.length);
					}
					i = 0;
					for (PCFMessage resetResponse : pcfResetResponse) {
						if (resetResponse.getParameterCount() <= 2) {
							continue;
						}
						
						if (log.isLoggable(Level.FINER)) {
							log.finer("getQueueProperties method: pcfResetCmd command: QName of the resetResponse is '" + resetResponse.getStringParameterValue(MQConstants.MQCA_Q_NAME) + "', response # " + (i++)
									+ ", reasone code " + resetResponse.getReason()
									+ ", parameters count "
									+ resetResponse.getParameterCount());
						}
						
						String qName = resetResponse.getStringParameterValue(MQConstants.MQCA_Q_NAME);
						// Added in the .trim()
						qName = qName.trim();
						if (qName.startsWith("SYSTEM") && ignoreSystem == true) {
							// do nothing
							log.finer("getQueueProperties method:  pcfResetCmd command: skip response for the pcfResetCmd with the queueName '" + qName + "' because ignoreSystem is " + ignoreSystem);
						} else {
							QueuePropWrapper wrapper;
							if ((wrapper = qObjectMap.get(qName)) != null) {
								wrapper.setHighQDepth(resetResponse.getIntParameterValue(MQConstants.MQIA_HIGH_Q_DEPTH)); //<== check if get returns null
								int timeSinceLastReset = resetResponse.getIntParameterValue(MQConstants.MQIA_TIME_SINCE_RESET);
								int valueCurrent;
								wrapper.setEnQueueCount(valueCurrent = resetResponse.getIntParameterValue(MQConstants.MQIA_MSG_ENQ_COUNT));
								// add calculation of the EnqueueRate
								wrapper.setEnQueueRate(getRate(valueCurrent, rate, timeSinceLastReset));
								wrapper.setDeQueueCount(valueCurrent = resetResponse.getIntParameterValue(MQConstants.MQIA_MSG_DEQ_COUNT));
								// add calculation of the DequeueRate
								wrapper.setDeQueueRate(getRate(valueCurrent, rate, timeSinceLastReset));
								if (log.isLoggable(Level.FINE)) {
									log.fine("getQueueProperties method: pcfResetCmd command: QName of the resetResponse is '" + qName 
											+ "', High Queue depth is '" + wrapper.getHighQDepth() 
											+ "', EnQueue count is '" + wrapper.getEnQueueCount()
											+ "', DeQueue count is '" + wrapper.getDeQueueCount()
											+ "'");
								}
							}
						}
					}
	//				rateTimestamp = currentTimestamp;
				} catch (PCFException e) {
					String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
					log.severe(msg);
					status = new Status(StatusCode.PartialSuccess, msg, msg);
				} catch (MQDataException e) {
					String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
					log.severe(msg);
					status = new Status(StatusCode.PartialSuccess, msg, msg);
				} catch (IOException e) {
					String msg = "getQueueProperties method: " + getExceptionAsString(e);
					log.severe(msg);
					status = new Status(StatusCode.PartialSuccess, msg, msg);
				}
			}
			try {
			// pcfInqCmd command is MQConstants.MQCMD_INQUIRE_Q_STATUS
			pcfInqResponse = msgAgent.send(pcfInqCmd);
			log.finer("getQueueProperties method: pcfInqCmd command is MQConstants.MQCMD_INQUIRE_Q_STATUS, responses = "
					+ pcfInqResponse.length);
			i = 0;
				for (PCFMessage inqResponse : pcfInqResponse) {
					// check if response should be skipped
					if (getEnumSize(inqResponse.getParameters()) <= 2) {
						// skip response
						log.warning("getQueueProperties method: pcfInqCmd command: skip response. Response object is " + inqResponse.toString());
						Enumeration<?> e = inqResponse.getParameters();
						int j = 0;
						while (e.hasMoreElements()) {
							Object obj = e.nextElement();
							log.finer("getQueueProperties method: pcfInqCmd command: element "
									+ (j++) + ", class is '"
									+ obj.getClass().getCanonicalName()
									+ "', String is '" + obj.toString()
									+ "'");
						}
						
						continue;
					}
					
					if (log.isLoggable(Level.FINER)) {
						log.finer("getQueueProperties method: pcfInqCmd command: QName of the resetResponse is '" + inqResponse.getStringParameterValue(MQConstants.MQCA_Q_NAME) + "', response # " + (i++)
								+ ", reason code " + inqResponse.getReason()
								+ ", parameters count "
								+ inqResponse.getParameterCount());
					}
					String qName;
					try {
						qName = inqResponse.getStringParameterValue(MQConstants.MQCA_Q_NAME);
						// Added in the .trim()
						qName = qName.trim();
					} catch (Exception e) {
						log.severe("getQueueProperties method:  pcfInqCmd command: Could not get MQCA_Q_NAME");
						continue;
					}
					if (qName.startsWith("SYSTEM") && ignoreSystem == true){
						//do nothing
						log.finer("getQueueProperties method:  pcfInqCmd command: skip response with the queueName '" + qName + "' because ignoreSystem is " + ignoreSystem);
					}else{
						try{// somehow, this fails sometimes
							qObjectMap.get(qName).setInhibitGet(inqResponse.getIntParameterValue(MQConstants.MQIA_INHIBIT_GET));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get INHIBIT_GET info for : " +  qName);
							isException = true;
						}
						try{
							qObjectMap.get(qName).setInhibitPut(inqResponse.getIntParameterValue(MQConstants.MQIA_INHIBIT_PUT));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get INHIBIT_PUT info for : " +  qName);
							isException = true;
						}
						try {
							qObjectMap.get(qName).setDefinitionType(inqResponse.getIntParameterValue(MQConstants.MQIA_DEFINITION_TYPE));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get MQIA_DEFINITION_TYPE info for : " +  qName);
							isException = true;
						}
						try {
							qObjectMap.get(qName).setMaxDepth(inqResponse.getIntParameterValue(MQConstants.MQIA_MAX_Q_DEPTH));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get MQIA_MAX_Q_DEPTH info for : " +  qName);
							isException = true;
						}
						try {
							qObjectMap.get(qName).setMsgLength(inqResponse.getIntParameterValue(MQConstants.MQIA_MAX_MSG_LENGTH));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get MQIA_MAX_MSG_LENGTH info for : " +  qName);
							isException = true;
						}
						try {
							qObjectMap.get(qName).setPriorty(inqResponse.getIntParameterValue(MQConstants.MQIA_DEF_PRIORITY));
						} catch (Exception e) {
							log.severe(errMsg = "getQueueProperties method:  pcfInqCmd command: Could not get MQIA_DEF_PRIORITY info for : " +  qName);
							isException = true;
						}
						
						if (log.isLoggable(Level.FINER) && !isException) {
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_INHIBIT_GET is '" + qObjectMap.get(qName).getInhibitGet() + "'");
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_INHIBIT_PUT is '" + qObjectMap.get(qName).getInhibitPut() + "'");
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_DEFINITION_TYPE is '" + qObjectMap.get(qName).getDefinitionType() + "'");
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_MAX_Q_DEPTH is '" + qObjectMap.get(qName).getMaxDepth() + "'");
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_MAX_MSG_LENGTH is '" + qObjectMap.get(qName).getMsgLength() + "'");
							log.finer("getQueueProperties method:  pcfInqCmd command: Queue Name is '" + qName + "', MQIA_DEF_PRIORITY is '" + qObjectMap.get(qName).getPriorty() + "'");
						}
					}
				}
			} catch (PCFException e) {
				disconnect();
				String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			} catch (MQDataException e) {
				disconnect();
				String msg = "getQueueProperties method: " + getMQDataExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			} catch (IOException e) {
				disconnect();
				String msg = "getQueueProperties method: " + getExceptionAsString(e);
				log.severe(msg);
				status = new Status(StatusCode.PartialSuccess, msg, msg);
			}		

//		ArrayList<QueuePropWrapper> allQueueProperties = new ArrayList<QueuePropWrapper>();
		
		allQueueProperties.addAll(qObjectMap.values());
		
		if (!isException) {
			return status;
		} else {
			return new Status(StatusCode.PartialSuccess, errMsg, errMsg);
		}
	}
	
	public static double getRate(/*long rateTimestamp, long currentTimestamp,*/ int valueCurrent, Rate rate, int timeSinceLastReset) {
		if (log.isLoggable(Level.FINE)) {
			log.fine("Entering getRate method");
		}
		double d = timeSinceLastReset != 0 ? valueCurrent * getRateMultiplier(rate)/ timeSinceLastReset : Double.NaN; 
		if (log.isLoggable(Level.FINE)){
			log.fine("getRate method: rate: time since last reset is " + timeSinceLastReset 
					+ ", valueCurrent is " + valueCurrent 
					+ ", rate is " + d
					);
		}
		
		return d;
	}
	
	public static double getRateMultiplier(Rate rate) {
		if (log.isLoggable(Level.FINE)) {
			log.fine("Entering getRateMultiplier method: rate = '" + rate.name() + "'");
		}

		double multiplier;
		switch (rate) {
		case per_second:
			multiplier = 1.;
			break;
		case per_minute:
			multiplier = 60.;
			break;
		case per_hour:
			multiplier = 360.;
			break;
		default:
			multiplier = 1.;
			break;
		}
		
		if (log.isLoggable(Level.FINE)) {
			log.fine("getRateMultiplier method: rate = '" + rate.name() + "', multiplier is " + multiplier);
		}
		return multiplier;
	}
	
	public static int getEnumSize(Enumeration<?> e) {
		int i = 0;
		while (e.hasMoreElements()) {
			i++;
			e.nextElement();
		}
		
		return i;
	}

	/** disconnect PCF Agent
	 */
	public void disconnect( ) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Entering disconnect method");
		}
		try {
			if (msgAgent != null) {
				msgAgent.disconnect();
			}
			
			if (qMgr != null) {
				qMgr.disconnect();
			}
		} catch (MQDataException e) {
			// do nothing here
		} catch (MQException e) {
			// do nothing here
		} finally {
			qMgr = null;
			msgAgent = null;
		}
		
	}
	
	/**
	 * Shuts the Plugin down and frees resources. This method is called in the
	 * following cases:
	 * <ul>
	 * <li>the <tt>setup</tt> method failed</li>
	 * <li>the Plugin configuration has changed</li>
	 * <li>the execution duration of the Plugin exceeded the schedule timeout</li>
	 * <li>the schedule associated with this Plugin was removed</li>
	 * </ul>
	 *
	 * <p>
	 * The Plugin methods <tt>setup</tt>, <tt>execute</tt> and
	 * <tt>teardown</tt> are called on different threads, but they are called
	 * sequentially. This means that the execution of these methods does not
	 * overlap, they are executed one after the other.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 * <li><tt>setup</tt> (failed) -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, configuration changes, <tt>execute</tt>
	 * ends -&gt; <tt>teardown</tt><br>
	 * on next schedule interval: <tt>setup</tt> -&gt; <tt>execute</tt> ...</li>
	 * <li><tt>execute</tt> starts, execution duration timeout,
	 * <tt>execute</tt> stops -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, <tt>execute</tt> ends, schedule is
	 * removed -&gt; <tt>teardown</tt></li>
	 * </ul>
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 *
	 * <p>
	 * All by the Plugin allocated resources should be freed in this method.
	 * Examples are opened sockets or files.
	 *
	 * @see Monitor#setup(MonitorEnvironment)
	 */	
	public void teardown(MonitorEnvironment env) throws Exception {
		try {
			/*
			 * try{ pcfMgr.closeConnection(); }catch(Exception ex){ }
			 */
			try {
				disconnect();
			} catch (Exception ex) {

			}
			// queueConnection.close();
			// stopConnect();
		} catch (Exception ex) {

			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw);
			ex.printStackTrace(pw);
			// return new
			// Status(Status.StatusCode.ErrorInternalException,sw.toString());

		}
	}
	 
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MQQueueChannelMonitorUpdated mqChannelMonitor = new MQQueueChannelMonitorUpdated();
		Map<String, Object> env_ = new HashMap<String, Object>();
		
		// queue name
		env_.put(PARAM_QMNAME, "NMQM");
//		env_.put(PARAM_QMNAME, "MB8QMGR");
		
		// host
		env_.put(PARAM_HOST, new HostImpl("cw06.compuware.com"));
//		env_.put(PARAM_HOST, new HostImpl("172.23.33.176"));
		
		// port
		env_.put(PARAM_PORT, "1410");
//		env_.put(PARAM_PORT, "2414");
		
		// channel name
		env_.put(PARAM_CHANNEL, "SYSTEM.ADMIN.SVRCONN");
//		env_.put(PARAM_CHANNEL, "SYSTEM.ADMIN.SVRCONN");
		
		// collect queue information
		env_.put(PARAM_COLLECT_QUEUE_INFORMATION,  true);
		
		// Get Collect Channel Info
		env_.put(PARAM_COLLECT_CHANNEL_INFORMATION, true);
		
		// queue filter
		env_.put(PARAM_QUEUE_NAME_FILTER, "A*");
		
		// channel filter
		env_.put(PARAM_CHANNEL_NAME_FILTER, "");
		
		// use SSL
		env_.put(PARAM_USE_SSL_CONNECTION, false);
		
		// authenticate
		env_.put(PARAM_AUTHENTICATE, false);
//		env_.put(PARAM_AUTHENTICATE, true);
		
		// user
		env_.put(PARAM_USER, "");
//		env_.put(PARAM_USER, "MUSR_MQADMIN");
		
		// password
		env_.put(PARAM_PASSWORD, "");
//		env_.put(PARAM_PASSWORD, "!CPWR?adlex");
		
		// ignoreSystem
		env_.put(PARAM_SYSTEM_OBJECT_FILTER, false);
		
		EnvEmulator env = new EnvEmulator();
		env.setEnvInternal(env_);
		
		try {
			mqChannelMonitor.setup((MonitorEnvironment)env);
			mqChannelMonitor.execute((MonitorEnvironment)env);
		} catch (Exception e) {
			e.printStackTrace();
		}
		

	}
}
