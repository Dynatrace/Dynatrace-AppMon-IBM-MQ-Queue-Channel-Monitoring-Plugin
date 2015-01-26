package com.dynatrace.plugin.util;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;

public class EnvEmulator implements MonitorEnvironment {
	
	Map<String, Object> envInternal;

	public Map<String, Object> getEnvInternal() {
		return envInternal;
	}

	public void setEnvInternal(Map<String, Object> envInternal) {
		this.envInternal = envInternal;
	}
	
	public String getConfigString(String key) {
		return (String)envInternal.get(key);
	}
	
	public Boolean getConfigBoolean(String key) {
		return (Boolean)envInternal.get(key);
	}
	
	public String getConfigPassword(String key) {
		return (String) envInternal.get(key);
	}
	
	public com.dynatrace.diagnostics.pdk.PluginEnvironment.Host getHost() {
		return (Host)envInternal.get("Host");
	}
	
	@Override
	public Date getConfigDate(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Double getConfigDouble(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	@Deprecated
	public File getConfigFile(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long getConfigLong(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URL getConfigUrl(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public MonitorMeasure createDynamicMeasure(MonitorMeasure arg0,
			String arg1, String arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<MonitorMeasure> getMonitorMeasures() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<MonitorMeasure> getMonitorMeasures(String arg0,
			String arg1) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
}
