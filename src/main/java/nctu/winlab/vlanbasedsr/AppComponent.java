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
//start
import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.DeviceId;


import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.net.host.HostService;

import org.onosproject.net.topology.TopologyService;

import java.util.Set;
import java.util.HashMap;
import java.util.*;
//end

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sample Network Configuration Service Application */
@Component(immediate = true)
public class AppComponent {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final NameConfigListener cfgListener = new NameConfigListener();
  private final ConfigFactory factory =
      new ConfigFactory<ApplicationId, NameConfig>(
          APP_SUBJECT_FACTORY, NameConfig.class, "VlanBasedSRConfig") {
        @Override
        public NameConfig createConfig() {
          return new NameConfig();
        }
      };

	private ApplicationId appId;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected NetworkConfigRegistry cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    private VlanRoutingProcessor processor = new VlanRoutingProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;


	//to record the information of edge switch and subnet and device id
	Map<IpPrefix, DeviceId> edgeIPTable = new HashMap();
	Map<DeviceId, Integer> deviceVlanTable = new HashMap();
	Map<Integer, MacAddress> hostMacTable = new HashMap();
	Map<Integer, ConnectPoint> hostConnectPointTable = new HashMap();

  @Activate
	protected void activate() {
		appId = coreService.registerApplication("nctu.winlab.vlanbasedsr");
		cfgService.addListener(cfgListener);
		cfgService.registerConfigFactory(factory);
		packetService.addProcessor(processor,PacketProcessor.director(2));
//		tempSetConfig();
//		configFlowInstall();
		log.info("Started");
/*		
	//dhcp diiscoverr->ppacketiin
		TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
			.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPProtocol(IPv4.PROTOCOL_UDP)
			.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
			.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
		packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
*/

  	}
	private void tempSetConfig(){
		edgeIPTable = new HashMap();
		edgeIPTable.put(IpPrefix.valueOf("10.0.2.0/24"),DeviceId.deviceId("of:0000000000000002"));
		edgeIPTable.put(IpPrefix.valueOf("10.0.3.0/24"),DeviceId.deviceId("of:0000000000000003"));
		deviceVlanTable = new HashMap();
		deviceVlanTable.put(DeviceId.deviceId("of:0000000000000001"),102);
		deviceVlanTable.put(DeviceId.deviceId("of:0000000000000002"),101);
		deviceVlanTable.put(DeviceId.deviceId("of:0000000000000003"),103);


	}

	private void configFlowInstall(){
		//consider each subnet
		for(IpPrefix sourceSubnet: edgeIPTable.keySet()){
			//find the device of subnet
			DeviceId sourceDeviceId = edgeIPTable.get(sourceSubnet);
			VlanId sourceVlanId = VlanId.vlanId(deviceVlanTable.get(sourceDeviceId).shortValue());

			//to find another device to source device
			for(DeviceId otherDeviceId:deviceVlanTable.keySet()){
				if(sourceDeviceId.equals(otherDeviceId)){
					continue;
				}
				//try to find the Path from another device to source device
				Set<Path> paths;
				paths = topologyService.getPaths(topologyService.currentTopology(),
							otherDeviceId,
							sourceDeviceId);
				//set rule on ontherDevice that output port with vlan id
				for(Path path:paths){
					installVlanForwardRule(sourceVlanId,otherDeviceId,path.src().port());
					break;
					
				}

			}
			//when the edge device is source,install the rule and push the vlanid of destination subnet
			//consider another subnet that want to send packet to this subnet
			for(IpPrefix otherSubnet: edgeIPTable.keySet()){
				if(sourceSubnet.equals(otherSubnet)){
					continue;
				}
				DeviceId otherSubnetDeviceId = edgeIPTable.get(otherSubnet);
				//try to find the Path from another device to source device
				Set<Path> paths;
				paths = topologyService.getPaths(topologyService.currentTopology(),
							otherSubnetDeviceId,
							sourceDeviceId);
				//set rule on ontherDevice that output port with vlan id
				for(Path path:paths){
					installVlanPushRule(sourceSubnet,sourceVlanId,otherSubnetDeviceId,path.src().port());
					break;
					
				}
	
			}

		}
		
	}


	private void removePacketRule(){
		//dhcp diiscoverr->ppacketiin
		TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
			.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPProtocol(IPv4.PROTOCOL_UDP)
			.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
			.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
		packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
	//dhcp offer->ppacketin
		selector =  DefaultTrafficSelector.builder()
			.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPProtocol(IPv4.PROTOCOL_UDP)
			.matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
			.matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
		packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);

	
	}
  @Deactivate
  protected void deactivate() {
    cfgService.removeListener(cfgListener);
	packetService.removeProcessor(processor);
	processor = null;
    cfgService.unregisterConfigFactory(factory);
//	removePacketRule();
    log.info("Stopped");
  }

  private class NameConfigListener implements NetworkConfigListener {
    @Override
    public void event(NetworkConfigEvent event) {
      if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
          && event.configClass().equals(NameConfig.class)) {
        NameConfig config = cfgService.getConfig(appId, NameConfig.class);
        if (config != null) {
			//log.info("get something {}",config.name());
			log.info("get vlan config");
			edgeIPTable = config.getSubnetDevice();
			deviceVlanTable = config.getDeviceVlan();
			hostMacTable = config.getHostMac();
			hostConnectPointTable = config.getHostCP();	
			configFlowInstall();
			installConfigHost();
        }
		else{
			log.info("can't get vlan config");
		}
      }
    }
  }
	private class VlanRoutingProcessor implements PacketProcessor{
		@Override
		public void process(PacketContext context){
			if(context.isHandled()){
				return;
			}
/*
			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();
			MacAddress srcMac = ethPkt.getSourceMAC();
			log.info("{}",srcMac);
			Set<Host> srcHosts = hostService.getHostsByMac(srcMac);
	
			if(srcHosts.size() == 0){
				log.info("cant find srchost");
				return;
			}

			Host srcHost = srcHosts.iterator().next();
			log.info("{} {} {}",srcHost,srcHost.location().deviceId(),srcHost.location().port());
			DeviceId srcDeviceId = srcHost.location().deviceId();
			PortNumber srcPort = srcHost.location().port();
			installSelfSameDeviceRule(srcMac,srcDeviceId,srcPort);
			VlanId srcVlanid = VlanId.vlanId(deviceVlanTable.get(srcDeviceId).shortValue());
			installSelfVlanPopRule(srcVlanid,srcMac,srcDeviceId,srcPort);
*/			

		}
	}
	private void installConfigHost(){
		for(Integer i:hostConnectPointTable.keySet()){
			MacAddress srcMac = hostMacTable.get(i);
			ConnectPoint srcCP = hostConnectPointTable.get(i);
			log.info("{}",srcMac);

			log.info("{} {} {}",srcMac,srcCP.deviceId(),srcCP.port());
			DeviceId srcDeviceId = srcCP.deviceId();
			PortNumber srcPort = srcCP.port();
			installSelfSameDeviceRule(srcMac,srcDeviceId,srcPort);
			VlanId srcVlanid = VlanId.vlanId(deviceVlanTable.get(srcDeviceId).shortValue());
			installSelfVlanPopRule(srcVlanid,srcMac,srcDeviceId,srcPort);

		}
	}
	private void handler(PacketContext context){
			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();
			MacAddress srcMac = ethPkt.getSourceMAC();
			MacAddress dstMac = ethPkt.getDestinationMAC();
//			MacAddress dstMac = MacAddress.valueOf("ea:e9:78:fb:fd:05");
			log.info("{} {}",srcMac,dstMac);
			Set<Host> dstHosts = hostService.getHostsByMac(dstMac);
			Set<Host> srcHosts = hostService.getHostsByMac(srcMac);
	
			if(dstHosts.size() == 0 || srcHosts.size() == 0){
				log.info("cant find dsthost");
				return;
			}

			Host dstHost = dstHosts.iterator().next();
			Host srcHost = srcHosts.iterator().next();
			log.info("{} {} {}",dstMac,dstHost.location().deviceId(),dstHost.location().port());
			DeviceId dstDeviceId = dstHost.location().deviceId();
			PortNumber dstPort = dstHost.location().port();
			if(srcHost.location().deviceId().equals(dstDeviceId)){
				installSameDeviceRule(srcMac,dstMac,dstDeviceId,dstPort);
//				installSameDeviceRule(dstMac,srcMac,srcHost.location().deviceId(),srcHost.location().port());
			}
			else{
				VlanId dstVlanid = VlanId.vlanId(deviceVlanTable.get(dstDeviceId).shortValue());
				installVlanPopRule(dstVlanid,dstMac,dstDeviceId,dstPort);
			}

	}

	
	private void installVlanForwardRule(VlanId vid,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchVlanId(vid);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);
	}

	private void installVlanPushRule(IpPrefix subnet,VlanId vid,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPDst(subnet);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.pushVlan()
			.setVlanId(vid)
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);

	}

	private void installVlanPopRule(VlanId vid,MacAddress dstMac,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchVlanId(vid)
			.matchEthDst(dstMac);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.popVlan()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);


	}

	private void installSelfVlanPopRule(VlanId vid,MacAddress dstMac,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchVlanId(vid)
			.matchEthDst(dstMac);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.popVlan()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);


	}

	private void installSameDeviceRule(MacAddress srcMac,MacAddress dstMac,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthSrc(srcMac)
			.matchEthDst(dstMac);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);


	}

	private void installSelfSameDeviceRule(MacAddress dstMac,DeviceId deviceId,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthDst(dstMac);
		//use  to create action
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(39999)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(2000)
			.add();
		flowObjectiveService.forward(deviceId,forwardingObjective);


	}

	private void installRule(PacketContext context,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		InboundPacket pkt = context.inPacket();
		Ethernet ethPkt  = pkt.parsed();

		if(ethPkt.isBroadcast()){
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
				.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
				.matchEthSrc(ethPkt.getSourceMAC());
		}
		else{
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchEthDst(ethPkt.getDestinationMAC())
				.matchEthSrc(ethPkt.getSourceMAC())
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

		}
		//use  to create action
		TrafficTreatment treatmentBuild = DefaultTrafficTreatment.builder()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuild)
			.withPriority(50000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(200)
			.add();
		flowObjectiveService.forward(pkt.receivedFrom().deviceId(),forwardingObjective);
		//packetout
		context.treatmentBuilder().setOutput(portNumber);
		context.send();

	}
}
