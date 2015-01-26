package com.dynatrace.plugin.domain;

import com.dynatrace.diagnostics.pdk.PluginEnvironment.Host;

public class HostImpl implements Host{
	
	private String address;
	
	public HostImpl(String address)	{
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

}
