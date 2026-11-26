# Data Center SkyBlock: the island is placed once, on the first tick its chunk is loaded
execute unless data storage dcsb:state {placed:1b} run forceload add -7 -5 8 6
