place template dcsb:island -7 60 -5
setworldspawn -2 65 3
gamerule spawnRadius 0
forceload remove -7 -5 8 6
tp @a -1.5 65 3.5 -90 0
spawnpoint @a -2 65 3
data modify storage dcsb:state placed set value 1b
tellraw @a [{"text":"Data Center SkyBlock: ","color":"aqua"},{"text":"Willkommen auf deiner Insel! Die Truhe im Serverraum hat ein Handbuch. / Welcome to your island! The chest in the server room holds a guide.","color":"gray"}]
