/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.vlanbasedsr;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.basics.BasicElementConfig;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ConnectPoint;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpPrefix;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
public class NameConfig extends Config<ApplicationId> {
	public static final String NAME = "name";
	public static final String DV = "DeviceVlan";
	public static final String SD = "SubnetDevice";
	public static final String HC = "HostCP";
	public static final String HM = "HostMac";

	@Override
 	public boolean isValid() {
  		return hasFields(NAME,DV,SD,HC,HM);
	}
	
	public String name(){
		return get("NAME",null);
	}
	public Map<DeviceId, Integer> getDeviceVlan(){
		Map<DeviceId, Integer> deviceVlan = new HashMap();
		
		JsonNode findNode = node().get("DeviceVlan");
		Iterator<String> nodeIter = findNode.fieldNames();
		while( nodeIter.hasNext() ){
			String nodeKey = nodeIter.next();
			DeviceId deviceId = DeviceId.deviceId(nodeKey);
			int vid = Integer.valueOf(findNode.get(nodeKey).asText("-1"));
			deviceVlan.put(deviceId,vid);
		}
		return deviceVlan;
	}
	public Map<IpPrefix, DeviceId> getSubnetDevice(){
		Map<IpPrefix, DeviceId> subnetDevice = new HashMap();
		
		JsonNode findNode = node().get("SubnetDevice");
		Iterator<String> nodeIter = findNode.fieldNames();
		while( nodeIter.hasNext() ){
			String nodeKey = nodeIter.next();
			IpPrefix subnet = IpPrefix.valueOf(nodeKey);
			DeviceId deviceId = DeviceId.deviceId(findNode.get(nodeKey).asText(""));
			subnetDevice.put(subnet,deviceId);
		}
		return subnetDevice;

	}
	public Map<Integer, ConnectPoint> getHostCP(){
		Map<Integer, ConnectPoint> hostCP = new HashMap();
		
		JsonNode findNode = node().get("HostCP");
		Iterator<String> nodeIter = findNode.fieldNames();
		while( nodeIter.hasNext() ){
			String nodeKey = nodeIter.next();
			int hostNum = Integer.valueOf(nodeKey);
			ConnectPoint cp = ConnectPoint.deviceConnectPoint(findNode.get(nodeKey).asText(""));
			hostCP.put(hostNum,cp);
		}
		return hostCP;

	}
	public Map<Integer, MacAddress> getHostMac(){
		Map<Integer, MacAddress> hostMac = new HashMap();
		
		JsonNode findNode = node().get("HostMac");
		Iterator<String> nodeIter = findNode.fieldNames();
		while( nodeIter.hasNext() ){
			String nodeKey = nodeIter.next();
			int hostNum = Integer.valueOf(nodeKey);
			MacAddress mac = MacAddress.valueOf(findNode.get(nodeKey).asText(""));
			hostMac.put(hostNum,mac);
		}
		return hostMac;

	}
}
