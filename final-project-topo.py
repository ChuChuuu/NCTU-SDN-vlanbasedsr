# 2-by-2 leaf-spine topology
import os
import sys

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.node import RemoteController, Host, OVSSwitch
from mininet.link import TCLink
from mininet.log import setLogLevel, info, warn

class MyTopo(Topo):

    spineswitch = []
    leafswitch = []
    host = []

    def __init__(self):

        # initialize topology
        Topo.__init__(self)

        for i in range(1, 4):
            # add spine switches
            self.spineswitch.append(self.addSwitch("spine"+str(i), dpid="000000000000000"+str(i+3)))

            # add leaf switches
            self.leafswitch.append(self.addSwitch("leaf"+str(i), dpid="000000000000000"+str(i)))

        # add hosts
        # for i in range(1, 5):
        #     self.host.append(self.addHost("300"+str(i), mac="00:00:00:00:00:0"+str(i)))
        self.host.append(self.addHost("h1", cls=IpHost, mac="00:00:00:00:00:21", ip="10.0.2.1/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h2", cls=IpHost, mac="00:00:00:00:00:22", ip="10.0.2.2/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h3", cls=IpHost, mac="00:00:00:00:00:23", ip="10.0.2.3/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h4", cls=IpHost, mac="00:00:00:00:00:31", ip="10.0.3.1/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h5", cls=IpHost, mac="00:00:00:00:00:32", ip="10.0.3.2/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h6", cls=IpHost, mac="00:00:00:00:00:41", ip="10.0.4.1/16", gateway="10.0.225.225"))
        self.host.append(self.addHost("h7", cls=IpHost, mac="00:00:00:00:00:42", ip="10.0.4.2/16", gateway="10.0.225.225"))

        # add links
        for i in range(3):
          self.addLink(self.spineswitch[i], self.leafswitch[0])
          self.addLink(self.spineswitch[i], self.leafswitch[1])
          self.addLink(self.spineswitch[i], self.leafswitch[2])

        #for i in range(2):
        #    self.addLink(self.leafswitch[i], self.host[i*2])
        #    self.addLink(self.leafswitch[i], self.host[i*2+1])

        self.addLink(self.leafswitch[0], self.host[0])
        self.addLink(self.leafswitch[0], self.host[1])
        self.addLink(self.leafswitch[0], self.host[2])
        self.addLink(self.leafswitch[1], self.host[3])
        self.addLink(self.leafswitch[1], self.host[4])
        self.addLink(self.leafswitch[2], self.host[5])
        self.addLink(self.leafswitch[2], self.host[6])

class IpHost(Host):
    def __init__(self, name, gateway, *args, **kwargs):
        super(IpHost, self).__init__(name,*args,**kwargs)
        self.gateway = gateway

    def config(self, **kwargs):
        Host.config(self,**kwargs)
        mtu = "ifconfig " + self.name + "-eth0 mtu 1490"
        self.cmd(mtu)
        self.cmd('ip route add default via %s' % self.gateway)

topos = {'mytopo': (lambda: MyTopo())}

if __name__ == "__main__":
    setLogLevel('info')

    topo = MyTopo()
    net = Mininet(topo=topo, link=TCLink, controller=None)
    net.addController('c0', switch=OVSSwitch, controller=RemoteController, ip="127.0.0.1")

    net.start()
    CLI(net)
    net.stop()
