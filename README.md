# BackpackSystem

Plugin pro Paper/Spigot 1.20+ přidávající hráčům systém osobních batohů
(dodatečná úložná místa mimo klasický inventář), s rozlišením počtu
dostupných batohů podle permission skupiny.

## Funkce

- `/backpack <číslo>` (alias `/bp`) — otevře konkrétní batoh hráče
- `/backpack list` — vypíše seznam dostupných batohů a jejich stav (otevřen/zavřen)
- `/backpack reload` — znovu načte `config.yml` (vyžaduje `backpack.admin`)
- `/backpack give <hráč> <číslo>` — otevře batoh jiného hráče (vyžaduje `backpack.admin`)
- Skupiny s rozdílným počtem batohů: **default** (1), **VIP** (3), **PREMIUM** (5),
  **STAFF** (10), **ADMIN** (999) — vše konfigurovatelné v `config.yml`
- Vlastní hláška při překročení limitu i při neplatném čísle
- Cooldown mezi otevřeními (výchozí 2 s), nastavitelný v `config.yml`
- Automatické ukládání dat: při zavření inventáře, při odchodu hráče ze
  serveru a periodicky podle `storage.auto-save` (výchozí 300 s), i při
  vypnutí serveru
- Data se ukládají do `plugins/BackpackSystem/backpacks/<UUID>_<index>.dat`
  pomocí `BukkitObjectOutputStream` (bezpečná serializace `ItemStack`)
- Volitelné zálohování (`.bak` soubor) před každým přepsáním
- Jednoduchá ochrana proti duplikaci itemů (zámky nad souborem po dobu
  ukládání/načítání + ukládání ihned po zavření okna)
- In-memory cache (`HashMap`/`LinkedHashMap`) s nastavitelnou maximální
  velikostí a jednoduchým LRU vytěsňováním (nejstarší nepoužívaný batoh
  se před vytěsněním z cache vždy uloží na disk)

## Struktura projektu

```
cz.tvojepackage.backpack/
├── BackpackSystem.java          # Hlavní třída (onEnable/onDisable, autosave)
├── managers/
│   ├── BackpackManager.java     # Cache, ukládání/načítání, cooldown
│   ├── ConfigManager.java       # Práce s config.yml
│   └── GroupManager.java        # Skupiny a permissions
├── commands/
│   └── BackpackCommand.java     # /backpack a jeho podpříkazy + tab-completion
├── listeners/
│   └── BackpackListener.java    # InventoryCloseEvent, PlayerQuitEvent
└── utils/
    ├── BackpackUtils.java       # Barvy, placeholdery, parsování
    └── BackpackHolder.java      # InventoryHolder nesoucí UUID + index batohu
```

## Sestavení (build)

Projekt používá Maven a závisí na **Paper API 1.20.4**.

```bash
mvn clean package
```

Výsledný `.jar` soubor najdeš v `target/BackpackSystem-1.0.0.jar`
(díky `maven-shade-plugin` je to rovnou finální jar připravený na nahrání
do složky `plugins/` na serveru).

> Poznámka: Paper API je stažena z repozitáře `https://repo.papermc.io`,
> pro build je tedy potřeba připojení k internetu.

## Instalace

1. Zkopíruj `BackpackSystem-1.0.0.jar` do složky `plugins/` na serveru.
2. Restartuj nebo spusť `/reload` na serveru (doporučen čistý restart).
3. Uprav `plugins/BackpackSystem/config.yml` podle potřeby a proveď
   `/backpack reload`.
4. Přiřaď hráčům odpovídající permissions (`backpack.vip`,
   `backpack.premium`, `backpack.staff`, `backpack.admin`) přes svůj
   permission plugin (LuckPerms apod.).

## Permissions

| Permission          | Popis                                   | Výchozí |
|----------------------|------------------------------------------|---------|
| `backpack.use`       | Použití základního příkazu               | true    |
| `backpack.default`   | Přístup k 1 batohu                       | true    |
| `backpack.vip`       | Přístup k batohům #1–3                   | op      |
| `backpack.premium`   | Přístup k batohům #1–5                   | op      |
| `backpack.staff`     | Přístup k batohům #1–10                  | op      |
| `backpack.admin`     | Neomezený přístup + `reload` a `give`    | op      |

## Konfigurace

Všechny texty, počty batohů, velikost inventáře, cooldown, interval
automatického ukládání i chování cache lze upravit v `config.yml` —
viz komentáře přímo v souboru.

## Poznámky k implementaci

- Skupina hráče se určuje tak, že se všechny nakonfigurované skupiny
  seřadí sestupně podle počtu batohů a hráči se přiřadí první skupina,
  jejíž permission vlastní — takže není nutné řešit přesné dědění
  permissions mezi skupinami.
- Otevřený batoh nese vlastní `InventoryHolder` (`BackpackHolder`) s UUID
  vlastníka a indexem, takže se při zavření okna vždy uloží přesně ten
  správný soubor, bez nutnosti cokoliv parsovat z titulku okna.
- Automatické ukládání i ukládání po zavření okna běží asynchronně, aby
  nezatěžovalo hlavní vlákno serveru; přístup do sdílené cache je
  synchronizovaný, aby nedocházelo k závodním podmínkám.
