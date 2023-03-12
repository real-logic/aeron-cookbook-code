The design assumes that the cluster is the system of record for Auctions, but not for Participants. 

This means that Auctions differ from the participant data in that the id's of auctions are defined by the cluster,
while participants are assumed to be loaded from another system of record, and thus are provided an id.

This domain logic is purposefully clean of any Aeron or SBE code.