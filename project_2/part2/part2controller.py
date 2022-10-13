# Part 2 of UWCSE's Project 3
#
# based on Lab 4 from UCSC's Networking Class
# which is based on of_tutorial by James McCauley

from pox.core import core
import pox.openflow.libopenflow_01 as of
import pox.lib.packet as pkt

log = core.getLogger()

class Firewall (object):
  """
  A Firewall object is created for each switch that connects.
  A Connection object for that switch is passed to the __init__ function.
  """
  def __init__ (self, connection):
    # Keep track of the connection to the switch so that we can
    # send it messages!
    self.connection = connection

    # This binds our PacketIn event listener
    connection.addListeners(self)

    #add switch rules here
    flood = of.ofp_action_output(port = of.OFPP_FLOOD)

    #######################################################
    # ICMP
    # Src IP: Any IPv4
    # Dest IP: Any IPv4
    # Protocol: ICMP
    # Action: Accept
    rule1 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x800, # IPv4
      nw_proto = pkt.ipv4.ICMP_PROTOCOL
    ))
    rule1.actions.append(flood)
    self.connection.send(rule1)

    #######################################################
    # ARP
    # Src IP: Any
    # Dest IP: Any
    # Protocol: ARP
    # Action: Accept
    rule2 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x806, # ARP
      nw_proto = pkt.arp.REQUEST
    ))
    rule2.actions.append(flood)
    self.connection.send(rule2)

    rule3 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x806, # ARP
      nw_proto = pkt.arp.REPLY
    ))
    rule3.actions.append(flood)
    self.connection.send(rule3)

    rule4 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x806, # ARP
      nw_proto = pkt.arp.REV_REQUEST
    ))
    rule4.actions.append(flood)
    self.connection.send(rule4)

    rule5 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x806, # ARP
      nw_proto = pkt.arp.REV_REPLY
    ))
    rule5.actions.append(flood)
    self.connection.send(rule5)

    #######################################################
    # Drop everything else
    # Src IP: Any IPv4
    # Dest IP: Any IPv4
    # Protocol: -
    # Action: Drop
    rule6 = of.ofp_flow_mod(match = of.ofp_match(
      dl_type = 0x800 # IPv4
    ))
    # no action to drop
    self.connection.send(rule6)

    #######################################################

  def _handle_PacketIn (self, event):
    """
    Packets not handled by the router rules will be
    forwarded to this method to be handled by the controller
    """

    packet = event.parsed # This is the parsed packet data.
    if not packet.parsed:
      log.warning("Ignoring incomplete packet")
      return

    packet_in = event.ofp # The actual ofp_packet_in message.
    print ("Unhandled packet :" + str(packet.dump()))

def launch ():
  """
  Starts the component
  """
  def start_switch (event):
    log.debug("Controlling %s" % (event.connection,))
    Firewall(event.connection)
  core.openflow.addListenerByName("ConnectionUp", start_switch)
