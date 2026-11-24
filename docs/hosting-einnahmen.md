# Hosting-Einnahmen: wie sie entstehen, warum sie klein sind, was man tun kann

Grundlage ist der Code von Data Center 2.1.3, vor allem `HostingRackBlockEntity.runLoadout`,
`HostingRates`, `HostingModeRates`, `HostingGrowth` und `HostingMarket`. Die Zahlen unten wurden
mit einem Nachbau dieser Formeln berechnet. Es sind Richtwerte **vor** allen Multiplikatoren,
jeweils pro Spieler/Konto.

**Zeitmaß:** Alle Preise im Mod sind Monatsmieten. Ein Monat im Spiel hat 30,4 Spieltage. Bei
`fictionalDaysPerMcDay = 12` (Standard) dauert er etwa 2,5 Minecraft-Tage, also rund 50 Minuten
Echtzeit. In der Dev-Config steht 24, dort sind es rund 25 Minuten. „/Tag“ im Hosting Manager
bedeutet pro Spieltag.

---

## 1. Das Kernproblem: der Markt deckelt, nicht die Hardware

Bei Shared, VPS und Colocation kommen die Kunden aus einem **Kundenbuch pro Konto und Produkt**.
Alle Racks desselben Produkts teilen sich dieses Buch:

- **Zulauf:** Pro Monat kommen `Bestellungen × Reichweite` neue Kunden, aber nur so viele, wie
  freie Plätze da sind. Die Reichweite liegt zwischen 1,0 bei 0 Reputation und 3,0 bei
  1000 Reputation.
- **Abgang:** Pro Monat kündigen etwa 3 % (Churn), je nach Produkt und Kundensegment.
- **Gleichgewicht:** Daraus ergibt sich `Kunden ≈ Bestellungen / Churn`. Mehr Kunden bekommt
  man nicht, egal wie viele Racks man aufstellt.
- **Verteilung:** Die Kunden werden anteilig nach Kapazität auf alle Server des Produkts
  verteilt (`HostingMarket.takeRate`).

Bis das Gleichgewicht erreicht ist, vergehen 20 bis 60 Spielmonate. Bei Standardtempo sind das
**15 bis 50 Stunden Echtzeit**. Deshalb wirken die Einnahmen am Anfang so klein: Das Kundenbuch
füllt sich mit 20 bis 50 neuen Kunden pro Monat. Ein einziger moderner Server hätte Platz für
mehrere hundert.

### Marktobergrenze pro Produkt (Gleichgewicht, netto pro Monat, ohne Multiplikatoren)

| Produkt | 1999, Rep 0 | 2003, Rep 0 | 2009, Rep 0 | 2009, Rep 1000 |
|---|---|---|---|---|
| Shared Web | 637 Kd. → **$1.680** | 651 → $1.810 | 1.515 → $1.110 | 4.546 → $3.320 |
| Shared Mail | 270 → $505 | 315 → $655 | 545 → $285 | 1.634 → $860 |
| Reseller | 424 → $1.350 | 433 → $1.445 | 1.008 → $885 | 3.025 → $2.660 |
| Gameserver | 22 → $805 | 57 → $1.155 | 140 → $1.600 | 421 → $4.800 |
| VPS (ab 2002) | – | 119 → $1.125 | 592 → **$3.600** | 1.775 → **$10.800** |
| Colocation | 16 → **$1.990** | 8 → $460 | 15 → $965 | 45 → $2.900 |
| **Summe** | ≈ $6.300 | ≈ $6.650 | ≈ $8.450 | ≈ $25.300 |

Umgerechnet: 2009 mit 0 Reputation sind auch bei *vollem* Markt in **allen** Produkten nur etwa
**$280 pro Spieltag** drin. Bei einem halb gefüllten Markt ist es entsprechend weniger.

## 2. Was ein Kunde wirklich einbringt

Der Mod zieht vom Listenpreis einen Anteil für Traffic, Support usw. ab
(`*_NET_SHARE` in `HostingModeRates`). Übrig bleibt die Marge:

| Produkt | Listenpreis 1999 / 2009 | Marge | netto 1999 / 2009 | Lizenz |
|---|---|---|---|---|
| Shared Web | $25 / $7 | **10 %** | $2,50 / $0,70 | −$30 pro Server und Monat |
| Shared Mail | 50 % von Web | 15 % | $1,88 / $0,53 | −$30 |
| Reseller | 20 % von Web | 60 % | $3,00 / $0,84 | −$30 |
| Gameserver | $60 / $19 | 60 % | **$36 / $11,40** | keine |
| VPS | $30 / $17 | 35 % | $10,50 / $5,95 | keine (aber Hypervisor-OS) |
| Colocation | Schrankpreis / Server pro Schrank | 100 % | **$125 / $67** | keine |

Danach kommt noch der Segment-Mix dazu. Dotcom, Shop, Gameserver und Enterprise zahlen mehr.
Traffic-lastige Segmente kosten Leitungsmarge.

### Die cPanel/Plesk-Lizenz ist die Falle

Für Shared (außer Gameserver) wird **pro Server** gerechnet: `zahlende Kunden × Marge − $30`.
Ein negatives Ergebnis wird auf $0 gesetzt. Damit sich ein Server lohnt, braucht er also eine
Mindestzahl an Kunden:

| Produkt | 1999 | 2003 | 2006 | 2009 |
|---|---|---|---|---|
| Shared Web | 11 | 11 | 32 | **41** |
| Shared Mail | 16 | 14 | 45 | **57** |
| Reseller | 9 | 9 | 27 | **34** |

Weil die Kunden **gleichmäßig auf alle Server** verteilt werden, verdienen viele Server mit
wenigen Kunden alle $0. Das ist das „$0 trotz Kunden“-Phänomen, das der Hosting Manager als
Lizenz-Warnung zeigt.

> Beispiel 2009, 1.500 Web-Kunden:
> - auf 32 Servern: je 47 Kunden → je $3 → **≈ $100/Monat**
> - auf 3 Servern: je 500 Kunden → je $335 → **≈ $1.000/Monat**

## 3. Dedicated: das einzige Produkt ohne Marktdeckel

Dedicated-Server haben kein Kundenbuch. **Jeder bestückte, laufende Server zahlt Miete:**

- Miete pro Server = `max(Mindestmiete, Compute × $/Compute-Einheit)`
- Datacenter-CPUs/GPUs zählen **×3** beim Compute.
- **Storage-Miete** kommt pro GB dazu.

| | 1999 | 2001 | 2003 | 2006 | 2009+ |
|---|---|---|---|---|---|
| Mindestmiete | $170–200 | $67 | $80 | $73 | $30 |
| $ pro Compute-Einheit | 1,17 | 0,20 | 0,09 | 0,04 | 0,015 → 0,004 (2028) |
| $ pro GB Storage | **5,00** | 2,00 | 0,60 | 0,15 | 0,012 |

Beispiele:

- **1999:** Pentium III 500 (181 Compute) mit 10 GB bringt etwa **$260 pro Monat**. Für einen
  Server ist das mehr, als ein Shared-Web-Kunde in 8 Jahren einbringt.
- **2009:** Core 2 Quad Q9400 (3.848 Compute), zwei Stück, bringt etwa $115 pro Monat.
- **Später:** Ein Xeon Platinum 8480+ (Datacenter, ×3) bringt etwa **$900 pro Monat pro CPU**.

Dedicated skaliert linear mit Hardware und Strom. Es ist der Weg, über die Marktobergrenzen
hinauszukommen.

## 4. Multiplikatoren (werden alle nacheinander multipliziert)

| Faktor | Spanne | Wie man ihn hebt |
|---|---|---|
| Kundenklasse (Reputation) | 1,00 → 1,08 → 1,18 → 1,30 → **1,45** | Reputation: 150/350/600/850 Punkte. Ab Mid-Market braucht es auch die passende Zertifizierung des Raums. Racks haben eine maximale Klasse. |
| Raum-Zertifizierung | 1,00 / 1,05 / 1,15 / 1,30 / **1,50** | Tier I–IV im Serverraum |
| Reputation → Reichweite | Bestellungen ×1 bis **×3** | Uptime, keine Ausfälle, keine Überlast-Beschwerden |
| Betriebssystem | 0,85 bis 1,18 | Hypervisor (ESXi 1,18, VMware ESX 1,15) oder Windows 1,08. Bei VPS zählt der OS-Bonus **nicht**. Veraltete OS verlieren 3 % pro Jahr. |
| Grünstrom | bis +8 % | Anteil grüner Energie am Standort |
| Tageslastkurve | 0,85 bis 1,15 | Spitze um 18 Uhr; im Mittel ±0 |
| Dotcom-Stimmung | 0,63 bis 1,15 | Nur in Welten mit Start vor 2009. Tief 2002/03, ab 2005 bei 0,95. Gilt nicht für Colocation. |
| Angriffe, Politik | ≤ 1 | |
| `economy.hostingIncomeMultiplier` | Standard 1,0 | Server-Config |

Volle Klasse und Tier IV ergeben zusammen **×2,2**, mit OS und Grünstrom etwa ×2,8. Das ist viel,
aber auf einer kleinen Basis.

## 5. Tipps & Tricks

### Allgemein

1. **Alle Produkte gleichzeitig anbieten.** Jedes Produkt hat sein eigenes Kundenbuch und damit
   seine eigene Obergrenze. Fünf Produkte mit je einem kleinen Rack bringen mehr als fünf Racks
   mit Shared Web.
2. **Früh anfangen.** Kunden sammeln sich über Spieljahre an. Ein Rack, das in Jahr 1 läuft, hat
   in Jahr 3 einen vollen Kundenstamm.
3. **Reputation ist der größte Hebel für Kundenzahlen** (×3 Bestellungen). Ausfälle, Überhitzung
   (Drosselung), Netzwerk-Engpässe und Überlast-Beschwerden kosten Reputation. Ein Absturz kostet
   außerdem direkt 2 % × Ausfallgewicht der Kunden.
4. **Nicht überbuchen, außer der Markt ist größer als die Kapazität.** Füllgrad über 100 % schafft
   nur Plätze, die über der echten Kapazität liegen. Dort zahlen Kunden weniger
   (`1 − 0,5 × Überlast`), und ab 25 % Überlast gibt es volle Beschwerden. Überbuchen lohnt sich
   nur, wenn alle Plätze voll sind und Bestellungen wegfallen.
5. **Etwas Luft lassen:** Neue Kunden kommen nur auf freie Plätze. Ein paar Prozent Reserve
   reichen.
6. **Produkt nicht ständig wechseln.** Während des Umzugs (`modeSwitchTicks`) verdient das Rack
   nichts.

### Shared (Web, Mail, Reseller)

7. **Wenige, starke Server statt viele schwache.** Wegen der $30-Lizenz pro Server muss jeder
   Server über der Break-even-Zahl liegen (2009: Web 41, Mail 57, Reseller 34 Kunden).
   Überzählige Server rausnehmen oder auf Dedicated umstellen.
8. **Reseller vor Mail.** Bei ähnlich vielen Kunden bringt Reseller 3× so viel pro Kunde wie Mail
   (60 % statt 15 % Marge).
9. **Shared Web lohnt bis etwa 2004 richtig.** Danach fällt der Preis von $28 auf $7, die Marge
   bleibt bei 10 %.

### Gameserver

10. **Die beste Marge pro Kunde: $36 (1999) bzw. $11,40 (2009), ohne Lizenz.** Ein Gameserver-
    Kunde bringt so viel wie 15 Web-Kunden. Der Markt ist klein (20 bis 140 Kunden bei Rep 0),
    aber schon 1999 sind $800 pro Monat drin.
11. Gameserver brauchen viel Compute pro Kunde (35 bis 128 Einheiten). Dafür eignet sich ein
    Server mit kräftiger CPU. Ein beliebiges Server-OS reicht (kein Solaris, kein Hypervisor),
    cPanel wird nicht gebraucht.

### VPS (ab 2002)

12. **Ab etwa 2005 das stärkste Marktprodukt** (2009: $3.600 bei Rep 0, $10.800 bei Rep 1000).
    VPS braucht ein **Hypervisor-OS** (VMware ESX, Virtuozzo, Xen, OpenVZ, ESXi). VMware ESX und
    ESXi kosten zusätzlich Lizenz pro Rack ($104 bzw. $145 pro Monat). Xen und OpenVZ sind
    kostenlos.

### Colocation

13. **Pro Kunde das meiste Geld ($60 bis $125 pro Monat) ohne eigene Hardware.** Gebraucht werden
    nur leere Einschübe und Strom (150 bis 250 W pro Kunde). Der Zulauf ist allerdings winzig:
    0,15 bis 0,7 Kunden pro Monat. Deshalb **sofort ein Rack mit leeren Einschüben aufstellen und
    laufen lassen.** Ein voller Kundenstamm (15 bis 45) dauert 5+ Spieljahre.
14. Colocation ist von der Dotcom-Stimmung und vom OS unabhängig, braucht aber zuverlässige
    Stromversorgung. Kunden, die keinen Strom bekommen, zahlen nicht und beschweren sich.

### Dedicated

15. **In frühen Welten (1999 bis 2000) die beste Investition:** Ein billiger Celeron für $167
    bringt die Mindestmiete von $170 bis $200 pro Monat. Er hat sich nach etwa einem Spielmonat
    bezahlt (Strom gegenrechnen).
16. **Storage nicht vergessen:** 1999 zahlt Dedicated $5 pro GB und Monat. Jede Platte im
    Dedicated-Server ist bares Geld. Ab 2006 lohnt sich das kaum noch.
17. **Spät im Spiel Datacenter-Hardware:** ×3 auf das Compute. Ab 2009 liegen schwache Server nur
    bei $30 Mindestmiete, dann zählt rohes Compute.
18. Das OS zählt mit (Hypervisor ×1,18). Lizenzkosten pro Rack gegenrechnen: Windows Server 2008
    $175/Monat, ESXi $145/Monat. Linux kostet nichts.

### Aufträge (Add-on)

19. **Hosting-Aufträge zahlen 40 % des Listenpreises pro reserviertem Platz.** Regulärer
    Shared-Web-Umsatz bringt nur 10 %. Freie Plätze, die der Markt ohnehin nicht füllt, sind mit
    Aufträgen also gut genutzt. Achtung: Strafe und Reputation, wenn die Plätze nicht frei bleiben.
20. *(Preview)* Lieferaufträge bezahlen Kabel mit dem 2,5-fachen und Switches mit dem 1,25-fachen
    Preis. Mit eigener Produktion ist das ein netter Nebenverdienst.

## 6. Für spätere Balance (noch nichts geändert)

Die Ursachen für die geringen Einnahmen, nach Wirkung sortiert:

1. **Marktgleichgewicht pro Produkt.** `SHARED_SIGNUPS` liegt bei 20 bis 48 Bestellungen pro
   Monat, der Churn bei etwa 3 %. Hardware-Ausbau bringt darüber hinaus nichts.
2. **Langsamer Zulauf.** Das Gleichgewicht ist erst nach 2 bis 5 Spieljahren erreicht.
3. **Marge von 10 %** bei Shared Web und 15 % bei Mail.
4. **$30 Lizenz pro Server**, die bei gleichmäßiger Verteilung viele Server auf $0 drückt.
5. **Preisverfall nach 2004**, besonders bei Shared ($28 → $7).

Ansatzpunkte, ohne den Base-Mod zu ändern:

- `economy.hostingIncomeMultiplier` in der Data-Center-Config (wirkt auf alles gleich)
- `contractPayMultiplier` und Auftragsgrößen im Add-on
- mehr Aufträge bzw. Aufträge, die Kundenzulauf als Belohnung geben
