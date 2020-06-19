# reliable-transport

This program leverages a non-reliable transport layer, UDP, and makes it a reliable layer. A reliable layer ensures that no packets should be lost during the connection of client and server. This does not mean this layer provides flow control, but it is simply to make sure no packloss occur. Congestion control is added to not overload the user.

A reliable-transport layer is achieved by following the RDT 3.0 protocol. This is not a standard protocol, but rather more of a guideline to help achieve reliablity.

RDT 3.0 defines:
- Use numbered ACKs to determine in order packet acknowledgement.
- Server has a un-ACKed window to provide some congestion control but also to send multiple packets at once.
- A timer is set on the longest Un-ACKed packet. When a timeout occurs, all un-ACKed packets are resent.

Note: The Server's un-ACKed window must be less than half of the max number seq ID set on packets. Only then the Server can detect if a seq ID of any ACKed packet is meaningful (when the seqID is reset, we expect ACKed IDs to be below the limit again).
