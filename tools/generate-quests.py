"""Generates the add-on's FTB Quests chapters: a guide and a release calendar.

Reads the parts (src/main/resources/data/data_center_addon/datacenter/part), the cables and switches
(CableType.java, SwitchModel.java) and writes the quest files the add-on installs into FTB Quests:

  src/main/resources/data_center_addon_quests/chapters/<file>.snbt   one per chapter
  src/main/resources/data_center_addon_quests/lang/en_us.snbt        titles and texts
  src/main/resources/data_center_addon_quests/lang/de_de.snbt
  src/main/resources/data_center_addon_quests/manifest.properties    groups, chapters, content hash

Run after changing parts, cables or switches:  python tools/generate-quests.py
"""
import glob
import hashlib
import json
import os
import re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src/main/resources')
OUT = os.path.join(RES, 'data_center_addon_quests')
NS = 'data_center_addon'


# ---------------------------------------------------------------------------------------------- ids

def qid(key):
    """Stable 16-digit hex id for an object of the add-on's quest book."""
    # FTB Quests reads ids as signed longs: keep them positive (first digit 2-7, like its own ids)
    h = hashlib.sha1(('data_center_addon:' + key).encode()).hexdigest()[:16].upper()
    return '234567'[int(h[0], 16) % 6] + h[1:]


# ---------------------------------------------------------------------------------------------- SNBT

def s(text):
    return '"' + text.replace('\\', '\\\\').replace('"', '\\"') + '"'


def key(name):
    return name if re.fullmatch(r'[A-Za-z0-9_.+-]+', name) else s(name)


def snbt(value, indent=0):
    """FTB Library's SNBT layout: tabs, no commas between compound entries."""
    pad = '\t' * (indent + 1)
    end = '\t' * indent
    if isinstance(value, dict):
        if not value:
            return '{ }'
        lines = [f'{pad}{key(k)}: {snbt(v, indent + 1)}' for k, v in value.items()]
        return '{\n' + '\n'.join(lines) + '\n' + end + '}'
    if isinstance(value, list):
        if not value:
            return '[ ]'
        if all(isinstance(v, str) for v in value):
            return '[' + ', '.join(s(v) for v in value) + ']'
        return '[\n' + '\n'.join(pad + snbt(v, indent + 1) for v in value) + '\n' + end + ']'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, float):
        return f'{value}d'
    if isinstance(value, int):
        return str(value)
    return s(value)


# ---------------------------------------------------------------------------------------------- data

def load_parts():
    parts = {}
    for path in glob.glob(os.path.join(RES, 'data', NS, 'datacenter', 'part', '*.json')):
        j = json.load(open(path, encoding='utf-8'))
        pid = os.path.basename(path)[:-5]
        parts[pid] = {'id': pid, 'name': j['name'], 'category': j['shop']['category'],
                      'date': j['shop']['date'], 'price': j['shop']['price']}
    return parts


def load_network():
    """Cables and switches from their Java enums, names from the add-on's English lang file."""
    lang = json.load(open(os.path.join(RES, 'assets', NS, 'lang', 'en_us.json'), encoding='utf-8'))
    java = os.path.join(ROOT, 'src/main/java/de/f4ked/data_center_addon/network')
    items = []
    cable_src = open(os.path.join(java, 'CableType.java'), encoding='utf-8').read()
    for name, date, price in re.findall(r'^\s{4}([A-Z0-9_]+)\(Medium\.\w+,\s*[\d.]+,\s*[\d_]+,\s*"([\d-]+)",\s*([\d_]+)\)', cable_src, re.M):
        item = f'cable_{name.lower()}'
        items.append({'item': f'{NS}:{item}', 'name': lang[f'block.{NS}.{item}'], 'date': date,
                      'price': int(price.replace('_', '')), 'category': 'NETWORK'})
    switch_src = open(os.path.join(java, 'SwitchModel.java'), encoding='utf-8').read()
    for name, display, date, price in re.findall(r'^\s{4}([A-Z0-9_]+)\("([^"]+)",\s*"([\d-]+)",\s*([\d_]+),', switch_src, re.M):
        items.append({'item': f'{NS}:switch_{name.lower()}', 'name': display, 'date': date,
                      'price': int(price.replace('_', '')), 'category': 'NETWORK'})
    return items


def usd(cents):
    dollars = cents / 100
    return f'${dollars:,.2f}' if dollars < 10 else f'${dollars:,.0f}'


def part_stack(pid):
    return {'components': {'datacenter:part_id': f'{NS}:{pid}'}, 'count': 1, 'id': 'datacenter:part'}


def voucher(cents):
    return {'components': {'datacenter:payment_cents': cents}, 'count': 1, 'id': 'datacenter:payment_voucher'}


# ---------------------------------------------------------------------------------------------- text

CATEGORIES = [
    ('CPU_INTEL', 'Intel CPUs', 'Intel-CPUs'),
    ('CPU_AMD', 'AMD CPUs', 'AMD-CPUs'),
    ('CPU_SERVER', 'Server CPUs', 'Server-CPUs'),
    ('GPU_NVIDIA', 'NVIDIA graphics cards', 'NVIDIA-Grafikkarten'),
    ('GPU_AMD', 'Radeon & Arc graphics cards', 'Radeon- & Arc-Grafikkarten'),
    ('GPU_DATACENTER', 'Data center accelerators', 'Rechenzentrums-Beschleuniger'),
    ('GPU_MINING', 'Mining cards', 'Mining-Karten'),
    ('MAINBOARD', 'Mainboards', 'Mainboards'),
    ('MAINBOARD_MINING', 'Mining boards', 'Mining-Boards'),
    ('RAM', 'Memory', 'Arbeitsspeicher'),
    ('STORAGE', 'Storage', 'Speicher'),
    ('PSU', 'Power supplies', 'Netzteile'),
    ('FAN', 'Fans', 'Lüfter'),
    ('NETWORK', 'Network cables & switches', 'Netzwerkkabel & Switches'),
]

# (part id or item id, English text, German text): the milestones of the release calendar
HIGHLIGHTS = [
    ('item:cable_cat3', 'The first twisted-pair standard for 10 Mbit/s Ethernet. Slow, but it gets every early server room online.',
     'Der erste Twisted-Pair-Standard für 10-Mbit-Ethernet. Langsam, aber er bringt jeden frühen Serverraum ans Netz.'),
    ('item:switch_hub_3com_superstack_ii', 'A hub, not a switch: every port shares the same 10 Mbit/s.',
     'Ein Hub, kein Switch: Alle Ports teilen sich dieselben 10 Mbit/s.'),
    ('item:cable_cat5', 'Fast Ethernet: ten times the speed of CAT3 at the same 100 metres.',
     'Fast Ethernet: zehnmal so schnell wie CAT3, bei denselben 100 Metern.'),
    ('intel_celeron_300a', 'The legendary overclocker: a cheap Celeron that ran at 450 MHz for anyone who dared.',
     'Der legendäre Übertakter: ein günstiger Celeron, der bei Mutigen mit 450 MHz lief.'),
    ('amd_athlon_600', 'AMD\'s first Athlon - from now on AMD races Intel at the top.',
     'AMDs erster Athlon - ab jetzt kämpft AMD mit Intel um die Spitze.'),
    ('item:cable_cat5e', 'Gigabit Ethernet over copper. For years the backbone of every server room.',
     'Gigabit-Ethernet über Kupfer. Jahrelang das Rückgrat jedes Serverraums.'),
    ('amd_athlon_1000', 'The first x86 processor to reach 1 GHz.',
     'Der erste x86-Prozessor mit 1 GHz.'),
    ('intel_pentium_4_1500', 'NetBurst: Intel bets on clock speed.',
     'NetBurst: Intel setzt alles auf Takt.'),
    ('nvidia_geforce3', 'The first graphics card with programmable shaders.',
     'Die erste Grafikkarte mit programmierbaren Shadern.'),
    ('item:switch_cisco_catalyst_2950', 'A real switch: every port gets its own bandwidth.',
     'Ein echter Switch: Jeder Port bekommt seine eigene Bandbreite.'),
    ('ati_radeon_9700_pro', 'The first DirectX 9 card - ATI takes the crown.',
     'Die erste DirectX-9-Karte - ATI übernimmt die Krone.'),
    ('intel_pentium_4_3060_ht', 'Hyper-Threading arrives on the desktop: two threads on one core.',
     'Hyper-Threading kommt auf den Desktop: zwei Threads auf einem Kern.'),
    ('item:cable_om3', 'Multimode fibre at 10 Gbit/s over 300 metres.',
     'Multimode-Glasfaser mit 10 Gbit/s über 300 Meter.'),
    ('amd_opteron_244', 'AMD64 enters the server room.',
     'AMD64 zieht in den Serverraum ein.'),
    ('amd_athlon_64_3200', 'The first 64-bit x86 desktop processor.',
     'Der erste 64-Bit-x86-Desktopprozessor.'),
    ('amd_athlon_64_x2_3800', 'Dual-core for everyone.',
     'Doppelkern für alle.'),
    ('intel_core_2_duo_e6600', 'Core 2: Intel is back on top, cooler and faster.',
     'Core 2: Intel ist wieder vorn, kühler und schneller.'),
    ('intel_core_2_extreme_qx6700', 'The first quad-core desktop processor.',
     'Der erste Vierkern-Desktopprozessor.'),
    ('nvidia_geforce_8800_gts_640', 'G80 and CUDA: graphics cards start to compute.',
     'G80 und CUDA: Grafikkarten fangen an zu rechnen.'),
    ('nvidia_tesla_c870', 'The first Tesla: a graphics card without a display output, made only for compute.',
     'Die erste Tesla: eine Grafikkarte ohne Bildausgang, nur zum Rechnen.'),
    ('intel_core_2_quad_q6600', 'The quad-core that everyone bought.',
     'Der Vierkerner, den alle gekauft haben.'),
    ('item:cable_cat6a', '10 Gbit/s over the full 100 metres of copper.',
     '10 Gbit/s über die vollen 100 Meter Kupfer.'),
    ('item:switch_cisco_nexus_5010', 'Data-centre switching at 10 Gbit/s per port.',
     'Rechenzentrums-Switching mit 10 Gbit/s pro Port.'),
    ('intel_core_i7_920', 'Nehalem: integrated memory controller and Hyper-Threading are back.',
     'Nehalem: integrierter Speichercontroller, und Hyper-Threading ist zurück.'),
    ('item:cable_om4', '100 Gbit/s over multimode fibre.',
     '100 Gbit/s über Multimode-Glasfaser.'),
    ('ati_radeon_hd_5870', 'The card of the first GPU mining wave.',
     'Die Karte der ersten GPU-Mining-Welle.'),
    ('amd_phenom_ii_x6_1090t', 'Six cores on the desktop.',
     'Sechs Kerne auf dem Desktop.'),
    ('intel_core_i7_2600k', 'Sandy Bridge: the processor that lasted a decade.',
     'Sandy Bridge: der Prozessor, der ein Jahrzehnt hielt.'),
    ('amd_fx_8150', 'Bulldozer: eight cores, a bold bet.',
     'Bulldozer: acht Kerne, eine kühne Wette.'),
    ('amd_radeon_hd_7970', 'GCN arrives - a mining favourite for years.',
     'GCN ist da - jahrelang ein Mining-Liebling.'),
    ('nvidia_tesla_k20x', 'The accelerator behind the Titan supercomputer.',
     'Der Beschleuniger hinter dem Supercomputer Titan.'),
    ('amd_radeon_r9_290x', 'The card of the Litecoin mining rush.',
     'Die Karte des Litecoin-Mining-Rauschs.'),
    ('nvidia_geforce_gtx_titan', 'The first TITAN: a consumer card with data-centre power.',
     'Die erste TITAN: eine Consumer-Karte mit Rechenzentrums-Leistung.'),
    ('nvidia_tesla_k80', 'Two GPUs on one board, made for clusters.',
     'Zwei GPUs auf einer Platine, gebaut für Cluster.'),
    ('nvidia_geforce_gtx_1080', 'Pascal: a huge leap in efficiency.',
     'Pascal: ein gewaltiger Effizienzsprung.'),
    ('amd_radeon_rx_480', 'The Ethereum miner\'s card.',
     'Die Karte der Ethereum-Miner.'),
    ('nvidia_tesla_p100', 'HBM2 memory and NVLink for deep learning.',
     'HBM2-Speicher und NVLink für Deep Learning.'),
    ('item:cable_cat8', '40 Gbit/s over copper - but only 30 metres, for the rack row.',
     '40 Gbit/s über Kupfer - aber nur 30 Meter, für die Rack-Reihe.'),
    ('amd_ryzen_7_1800x', 'Ryzen: AMD is back.',
     'Ryzen: AMD ist zurück.'),
    ('amd_epyc_7601', 'EPYC: 32 cores per socket.',
     'EPYC: 32 Kerne pro Sockel.'),
    ('nvidia_tesla_v100', 'Tensor cores: the era of AI accelerators begins.',
     'Tensor-Kerne: Die Ära der KI-Beschleuniger beginnt.'),
    ('asus_b250_mining_expert', '19 graphics cards on one mainboard.',
     '19 Grafikkarten auf einem Mainboard.'),
    ('item:cable_os2', 'Single-mode fibre at 400 Gbit/s over 10 kilometres.',
     'Singlemode-Glasfaser mit 400 Gbit/s über 10 Kilometer.'),
    ('nvidia_p102_100', 'A mining card without a display output.',
     'Eine Mining-Karte ohne Bildausgang.'),
    ('nvidia_geforce_rtx_2080_ti', 'Real-time ray tracing arrives.',
     'Echtzeit-Raytracing ist da.'),
    ('amd_ryzen_9_3950x', '16 cores on a desktop socket.',
     '16 Kerne auf einem Desktop-Sockel.'),
    ('amd_epyc_7742', '64 cores per socket.',
     '64 Kerne pro Sockel.'),
    ('nvidia_a100', 'Ampere: the accelerator of the AI boom.',
     'Ampere: der Beschleuniger des KI-Booms.'),
    ('nvidia_geforce_rtx_3090', '24 GB of GDDR6X for gamers and miners alike.',
     '24 GB GDDR6X für Gamer und Miner gleichermaßen.'),
    ('item:switch_nvidia_spectrum_sn4700', '400 Gbit/s per port for the AI data centre.',
     '400 Gbit/s pro Port für das KI-Rechenzentrum.'),
    ('nvidia_cmp_90hx', 'NVIDIA\'s dedicated mining card at the height of the boom.',
     'NVIDIAs eigene Mining-Karte auf dem Höhepunkt des Booms.'),
    ('intel_core_i9_12900k', 'Alder Lake: performance and efficiency cores together.',
     'Alder Lake: Performance- und Effizienzkerne gemeinsam.'),
    ('amd_ryzen_7_5800x3d', '3D V-Cache: stacked cache for games.',
     '3D V-Cache: gestapelter Cache für Spiele.'),
    ('nvidia_geforce_rtx_4090', 'Ada Lovelace: the fastest graphics card of its time.',
     'Ada Lovelace: die schnellste Grafikkarte ihrer Zeit.'),
    ('nvidia_h100_sxm', 'Hopper: the most wanted chip of the AI era.',
     'Hopper: der begehrteste Chip der KI-Ära.'),
    ('amd_instinct_mi300x', '192 GB of HBM3 on one accelerator.',
     '192 GB HBM3 auf einem Beschleuniger.'),
    ('nvidia_b200', 'Blackwell: two dies, one accelerator.',
     'Blackwell: zwei Dies, ein Beschleuniger.'),
    ('amd_epyc_9965', '192 cores per socket.',
     '192 Kerne pro Sockel.'),
    ('nvidia_geforce_rtx_5090', 'Blackwell for the desktop.',
     'Blackwell für den Desktop.'),
]

# ------------------------------------------------------------------------------------------- guide

C = 'checkmark'

# (key, icon item, task, reward cents, title en, title de, desc en, desc de); task: 'checkmark' or (item id, count, match)
GUIDE = [
    ('start', 'minecraft:book', 'Getting Started', '1 · Erste Schritte', [
        ('welcome', 'minecraft:book', C, 1000, 'Welcome to the Data Center AddOn', 'Willkommen beim Data Center AddOn',
         ['The add-on brings real hardware, a real network and a management terminal to Data Center:',
          '',
          '&e• &f711 real computer parts&r from 1998 to today, each at its real release date and launch price.',
          '&e• &fReal network cables and switches&r, from CAT3 to single-mode fibre.',
          '&e• &fThe Hosting Manager&r: customers, income and control of all your hosting racks.',
          '&e• &fAn overlay&r with the date, your wallet and crypto prices.',
          '',
          'This guide explains everything step by step. The &6Release Calendar&r tab shows what comes out when.'],
         ['Das Add-on bringt echte Hardware, ein echtes Netzwerk und ein Verwaltungsterminal zu Data Center:',
          '',
          '&e• &f711 echte Computer-Bauteile&r von 1998 bis heute, jeweils zum echten Erscheinungsdatum und Einführungspreis.',
          '&e• &fEchte Netzwerkkabel und Switches&r, von CAT3 bis Singlemode-Glasfaser.',
          '&e• &fDer Hosting Manager&r: Kunden, Einnahmen und Steuerung all deiner Hosting Racks.',
          '&e• &fEin Overlay&r mit Datum, Geldbörse und Kryptopreisen.',
          '',
          'Dieser Leitfaden erklärt alles Schritt für Schritt. Der Reiter &6Release-Kalender&r zeigt, was wann erscheint.']),
        ('shop', 'datacenter:shop', C, 1000, 'Real parts in the Shop', 'Echte Bauteile im Shop',
         ['Every real part appears in the Shop on its real release date, at its real launch price.',
          'Data Center announces each new part in chat: &b"New in the Shop"&r.',
          '',
          'The tabs are called &fAMD&r, &fIntel&r, &fNVIDIA&r and &fRadeon/Arc&r. Network cables, switches and the Hosting Manager are under &fInfrastructure&r.',
          '',
          'The Shop window is wider than in Data Center, so long names fit. Adjust it under &7Config → Client → Shop → Extra Width&r.'],
         ['Jedes echte Bauteil erscheint im Shop an seinem echten Erscheinungsdatum, zu seinem echten Einführungspreis.',
          'Data Center kündigt jedes neue Teil im Chat an: &b„Neu im Shop“&r.',
          '',
          'Die Reiter heißen &fAMD&r, &fIntel&r, &fNVIDIA&r und &fRadeon/Arc&r. Netzwerkkabel, Switches und der Hosting Manager liegen unter &fInfrastructure&r.',
          '',
          'Das Shop-Fenster ist breiter als bei Data Center, damit lange Namen passen. Einstellbar unter &7Config → Client → Shop → Zusätzliche Breite&r.']),
        ('tiers', 'minecraft:amethyst_shard', C, 1000, 'Tiers: Common to Legendary', 'Stufen: Gewöhnlich bis Legendär',
         ['Every part is ranked against all parts of its kind:',
          '&fCommon&r, &aUncommon&r, &9Rare&r, &5Epic&r, &6Legendary&r.',
          '',
          'CPUs and GPUs are compared by compute (mining cards by hashrate), RAM by size, storage by capacity, power supplies by usable output, fans by airflow and mainboards by their slots.',
          '',
          'The tooltip border shows the tier at a glance. Turn it off under &7Config → Client → Tooltips&r.'],
         ['Jedes Teil wird mit allen Teilen seiner Art verglichen:',
          '&fGewöhnlich&r, &aUngewöhnlich&r, &9Selten&r, &5Episch&r, &6Legendär&r.',
          '',
          'CPUs und GPUs werden nach Compute verglichen (Mining-Karten nach Hashrate), RAM nach Größe, Speicher nach Kapazität, Netzteile nach nutzbarer Leistung, Lüfter nach Luftstrom und Mainboards nach ihren Slots.',
          '',
          'Der Tooltip-Rahmen zeigt die Stufe auf einen Blick. Abschaltbar unter &7Config → Client → Tooltips&r.']),
        ('overlay', 'minecraft:clock', C, 1000, 'The date & prices overlay', 'Das Datum- & Preis-Overlay',
         ['The overlay shows the &fin-game date&r, your &fwallet&r, what your &fcoins&r are worth and the &fprice of every coin&r with its change since yesterday.',
          '',
          'Press &eO&r to show or hide it (rebind under &7Controls → Data Center&r).',
          'Position, size, colours, date format and the list of coins: &7Config → Client → Overlay&r.'],
         ['Das Overlay zeigt das &fSpieldatum&r, deine &fGeldbörse&r, was deine &fCoins&r wert sind und den &fPreis jedes Coins&r mit seiner Änderung seit gestern.',
          '',
          'Drücke &eO&r zum Ein- und Ausblenden (änderbar unter &7Steuerung → Data Center&r).',
          'Position, Größe, Farben, Datumsformat und die Coin-Liste: &7Config → Client → Overlay&r.']),
        ('config', 'minecraft:comparator', C, 1000, 'Settings', 'Einstellungen',
         ['Everything is set under &7Mods → Data Center AddOn → Config&r:',
          '',
          '&e• &fReal Parts&r: hide Data Center\'s own (fictional) parts in the Shop.',
          '&e• &fNetwork&r: hide Data Center\'s cables and switches, switch power, cable reach.',
          '&e• &fHosting&r: storage boost of the add-on\'s drives.',
          '&e• &fClient&r: overlay, tooltips, Shop width.',
          '',
          'Changes to parts and network take effect the next time a world is loaded.'],
         ['Alles wird unter &7Mods → Data Center AddOn → Config&r eingestellt:',
          '',
          '&e• &fEchte Bauteile&r: blendet Data Centers eigene (fiktive) Teile im Shop aus.',
          '&e• &fNetzwerk&r: Data Centers Kabel und Switches ausblenden, Switch-Strom, Kabel-Reichweite.',
          '&e• &fHosting&r: Speicher-Bonus der Laufwerke des Add-ons.',
          '&e• &fClient&r: Overlay, Tooltips, Shop-Breite.',
          '',
          'Änderungen an Bauteilen und Netzwerk wirken beim nächsten Laden einer Welt.']),
    ]),
    ('parts', 'datacenter:server', 'Real Parts', '2 · Echte Bauteile', [
        ('server', 'datacenter:server', ('datacenter:server', 1, 'none'), 5000, 'Assemble a server', 'Baue einen Server',
         ['Assemble a server at the &fAssembly Bench&r from a mainboard, CPU, RAM, storage and a power supply.',
          '',
          'Hover over it: the tooltip lists its &fbuilt-in components&r, each coloured by its tier, and the server gets a tier of its own.'],
         ['Baue an der &fAssembly Bench&r einen Server aus Mainboard, CPU, RAM, Speicher und Netzteil.',
          '',
          'Fahre mit der Maus darüber: Der Tooltip listet die &fverbauten Komponenten&r, jeweils in der Farbe ihrer Stufe, und der Server bekommt eine eigene Stufe.']),
        ('boards', 'minecraft:iron_ingot', C, 1000, 'The right mainboard', 'Das richtige Mainboard',
         ['A CPU only fits a mainboard with its &fsocket&r, and the RAM must match the board\'s &fmemory generation&r (SDRAM, DDR, DDR2 ... DDR5).',
          '',
          'Every CPU socket has a real board from the day its first CPU comes out. Mining boards carry up to 19 graphics cards.'],
         ['Eine CPU passt nur auf ein Mainboard mit ihrem &fSockel&r, und der RAM muss zur &fSpeichergeneration&r des Boards passen (SDRAM, DDR, DDR2 ... DDR5).',
          '',
          'Jeder CPU-Sockel hat ab dem Tag seiner ersten CPU ein echtes Board. Mining-Boards tragen bis zu 19 Grafikkarten.']),
        ('hide', 'minecraft:barrier', C, 1000, 'Only real parts', 'Nur echte Bauteile',
         ['With &fHide Original Parts&r Data Center\'s fictional brands leave the Shop, and only real parts are for sale.',
          '&fHidden Categories&r decides which Shop categories are affected.',
          '',
          'Parts you already own keep working. Hidden parts are no longer announced in chat either.'],
         ['Mit &fOriginal-Bauteile ausblenden&r verschwinden Data Centers fiktive Marken aus dem Shop, und es gibt nur noch echte Teile.',
          '&fAusgeblendete Kategorien&r legt fest, welche Shop-Kategorien betroffen sind.',
          '',
          'Teile, die du schon besitzt, funktionieren weiter. Ausgeblendete Teile werden auch nicht mehr im Chat angekündigt.']),
        ('storage', 'minecraft:chest', C, 1000, 'Storage for hosting', 'Speicher fürs Hosting',
         ['The add-on\'s drives get &f+10 % capacity&r while their server runs in a Hosting Rack: more storage to rent out, more shared capacity.',
          '',
          'Set the bonus under &7Config → Hosting → Storage Boost&r.'],
         ['Die Laufwerke des Add-ons haben &f+10 % Kapazität&r, solange ihr Server in einem Hosting Rack läuft: mehr Speicher zum Vermieten, mehr geteilte Kapazität.',
          '',
          'Den Bonus stellst du unter &7Config → Hosting → Speicher-Bonus&r ein.']),
    ]),
    ('network', f'{NS}:cable_cat5e', 'Network: Cables & Switches', '3 · Netzwerk: Kabel & Switches', [
        ('cable', f'{NS}:cable_cat5e', (f'{NS}:cable_cat5e', 8, None), 2000, 'Your first data cable', 'Dein erstes Datenkabel',
         ['The add-on\'s cables carry compute through Data Center\'s network. They connect to racks, machines and Data Center\'s own cables.',
          '',
          'Every cable has the &freal speed&r of its standard: &fCAT5e&r = 1 Gbit/s = 250,000 compute per tick.',
          'Get them in the Shop or craft them: CAT3 from copper and string.'],
         ['Die Kabel des Add-ons transportieren Compute durch Data Centers Netz. Sie verbinden sich mit Racks, Maschinen und Data Centers eigenen Kabeln.',
          '',
          'Jedes Kabel hat die &fechte Geschwindigkeit&r seines Standards: &fCAT5e&r = 1 Gbit/s = 250.000 Compute pro Tick.',
          'Kaufe sie im Shop oder crafte sie: CAT3 aus Kupfer und Faden.']),
        ('reach', 'minecraft:lead', C, 1000, 'Speed and reach', 'Geschwindigkeit und Reichweite',
         ['Every cable also has its &freal maximum length&r:',
          '&7CAT3/5/5e/6a: &f100 blocks   &7CAT6 (10G): &f55   &7CAT8: &f30',
          '&7OM3: &f300   &7OM4: &f150   &7OS2: &f10,000',
          '',
          'A &frun&r is a stretch of cable between switches. If it is longer than its cable\'s reach, it falls back to &ca tenth&r of its speed.',
          'Without a switch the weakest cable limits the whole network.'],
         ['Jedes Kabel hat außerdem seine &fechte Maximallänge&r:',
          '&7CAT3/5/5e/6a: &f100 Blöcke   &7CAT6 (10G): &f55   &7CAT8: &f30',
          '&7OM3: &f300   &7OM4: &f150   &7OS2: &f10.000',
          '',
          'Ein &fStrang&r ist ein Kabelstück zwischen Switches. Ist er länger als die Reichweite seines Kabels, fällt er auf &cein Zehntel&r seiner Geschwindigkeit zurück.',
          'Ohne Switch begrenzt das schwächste Kabel das ganze Netz.']),
        ('switch', f'{NS}:switch_cisco_catalyst_2950', (f'{NS}:switch_cisco_catalyst_2950', 1, None), 10000, 'A real switch', 'Ein echter Switch',
         ['A switch ends every cable run plugged into it. From then on each run counts &fon its own&r: it carries the traffic of its own devices, and the switch\'s &fbackplane&r limits the total.',
          '',
          '&aA star beats a chain:&r give each rack its own cable to the switch.',
          'Switches also reset the cable reach.'],
         ['Ein Switch beendet jeden angeschlossenen Kabelstrang. Ab dann zählt jeder Strang &ffür sich&r: Er trägt den Verkehr seiner eigenen Geräte, und die &fBackplane&r des Switches begrenzt die Summe.',
          '',
          '&aEin Stern schlägt eine Kette:&r Gib jedem Rack ein eigenes Kabel zum Switch.',
          'Switches setzen außerdem die Kabel-Reichweite zurück.']),
        ('power', 'minecraft:redstone', C, 1000, 'Switches need power', 'Switches brauchen Strom',
         ['Every switch draws its &freal wattage&r in FE per tick, e.g. 30 FE/t for a Catalyst 2950 and 700 FE/t for a Spectrum SN4700.',
          '',
          '&cA switch without power cuts its network.&r Its status LED blinks red, and the Hosting Manager warns you about it.',
          'Turn it off under &7Config → Network → Switches Need Power&r.'],
         ['Jeder Switch braucht seine &fechte Leistungsaufnahme&r in FE pro Tick, z. B. 30 FE/t beim Catalyst 2950 und 700 FE/t beim Spectrum SN4700.',
          '',
          '&cEin Switch ohne Strom unterbricht sein Netz.&r Seine Status-LED blinkt rot, und der Hosting Manager warnt dich davor.',
          'Abschaltbar unter &7Config → Netzwerk → Switches brauchen Strom&r.']),
        ('fibre', f'{NS}:cable_om3', (f'{NS}:cable_om3', 8, None), 5000, 'Fibre', 'Glasfaser',
         ['Fibre goes much further than copper: &fOM3&r 10 Gbit/s over 300 blocks, &fOM4&r 100 Gbit/s over 150, &fOS2&r 400 Gbit/s over 10,000.',
          '',
          'Craft OM3 from glass panes, string and quartz.'],
         ['Glasfaser reicht viel weiter als Kupfer: &fOM3&r 10 Gbit/s über 300 Blöcke, &fOM4&r 100 Gbit/s über 150, &fOS2&r 400 Gbit/s über 10.000.',
          '',
          'OM3 craftest du aus Glasscheiben, Faden und Quarz.']),
        ('upgrade', 'minecraft:crafting_table', C, 1000, 'Upgrading cables', 'Kabel aufrüsten',
         ['Like Mekanism: &f8 cables around one material&r give 8 cables of the next tier.',
          '',
          '&7CAT3 → CAT5: &fredstone   &7→ CAT5e: &firon   &7→ CAT6: &fgold',
          '&7→ CAT6a: &fquartz   &7→ CAT8: &fdiamond',
          '&7OM3 → OM4: &famethyst   &7→ OS2: &fender pearl'],
         ['Wie bei Mekanism: &f8 Kabel um ein Material&r ergeben 8 Kabel der nächsten Stufe.',
          '',
          '&7CAT3 → CAT5: &fRedstone   &7→ CAT5e: &fEisen   &7→ CAT6: &fGold',
          '&7→ CAT6a: &fQuarz   &7→ CAT8: &fDiamant',
          '&7OM3 → OM4: &fAmethyst   &7→ OS2: &fEnderperle']),
        ('originals', 'datacenter:cable_cat', C, 1000, 'Data Center\'s own cables', 'Data Centers eigene Kabel',
         ['Data Center\'s cables keep working next to the new ones. With &fHide Original Cables & Switches&r its switch leaves the Shop and its cable recipes are disabled.',
          '',
          'The superconducting cable stays and is then crafted with OS2 fibre.'],
         ['Data Centers Kabel funktionieren neben den neuen weiter. Mit &fOriginal-Kabel & -Switches ausblenden&r verschwindet sein Switch aus dem Shop, und seine Kabelrezepte werden deaktiviert.',
          '',
          'Der Supraleiter bleibt und wird dann aus OS2-Glasfaser gecraftet.']),
    ]),
    ('hosting', f'{NS}:hosting_manager', 'Hosting Manager', '4 · Hosting Manager', [
        ('rack', 'datacenter:hosting_rack', ('datacenter:hosting_rack', 1, None), 50000, 'Hosting racks', 'Hosting Racks',
         ['A Hosting Rack rents out its servers: as &fdedicated servers&r, &fshared hosting&r, &fVPS&r or &fcolocation&r.',
          'Shared hosting, VPS and colocation win customers over time.'],
         ['Ein Hosting Rack vermietet seine Server: als &fDedicated Server&r, &fShared Hosting&r, &fVPS&r oder &fColocation&r.',
          'Shared Hosting, VPS und Colocation gewinnen mit der Zeit Kunden.']),
        ('manager', f'{NS}:hosting_manager', (f'{NS}:hosting_manager', 1, None), 100000, 'The Hosting Manager', 'Der Hosting Manager',
         ['The management terminal for all your hosting racks. Plug &fany data cable&r into it and run it to the network of your racks.',
          '',
          'It shows every rack on that network - and lets you control them from one place.'],
         ['Das Verwaltungsterminal für all deine Hosting Racks. Schließe &firgendein Datenkabel&r an und verlege es zum Netz deiner Racks.',
          '',
          'Er zeigt jedes Rack in diesem Netz - und steuert sie alle von einem Ort aus.']),
        ('overview', 'minecraft:spyglass', C, 10000, 'Overview', 'Übersicht',
         ['The &fOverview&r tab: customers, income per day and month, number of racks, reputation and customer class.',
          '',
          'The &fNetwork&r bar shows how much of the network\'s capacity is used. It warns you about switches without power and cable runs that are too long.'],
         ['Der Reiter &fÜbersicht&r: Kunden, Einnahmen pro Tag und Monat, Anzahl der Racks, Reputation und Kundenklasse.',
          '',
          'Der Balken &fNetz&r zeigt, wie viel der Netzkapazität genutzt wird. Er warnt vor Switches ohne Strom und zu langen Kabelsträngen.']),
        ('customers', 'minecraft:player_head', C, 10000, 'Customers', 'Kunden',
         ['The &fCustomers&r tab lists every customer segment - company websites, online shops, game servers, streaming and more - with its number of customers, share and income.',
          '',
          'The income is split by what each segment pays.'],
         ['Der Reiter &fKunden&r listet jedes Kundensegment - Firmen-Websites, Online-Shops, Game-Server, Streaming und mehr - mit Anzahl, Anteil und Einnahmen.',
          '',
          'Die Einnahmen werden nach dem aufgeteilt, was jedes Segment zahlt.']),
        ('control', 'minecraft:lever', C, 10000, 'Remote control', 'Fernsteuerung',
         ['The &fRacks&r tab lists every rack with its product, customers, share, income and status.',
          '',
          'Click a rack, then change its &fproduct&r with &e◀ ▶&r and its &ffill&r with &e◀ ▶&r - exactly like in the rack\'s own menu.',
          'Racks of other players are counted, but not listed.'],
         ['Der Reiter &fRacks&r listet jedes Rack mit Produkt, Kunden, Anteil, Einnahmen und Status.',
          '',
          'Klicke ein Rack an und stelle sein &fProdukt&r mit &e◀ ▶&r und seine &fAuslastung&r mit &e◀ ▶&r um - genau wie im Menü des Racks.',
          'Racks anderer Spieler werden gezählt, aber nicht aufgelistet.']),
        ('contracts', 'minecraft:writable_book', C, 30000, 'Contracts', 'Aufträge',
         ['The &fContracts&r tab offers deals from clients: keep a number of places of one product &ffree&r for a number of days.',
          '',
          'A day counts when the places were free for 80 % of it, and is paid. Otherwise the client gives a &cstrike&r; at three the contract fails, with a penalty and lost reputation.',
          'Finish it for a &abonus&r and reputation. Bigger clients unlock with your customer class.',
          '',
          'Every contract has a &frarity&r, coloured like the parts: &7Common&r, &aUncommon&r, &9Rare&r, &5Epic&r, &6Legendary&r. Rarer ones want more places but pay much more and bring more reputation. The better your customer class, the more often they come, and the more contracts you can run at once (3 up to 7). Delivery orders have the same rarities and places.',
          '',
          '&6⚡ Emergencies&r are short, well paid and open for one day only. &fMini orders&r for beginners have no penalty.'],
         ['Der Reiter &fAufträge&r bietet Angebote von Kunden: Halte eine Zahl von Plätzen eines Produkts für eine Zahl von Tagen &ffrei&r.',
          '',
          'Ein Tag zählt, wenn die Plätze 80 % der Zeit frei waren, und wird bezahlt. Sonst gibt es eine &cVerwarnung&r; bei drei platzt der Auftrag, mit Strafe und Reputationsverlust.',
          'Wer durchhält, bekommt einen &aBonus&r und Reputation. Größere Kunden kommen mit deiner Kundenklasse.',
          '',
          'Jeder Auftrag hat eine &fSeltenheit&r in den Farben der Teile: &7Gewöhnlich&r, &aUngewöhnlich&r, &9Selten&r, &5Episch&r, &6Legendär&r. Seltenere wollen mehr Plätze, zahlen aber deutlich mehr und bringen mehr Reputation. Je besser deine Kundenklasse, desto öfter kommen sie und desto mehr Aufträge kannst du gleichzeitig annehmen (3 bis 7). Lieferaufträge haben dieselben Seltenheiten und Plätze.',
          '',
          '&6⚡ Notfälle&r sind kurz, gut bezahlt und nur einen Tag annehmbar. &fMini-Aufträge&r für Einsteiger haben keine Strafe.']),
        ('startup', 'minecraft:gold_ingot', C, 50000, 'Founding your company', 'Firmengründung',
         ['The &fStartup&r tab helps a young company get its first money together:',
          '',
          '&e• &fStart bonus&r: extra income on every hosting rack, starting with the first income and fading out over time.',
          '&e• &fFounding grant&r: paid once with the first rack income.',
          '&e• &fSetup fees&r: every new customer pays a one-time fee.',
          '&e• &fPrepayment&r: part of the new customers pay 3-12 months in advance. The money comes at once and is settled against their income later.',
          '&e• &fFounding loan&r: interest-free, with days without repayment, then small daily instalments.',
          '&e• &fMilestones&r: rewards for customers, customer classes and your first contract and delivery.'],
         ['Der Reiter &fGründung&r hilft einer jungen Firma, das erste Geld zusammenzubekommen:',
          '',
          '&e• &fStartbonus&r: Extra-Einnahmen auf jedem Hosting Rack, ab der ersten Einnahme, läuft mit der Zeit aus.',
          '&e• &fGründerzuschuss&r: einmalig mit der ersten Rack-Einnahme.',
          '&e• &fEinrichtungsgebühren&r: Jeder neue Kunde zahlt einmalig eine Gebühr.',
          '&e• &fVorkasse&r: Ein Teil der neuen Kunden zahlt 3-12 Monate im Voraus. Das Geld kommt sofort und wird später mit ihren Einnahmen verrechnet.',
          '&e• &fGründerkredit&r: zinsfrei, erst tilgungsfreie Tage, dann kleine Tagesraten.',
          '&e• &fMeilensteine&r: Prämien für Kunden, Kundenklassen, deinen ersten Auftrag und die erste Lieferung.']),
        ('panel', f'{NS}:powered_server_panel', (f'{NS}:powered_server_panel', 1, None), 3000, 'Power through the wall', 'Strom durch die Wand',
         ['The &fPowered Server Room Panel&r seals a server room like any wall block and carries &fFE&r straight through.',
          'Place it in the wall and connect a cable or battery on each side.'],
         ['Das &fPowered Server Room Panel&r dichtet einen Serverraum ab wie jeder Wandblock und leitet &fFE&r direkt hindurch.',
          'Setze es in die Wand und schließe auf jeder Seite ein Kabel oder eine Batterie an.']),
    ]),
]

DECADES = [('1990s', '1990er', 1991, 1999), ('2000s', '2000er', 2000, 2009), ('2010s', '2010er', 2010, 2019), ('2020s', '2020er', 2020, 2029)]


# ------------------------------------------------------------------------------------------- build

def item_task(key, item, count, match, part=None):
    task = {'consume_items': False, 'id': qid(key + ':task'),
            'item': part_stack(part) if part else {'count': count, 'id': item}, 'type': 'item'}
    if part:
        task['match_components'] = 'fuzzy'
    elif match == 'none':
        task['match_components'] = 'none'
    return task


def build():
    parts = load_parts()
    network = load_network()
    by_name = {p['name']: p for p in parts.values()}
    chapters, en, de = {}, {}, {}
    guide_group = qid('group:guide')
    calendar_group = qid('group:calendar')
    en[f'chapter_group.{guide_group}.title'] = 'Data Center AddOn'
    de[f'chapter_group.{guide_group}.title'] = 'Data Center AddOn'
    en[f'chapter_group.{calendar_group}.title'] = 'Release Calendar'
    de[f'chapter_group.{calendar_group}.title'] = 'Release-Kalender'

    # ---- guide
    for order, (ckey, cicon, ctitle_en, ctitle_de, quests) in enumerate(GUIDE):
        cid = qid('chapter:' + ckey)
        filename = f'dca_guide_{ckey}'
        en[f'chapter.{cid}.title'] = f'{order + 1} · {ctitle_en}'
        de[f'chapter.{cid}.title'] = ctitle_de
        quest_list = []
        previous = None
        for i, (key, icon, task, reward, t_en, t_de, d_en, d_de) in enumerate(quests):
            full = f'{ckey}.{key}'
            q = {}
            if previous:
                q['dependencies'] = [previous]
            q['icon'] = {'id': icon}
            q['id'] = qid('quest:' + full)
            q['rewards'] = [{'id': qid(full + ':reward'), 'item': voucher(reward), 'type': 'item'}]
            q['shape'] = 'hexagon' if i == 0 else 'circle'
            q['size'] = 1.5 if i == 0 else 1.0
            if task == C:
                q['tasks'] = [{'id': qid(full + ':task'), 'type': 'checkmark'}]
            else:
                item, count, match = task
                q['tasks'] = [item_task(full, item, count, match)]
            q['x'] = float(i * 1.75)
            q['y'] = 0.0 if i % 2 == 0 else 1.25
            quest_list.append(q)
            en[f'quest.{q["id"]}.title'] = t_en
            de[f'quest.{q["id"]}.title'] = t_de
            en[f'quest.{q["id"]}.quest_desc'] = d_en
            de[f'quest.{q["id"]}.quest_desc'] = d_de
            previous = q['id']
        chapters[filename] = chapter(cid, filename, guide_group, cicon, order, quest_list)

    # ---- calendar: one quest per year, milestones hanging below
    entries = defaultdict(list)
    for p in parts.values():
        entries[int(p['date'][:4])].append(p)
    for n in network:
        entries[int(n['date'][:4])].append(n)
    highlights = defaultdict(list)
    for ref, text_en, text_de in HIGHLIGHTS:
        if ref.startswith('item:'):
            item = f'{NS}:{ref[5:]}'
            entry = next(n for n in network if n['item'] == item)
            highlights[int(entry['date'][:4])].append((ref, entry, None, item, text_en, text_de))
        else:
            if ref not in parts:
                raise SystemExit(f'unknown highlight part {ref}')
            entry = parts[ref]
            highlights[int(entry['date'][:4])].append((ref, entry, ref, None, text_en, text_de))

    cat_order = {c[0]: i for i, c in enumerate(CATEGORIES)}
    cat_names = {c[0]: (c[1], c[2]) for c in CATEGORIES}
    for order, (dec_en, dec_de, first, last) in enumerate(DECADES):
        years = [y for y in sorted(entries) if first <= y <= last]
        if not years:
            continue
        cid = qid('chapter:calendar:' + dec_en)
        filename = f'dca_calendar_{dec_en}'
        en[f'chapter.{cid}.title'] = dec_en
        de[f'chapter.{cid}.title'] = dec_de
        quest_list = []
        previous = None
        for i, year in enumerate(years):
            items = sorted(entries[year], key=lambda e: (cat_order.get(e['category'], 99), e['date'], e['name']))
            marks = sorted(highlights.get(year, []), key=lambda h: h[1]['date'])
            year_id = qid(f'quest:calendar:{year}')
            first_mark = marks[0] if marks else None
            q = {}
            if previous:
                q['dependencies'] = [previous]
            if first_mark and first_mark[2]:
                q['icon'] = part_stack(first_mark[2])
            elif first_mark:
                q['icon'] = {'id': first_mark[3]}
            else:
                q['icon'] = {'id': 'minecraft:paper'}
            q['id'] = year_id
            q['shape'] = 'gear'
            q['size'] = 1.5
            q['x'] = float(i * 2.5)
            q['y'] = 0.0
            quest_list.append(q)
            en[f'quest.{year_id}.title'] = str(year)
            de[f'quest.{year_id}.title'] = str(year)
            en[f'quest.{year_id}.quest_subtitle'] = f'{len(items)} releases'
            de[f'quest.{year_id}.quest_subtitle'] = f'{len(items)} Neuerscheinungen'
            for lang, out in ((0, en), (1, de)):
                lines = []
                category = None
                for e in items:
                    if e['category'] != category:
                        category = e['category']
                        if lines:
                            lines.append('')
                        lines.append('&6' + cat_names[category][lang])
                    date = e['date'][8:10] + '.' + e['date'][5:7] + '.' if lang == 1 else e['date'][5:]
                    price = usd(e['price'])
                    if lang == 1:
                        price = price.replace(',', 'X').replace('.', ',').replace('X', '.')
                    lines.append(f'&7{date} &f{e["name"]} &8{price}')
                out[f'quest.{year_id}.quest_desc'] = lines
            for j, (ref, entry, part, item, text_en, text_de) in enumerate(marks):
                mid = qid(f'quest:calendar:highlight:{ref}')
                m = {'dependencies': [year_id],
                     'icon': part_stack(part) if part else {'id': item},
                     'id': mid,
                     'shape': 'circle',
                     'size': 1.0,
                     'tasks': [item_task(f'calendar:highlight:{ref}', item, 1, None, part=part)],
                     'x': float(i * 2.5 + (j % 2) * 1.0 - 0.5 * (1 if len(marks) > 1 else 0)),
                     'y': float(1.75 + (j // 2) * 1.25 + (0.6 if j % 2 else 0))}
                quest_list.append(m)
                date_en = entry['date']
                date_de = entry['date'][8:10] + '.' + entry['date'][5:7] + '.' + entry['date'][:4]
                price_de = usd(entry['price']).replace(',', 'X').replace('.', ',').replace('X', '.')
                en[f'quest.{mid}.title'] = entry['name']
                de[f'quest.{mid}.title'] = entry['name']
                en[f'quest.{mid}.quest_subtitle'] = f'{date_en} · {usd(entry["price"])}'
                de[f'quest.{mid}.quest_subtitle'] = f'{date_de} · {price_de}'
                en[f'quest.{mid}.quest_desc'] = [text_en, '', '&7Own one to complete this milestone.']
                de[f'quest.{mid}.quest_desc'] = [text_de, '', '&7Besitze eins, um diesen Meilenstein abzuschließen.']
            previous = year_id
        icon = quest_list[0]['icon']
        chapters[filename] = chapter(cid, filename, calendar_group, icon, order, quest_list)
    return chapters, en, de, [guide_group, calendar_group]


def chapter(cid, filename, group, icon, order, quests):
    return {
        'default_hide_dependency_lines': False,
        'default_quest_shape': '',
        'filename': filename,
        'group': group,
        'icon': icon if isinstance(icon, dict) else {'id': icon},
        'id': cid,
        'images': [],
        'order_index': order,
        'quest_links': [],
        'quests': quests,
    }


def lang_snbt(entries):
    lines = [f'\t{key}: {snbt(value, 1)}' for key, value in sorted(entries.items())]
    return '{\n' + '\n'.join(lines) + '\n}\n'


def main():
    chapters, en, de, groups = build()
    os.makedirs(os.path.join(OUT, 'chapters'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'lang'), exist_ok=True)
    for f in glob.glob(os.path.join(OUT, 'chapters', '*.snbt')):
        os.remove(f)
    digest = hashlib.sha1()
    for name in sorted(chapters):
        text = snbt(chapters[name]) + '\n'
        digest.update(text.encode())
        with open(os.path.join(OUT, 'chapters', name + '.snbt'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
    for locale, entries in (('en_us', en), ('de_de', de)):
        text = lang_snbt(entries)
        digest.update(text.encode())
        with open(os.path.join(OUT, 'lang', locale + '.snbt'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
    with open(os.path.join(OUT, 'manifest.properties'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Generated by tools/generate-quests.py\n')
        f.write(f'hash={digest.hexdigest()}\n')
        f.write(f'groups={",".join(groups)}\n')
        f.write(f'chapters={",".join(sorted(chapters))}\n')
        f.write('locales=en_us,de_de\n')
    quests = sum(len(c['quests']) for c in chapters.values())
    print(f'Wrote {len(chapters)} chapters with {quests} quests, {len(en)} lang entries per language.')


if __name__ == '__main__':
    main()
