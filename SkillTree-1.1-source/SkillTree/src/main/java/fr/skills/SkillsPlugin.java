package fr.skills;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * SkillTree 1.1 - arbre de compétences sur 2 pages.
 *
 * Page 1 : Vitesse, Force, Résistance, Camouflage.
 * Page 2 : Marchand (ex-Fermier), Expérience, Sanguinaire, Nécromancien.
 */
public class SkillsPlugin extends JavaPlugin implements Listener {

    // ------------------------------------------------------------------ constantes (inchangées)
    static final int POINTS_PLAYER_KILL = 4;
    static final int MOBS_PER_REWARD = 150;
    static final int POINTS_MOBS = 2;
    static final int CROPS_PER_REWARD = 500;
    static final int POINTS_CROPS = 3;
    static final int ORES_PER_REWARD = 25;
    static final int POINTS_ORES = 4;
    static final int WITHER_CHANCE_PERCENT = 10;
    static final int WITHER_SECONDS = 5;
    static final int DASH_MAX_USES = 3;
    static final int DASH_WINDOW_SECONDS = 30;
    static final long DASH_DELAY_MS = 600L;
    static final double BONUS_HEALTH = 6.0;

    // ------------------------------------------------------------------ données statiques
    static SkillsPlugin I;

    static final Set<Material> ORES = EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS);
    static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS);
    static final List<Node> NODES = new ArrayList<>();
    static final Set<PotionEffectType> MANAGED = new HashSet<>();
    static final Node N_DASH;
    static final Node N_WITHER;
    static final Node N_CAMO;

    static final EquipmentSlot[] VISIBLE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };

    // ------------------------------------------------------------------ accès config (équilibrage)
    static double cfgD(String key, double def) {
        return I == null ? def : I.getConfig().getDouble(key, def);
    }

    static int cfgI(String key, int def) {
        return I == null ? def : I.getConfig().getInt(key, def);
    }

    static boolean cfgB(String key, boolean def) {
        return I == null ? def : I.getConfig().getBoolean(key, def);
    }

    static double xpMultOf(int t) {
        return cfgD("experience.multiplier." + t, new double[]{1.0, 1.2, 1.4, 1.7}[t]);
    }

    static int bottleCostOf(int t) {
        return cfgI("experience.bottle-cost." + t, t >= 3 ? 9 : 12);
    }

    static double bloodChanceOf(int t) {
        return cfgD("sanguinaire.chance." + t, new double[]{0, 12, 22, 32}[t]);
    }

    static double bloodStealOf(int t) {
        return cfgD("sanguinaire.steal." + t, new double[]{0, 1, 2, 3}[t]);
    }

    static int necroCooldownOf(int t) {
        return cfgI("necromancien.cooldown-seconds." + t, new int[]{0, 75, 90, 120}[t]);
    }

    static int necroDurationOf(int t) {
        return cfgI("necromancien.duration-seconds." + t, new int[]{0, 40, 50, 60}[t]);
    }

    static int camoDuration() {
        return cfgI("camouflage.duration-seconds", 45);
    }

    static int camoCooldown() {
        return cfgI("camouflage.cooldown-seconds", 180);
    }

    static long pct(double mult) {
        return Math.round((mult - 1.0) * 100.0);
    }

    // ------------------------------------------------------------------ types
    enum Path {
        SPEED("speed", "Voie de la Vitesse", Material.SUGAR, 1, 1, "Cours plus vite, fonce avec le dash."),
        FORCE("force", "Voie de la Force", Material.IRON_SWORD, 1, 3, "Frappe plus fort, empoisonne tes ennemis."),
        RESIST("resist", "Voie de la Résistance", Material.SHIELD, 1, 5, "Encaisse les coups, résiste au feu."),
        // id "farm" conservé pour ne pas casser les sauvegardes existantes
        MERCHANT("farm", "Voie du Marchand", Material.EMERALD, 2, 1, "Commerce mieux, travaille vite."),
        CAMO("camo", "Voie du Camouflage", Material.PHANTOM_MEMBRANE, 1, 7, "Deviens invisible, même ton équipement."),
        XP("xp", "Voie de l'Expérience", Material.EXPERIENCE_BOTTLE, 2, 3, "Gagne plus d'XP et mets-la en fiole."),
        BLOOD("blood", "Voie du Sanguinaire", Material.REDSTONE, 2, 5, "Vole la vie de ceux que tu frappes."),
        NECRO("necro", "Voie du Nécromancien", Material.SKELETON_SKULL, 2, 7, "Invoque une armée de morts-vivants.");

        final String id;
        final String label;
        final String desc;
        final Material icon;
        final int page;
        final int slot;

        Path(String id, String label, Material icon, int page, int slot, String desc) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.page = page;
            this.slot = slot;
            this.desc = desc;
        }

        static Path byId(String id) {
            for (Path p : values()) {
                if (p.id.equals(id)) return p;
            }
            return null;
        }

        static Path bySlot(int page, int slot) {
            for (Path p : values()) {
                if (p.page == page && p.slot == slot) return p;
            }
            return null;
        }
    }

    static final class Node {
        final String id;
        final String name;
        final String desc;
        final Path path;
        final int tier;
        final int cost;
        final int slot;
        final Material icon;
        final Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        boolean health;
        boolean wither;
        boolean dash;
        boolean wand;
        Supplier<String> dyn;

        Node(String id, Path path, int tier, int cost, Material icon, String name, String desc) {
            this.id = id;
            this.path = path;
            this.tier = tier;
            this.cost = cost;
            this.icon = icon;
            this.name = name;
            this.desc = desc;
            this.slot = 9 * tier + path.slot;
        }

        Node eff(PotionEffectType type, int amp) {
            effects.put(type, amp);
            return this;
        }

        Node health() {
            health = true;
            return this;
        }

        Node wither() {
            wither = true;
            return this;
        }

        Node dash() {
            dash = true;
            return this;
        }

        /** Ce palier débloque le sceptre. */
        Node wand() {
            wand = true;
            return this;
        }

        /** Description calculée à l'affichage (valeurs lues dans config.yml). */
        Node dyn(Supplier<String> s) {
            dyn = s;
            return this;
        }

        String describe() {
            return dyn != null ? dyn.get() : desc;
        }
    }

    static final class Data {
        int points;
        int kills;
        int playerKills;
        int crops;
        int ores;
        String path;
        String wand;
        final Set<String> owned = new HashSet<>();
        final Set<String> off = new HashSet<>();
    }

    static final class Menu implements InventoryHolder {
        Inventory inv;
        int page = 1;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class Minion {
        final UUID owner;
        final long expires;

        Minion(UUID owner, long expires) {
            this.owner = owner;
            this.expires = expires;
        }
    }

    // ------------------------------------------------------------------ définition de l'arbre
    static Node add(Node n) {
        NODES.add(n);
        MANAGED.addAll(n.effects.keySet());
        return n;
    }

    static {
        // --- Vitesse
        add(new Node("speed1", Path.SPEED, 1, 0, Material.FEATHER, "Vitesse I", "Effet Vitesse I permanent.")
                .eff(PotionEffectType.SPEED, 0));
        add(new Node("speed2", Path.SPEED, 2, 15, Material.SUGAR, "Vitesse II", "Effet Vitesse II permanent.")
                .eff(PotionEffectType.SPEED, 1));
        N_DASH = add(new Node("dash", Path.SPEED, 3, 30, Material.ENDER_PEARL, "Dash",
                "Clic droit avec le sceptre pour foncer vers l'avant.").dash().wand());

        // --- Force
        add(new Node("force1", Path.FORCE, 1, 5, Material.IRON_SWORD, "Force I", "Effet Force I permanent.")
                .eff(PotionEffectType.STRENGTH, 0));
        add(new Node("force2", Path.FORCE, 2, 20, Material.DIAMOND_SWORD, "Force II", "Effet Force II permanent.")
                .eff(PotionEffectType.STRENGTH, 1));
        N_WITHER = add(new Node("wither", Path.FORCE, 3, 30, Material.WITHER_SKELETON_SKULL, "Toucher du Wither",
                "10% de chance d'infliger Wither II (5s) quand tu frappes.").wither());

        // --- Résistance
        add(new Node("res1", Path.RESIST, 1, 0, Material.IRON_CHESTPLATE, "Résistance I",
                "Effet Résistance I permanent.").eff(PotionEffectType.RESISTANCE, 0));
        add(new Node("res2", Path.RESIST, 2, 18, Material.DIAMOND_CHESTPLATE, "Résistance II + Feu",
                "Résistance II et Résistance au feu I permanents.")
                .eff(PotionEffectType.RESISTANCE, 1).eff(PotionEffectType.FIRE_RESISTANCE, 0));
        add(new Node("res3", Path.RESIST, 3, 30, Material.GOLDEN_APPLE, "3 cœurs bonus",
                "Tu gagnes 3 cœurs de vie maximum.").health());

        // --- Marchand (ex-Fermier : mêmes effets, ids inchangés)
        add(new Node("farm1", Path.MERCHANT, 1, 0, Material.GOLD_NUGGET, "Marchand novice",
                "Effet Célérité I permanent.").eff(PotionEffectType.HASTE, 0));
        add(new Node("farm2", Path.MERCHANT, 2, 14, Material.GOLD_INGOT, "Marchand averti",
                "Célérité II et Héros du village III permanents.")
                .eff(PotionEffectType.HASTE, 1).eff(PotionEffectType.HERO_OF_THE_VILLAGE, 2));
        add(new Node("farm3", Path.MERCHANT, 3, 28, Material.HEART_OF_THE_SEA, "Grand Marchand",
                "Force du conduit et Héros du village V permanents.")
                .eff(PotionEffectType.CONDUIT_POWER, 0).eff(PotionEffectType.HERO_OF_THE_VILLAGE, 4));

        // --- Camouflage
        add(new Node("camo1", Path.CAMO, 1, 0, Material.LEATHER_BOOTS, "Discrétion",
                "Vision nocturne permanente.").eff(PotionEffectType.NIGHT_VISION, 0));
        add(new Node("camo2", Path.CAMO, 2, 18, Material.FEATHER, "Pas de velours",
                "Effet Vitesse I permanent.").eff(PotionEffectType.SPEED, 0));
        N_CAMO = add(new Node("camo3", Path.CAMO, 3, 30, Material.PHANTOM_MEMBRANE, "Manteau d'ombre", "")
                .wand().dyn(() -> "Clic droit avec le sceptre : invisibilité TOTALE (corps, armure, objets) pendant "
                        + camoDuration() + "s. Recharge : " + camoCooldown() + "s."));

        // --- Expérience
        add(new Node("xp1", Path.XP, 1, 0, Material.LAPIS_LAZULI, "Apprenti érudit", "")
                .dyn(() -> "+" + pct(xpMultOf(1)) + "% d'XP (mobs, minerais, pêche, fours, élevage)."));
        add(new Node("xp2", Path.XP, 2, 16, Material.ENCHANTING_TABLE, "Embouteilleur", "")
                .dyn(() -> "+" + pct(xpMultOf(2)) + "% d'XP. Tu peux mettre ton XP en fiole ("
                        + bottleCostOf(2) + " XP par fiole, fioles vides requises)."));
        add(new Node("xp3", Path.XP, 3, 30, Material.EXPERIENCE_BOTTLE, "Maître de l'expérience", "")
                .dyn(() -> "+" + pct(xpMultOf(3)) + "% d'XP. Mise en fiole améliorée ("
                        + bottleCostOf(3) + " XP par fiole)."));

        // --- Sanguinaire
        add(new Node("blood1", Path.BLOOD, 1, 5, Material.FERMENTED_SPIDER_EYE, "Soif de sang I", "")
                .dyn(() -> (int) bloodChanceOf(1) + "% de chance de voler jusqu'à " + bloodStealOf(1)
                        + " PV quand tu frappes."));
        add(new Node("blood2", Path.BLOOD, 2, 20, Material.REDSTONE_BLOCK, "Soif de sang II", "")
                .dyn(() -> (int) bloodChanceOf(2) + "% de chance de voler jusqu'à " + bloodStealOf(2)
                        + " PV quand tu frappes."));
        add(new Node("blood3", Path.BLOOD, 3, 30, Material.NETHERITE_SWORD, "Soif de sang III", "")
                .dyn(() -> (int) bloodChanceOf(3) + "% de chance de voler jusqu'à " + bloodStealOf(3)
                        + " PV quand tu frappes (réduit contre les joueurs)."));

        // --- Nécromancien (le sceptre est débloqué dès le palier I)
        add(new Node("necro1", Path.NECRO, 1, 5, Material.ROTTEN_FLESH, "Appel des morts", "")
                .wand().dyn(() -> "Sceptre : invoque 2 zombies pendant " + necroDurationOf(1)
                        + "s. Recharge : " + necroCooldownOf(1) + "s."));
        add(new Node("necro2", Path.NECRO, 2, 20, Material.BONE, "Légion squelettique", "")
                .dyn(() -> "Invoque 2 zombies renforcés + 1 squelette pendant " + necroDurationOf(2)
                        + "s. Recharge : " + necroCooldownOf(2) + "s."));
        add(new Node("necro3", Path.NECRO, 3, 30, Material.WITHER_SKELETON_SKULL, "Seigneur des morts", "")
                .dyn(() -> "Invoque 2 zombies d'élite, 1 squelette et 1 squelette wither pendant "
                        + necroDurationOf(3) + "s. Recharge : " + necroCooldownOf(3) + "s."));
    }

    static Node nodeBySlot(int page, int slot) {
        for (Node n : NODES) {
            if (n.path.page == page && n.slot == slot) return n;
        }
        return null;
    }

    static Node nodeOf(Path path, int tier) {
        for (Node n : NODES) {
            if (n.path == path && n.tier == tier) return n;
        }
        return null;
    }

    // ------------------------------------------------------------------ état
    private final Map<UUID, Data> data = new HashMap<>();
    private final Map<UUID, Deque<Long>> dashUses = new HashMap<>();
    private final Map<UUID, Long> pendingReset = new HashMap<>();
    private final Set<String> placed = new HashSet<>();
    private final Map<UUID, Long> camoUntil = new HashMap<>();
    private final Map<UUID, Long> camoReady = new HashMap<>();
    private final Map<UUID, Long> necroReady = new HashMap<>();
    private final Map<UUID, Minion> minions = new HashMap<>();

    private File dataFile;
    private File placedFile;
    private NamespacedKey wandKey;
    private NamespacedKey serialKey;
    private NamespacedKey healthKey;

    // ------------------------------------------------------------------ cycle de vie
    @Override
    public void onEnable() {
        I = this;
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        serialKey = new NamespacedKey(this, "wand_serial");
        healthKey = new NamespacedKey(this, "bonus_health");
        dataFile = new File(getDataFolder(), "data.yml");
        placedFile = new File(getDataFolder(), "placed.yml");
        load();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("skills") != null) {
            getCommand("skills").setExecutor(this);
        }
        // sauvegarde auto toutes les 5 minutes
        getServer().getScheduler().runTaskTimer(this, this::save, 6000L, 6000L);
        // effets permanents : chaque seconde
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                applyEffects(p);
            }
        }, 20L, 20L);
        // camouflage : on re-masque l'équipement très souvent
        getServer().getScheduler().runTaskTimer(this, this::tickCamo, 3L, 3L);
        // nécromancien : suivi / durée de vie des serviteurs
        getServer().getScheduler().runTaskTimer(this, this::tickMinions, 10L, 10L);
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(camoUntil.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) endCamo(p);
        }
        camoUntil.clear();
        for (UUID id : new ArrayList<>(minions.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        minions.clear();
        save();
    }

    // ------------------------------------------------------------------ sauvegarde
    private void load() {
        if (placedFile.exists()) {
            placed.addAll(YamlConfiguration.loadConfiguration(placedFile).getStringList("blocks"));
        }
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Data d = new Data();
                d.points = y.getInt(key + ".points");
                d.kills = y.getInt(key + ".kills");
                d.playerKills = y.getInt(key + ".playerKills");
                d.crops = y.getInt(key + ".crops");
                d.ores = y.getInt(key + ".ores");
                d.path = y.getString(key + ".path");
                d.wand = y.getString(key + ".wand");
                d.owned.addAll(y.getStringList(key + ".owned"));
                d.off.addAll(y.getStringList(key + ".off"));
                // ancien format : niveaux -> points
                ConfigurationSection lv = y.getConfigurationSection(key + ".levels");
                if (lv != null) {
                    for (String k : lv.getKeys(false)) {
                        int level = lv.getInt(k);
                        d.points += level * (level + 1) / 2;
                    }
                }
                data.put(id, d);
            } catch (Exception ignored) {
                // clé invalide : on ignore
            }
        }
    }

    private void save() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<UUID, Data> en : data.entrySet()) {
                String k = en.getKey().toString();
                Data d = en.getValue();
                y.set(k + ".points", d.points);
                y.set(k + ".kills", d.kills);
                y.set(k + ".playerKills", d.playerKills);
                y.set(k + ".crops", d.crops);
                y.set(k + ".ores", d.ores);
                y.set(k + ".path", d.path);
                y.set(k + ".wand", d.wand);
                y.set(k + ".owned", new ArrayList<>(d.owned));
                y.set(k + ".off", new ArrayList<>(d.off));
            }
            getDataFolder().mkdirs();
            y.save(dataFile);
            YamlConfiguration p = new YamlConfiguration();
            p.set("blocks", new ArrayList<>(placed));
            p.save(placedFile);
        } catch (IOException ex) {
            getLogger().warning("Impossible de sauvegarder les données : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ utilitaires
    private Data data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new Data());
    }

    private boolean active(Data d, Node n) {
        return d.owned.contains(n.id) && !d.off.contains(n.id);
    }

    /** Palier le plus haut actif d'une voie (0 = aucun). */
    private int tierOf(Data d, Path path) {
        int best = 0;
        for (Node n : NODES) {
            if (n.path == path && active(d, n) && n.tier > best) best = n.tier;
        }
        return best;
    }

    private boolean hasWandNode(Data d) {
        for (Node n : NODES) {
            if (n.wand && d.owned.contains(n.id)) return true;
        }
        return false;
    }

    static Component c(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore != null) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ sceptre
    private String wandAction(Data d) {
        Path p = d.path == null ? null : Path.byId(d.path);
        if (p == Path.SPEED) return "Dash (" + DASH_MAX_USES + " utilisations toutes les " + DASH_WINDOW_SECONDS + "s)";
        if (p == Path.CAMO) return "Camouflage (" + camoDuration() + "s d'invisibilité totale)";
        if (p == Path.NECRO) return "Invoquer des morts-vivants";
        return "Pouvoir de ta voie";
    }

    private ItemStack createWand(String serial, Data d) {
        ItemStack it = item(Material.BLAZE_ROD, "§6Sceptre de compétences",
                List.of("§7Clic droit : " + wandAction(d), "§8Lié à ton compte - non craftable."));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(serialKey, PersistentDataType.STRING, serial);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isWand(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private boolean isValidWand(Player p, ItemStack it) {
        if (!isWand(it)) return false;
        String serial = it.getItemMeta().getPersistentDataContainer().get(serialKey, PersistentDataType.STRING);
        return serial != null && serial.equals(data(p).wand);
    }

    private boolean hasValidWand(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isValidWand(p, it)) return true;
        }
        return isValidWand(p, p.getItemOnCursor());
    }

    private void giveWand(Player p) {
        Data d = data(p);
        if (!hasWandNode(d)) {
            p.sendMessage(c("§cLe sceptre se débloque avec : §eDash §c(Vitesse, palier III), §eManteau d'ombre "
                    + "§c(Camouflage, palier III) ou §eAppel des morts §c(Nécromancien, palier I)."));
            return;
        }
        if (hasValidWand(p)) {
            p.sendMessage(c("§eTu as déjà ton sceptre."));
            return;
        }
        String serial = UUID.randomUUID().toString();
        if (!p.getInventory().addItem(createWand(serial, d)).isEmpty()) {
            p.sendMessage(c("§cTon inventaire est plein."));
            return;
        }
        d.wand = serial;
        p.sendMessage(c("§6Tu as reçu ton Sceptre de compétences."));
    }

    private void removeWands(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWand(contents[i])) p.getInventory().setItem(i, null);
        }
    }

    // ------------------------------------------------------------------ effets permanents
    private void applyEffects(Player p) {
        Data d = data(p);
        Map<PotionEffectType, Integer> want = new HashMap<>();
        boolean wantHealth = false;
        for (Node n : NODES) {
            if (!active(d, n)) continue;
            for (Map.Entry<PotionEffectType, Integer> en : n.effects.entrySet()) {
                want.merge(en.getKey(), en.getValue(), Math::max);
            }
            if (n.health) wantHealth = true;
        }
        for (PotionEffectType type : MANAGED) {
            Integer amp = want.get(type);
            PotionEffect cur = p.getPotionEffect(type);
            // un effet "à nous" = infini + ambiant
            boolean ours = cur != null && cur.isInfinite() && cur.isAmbient();
            if (amp == null) {
                if (ours) p.removePotionEffect(type);
                continue;
            }
            if (ours && cur.getAmplifier() == amp) continue;
            if (ours) {
                p.removePotionEffect(type);
                cur = null;
            }
            if (cur != null && cur.getAmplifier() >= amp) continue; // l'effet du joueur est déjà plus fort
            p.addPotionEffect(new PotionEffect(type, -1, amp, true, false, true));
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            AttributeModifier existing = null;
            for (AttributeModifier m : max.getModifiers()) {
                if (healthKey.equals(m.getKey())) {
                    existing = m;
                    break;
                }
            }
            if (wantHealth && existing == null) {
                max.addTransientModifier(new AttributeModifier(healthKey, BONUS_HEALTH,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                p.setHealth(Math.min(max.getValue(), p.getHealth() + BONUS_HEALTH));
            } else if (!wantHealth && existing != null) {
                max.removeModifier(existing);
            }
        }
    }

    // ------------------------------------------------------------------ menu (2 pages)
    private void openMenu(Player p, int page) {
        Menu menu = new Menu();
        menu.page = page;
        menu.inv = Bukkit.createInventory(menu, 45, c("§8Arbre de compétences §7(" + page + "/2)"));
        fill(menu.inv, p, page);
        p.openInventory(menu.inv);
    }

    private void fill(Inventory inv, Player p, int page) {
        Data d = data(p);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        for (Path path : Path.values()) {
            if (path.page == page) inv.setItem(path.slot, pathItem(path, d));
        }
        for (Node n : NODES) {
            if (n.path.page == page) inv.setItem(n.slot, nodeItem(n, d));
        }
        // flèche de navigation
        if (page == 1) {
            inv.setItem(8, item(Material.ARROW, "§ePage suivante §7→", List.of(
                    "§7Marchand, Expérience,", "§7Sanguinaire et Nécromancien.")));
        } else {
            inv.setItem(0, item(Material.ARROW, "§e← §ePage précédente", List.of(
                    "§7Vitesse, Force, Résistance", "§7et Camouflage.")));
        }
        // sceptre
        if (hasWandNode(d)) {
            inv.setItem(36, item(Material.BLAZE_ROD, "§6Recevoir le sceptre", List.of(
                    "§7Clic droit avec le sceptre : " + wandAction(d) + ".", "§7Un seul sceptre valide par joueur.")));
        } else {
            inv.setItem(36, item(Material.BARRIER, "§8Sceptre verrouillé", List.of(
                    "§cDébloque Dash (Vitesse III), Manteau d'ombre", "§c(Camouflage III) ou Appel des morts (Nécro I).")));
        }
        // mise en fiole de l'XP (page 2, voie Expérience)
        if (page == 2 && Path.XP.id.equals(d.path)) {
            int tier = tierOf(d, Path.XP);
            if (tier >= 2) {
                inv.setItem(38, item(Material.EXPERIENCE_BOTTLE, "§aMettre mon XP en fiole", List.of(
                        "§7Clic : 1 fiole (" + bottleCostOf(tier) + " XP)",
                        "§7Shift+clic : jusqu'à 16 fioles",
                        "§8Il te faut des fioles vides (bouteilles en verre).")));
            } else {
                inv.setItem(38, item(Material.BARRIER, "§8Mise en fiole verrouillée", List.of(
                        "§cDébloque Embouteilleur (palier II).")));
            }
        }
        // infos
        inv.setItem(40, item(Material.NETHER_STAR, "§bPoints : §f" + d.points, List.of(
                "§7Comment gagner des points :",
                "§f- Joueur tué : §e+" + POINTS_PLAYER_KILL + " §7(tués : " + d.playerKills + ")",
                "§f- Monstres : §e+" + POINTS_MOBS + " §7tous les " + MOBS_PER_REWARD + " ("
                        + (d.kills % MOBS_PER_REWARD) + "/" + MOBS_PER_REWARD + ")",
                "§f- Cultures mûres : §e+" + POINTS_CROPS + " §7tous les " + CROPS_PER_REWARD + " ("
                        + (d.crops % CROPS_PER_REWARD) + "/" + CROPS_PER_REWARD + ")",
                "§f- Diamant/Émeraude/Netherite : §e+" + POINTS_ORES + " §7tous les " + ORES_PER_REWARD + " ("
                        + (d.ores % ORES_PER_REWARD) + "/" + ORES_PER_REWARD + ")")));
        inv.setItem(44, item(Material.TNT, "§cRéinitialiser ma progression", List.of(
                "§7Te permet de changer de voie.", "§cTu perds la moitié des points dépensés.",
                "§eClique deux fois pour confirmer.")));
    }

    private ItemStack pathItem(Path path, Data d) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + path.desc);
        lore.add("");
        String name;
        if (path.id.equals(d.path)) {
            name = "§a" + path.label + " §7(choisie)";
            lore.add("§aC'est ta voie actuelle.");
        } else if (d.path == null) {
            name = "§e" + path.label;
            lore.add("§aClic pour choisir cette voie");
            lore.add("§cUn seul choix possible !");
        } else {
            name = "§8" + path.label;
            lore.add("§cTu as déjà choisi une autre voie.");
        }
        return item(path.icon, name, lore);
    }

    private ItemStack nodeItem(Node n, Data d) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + n.describe());
        lore.add("");
        boolean chosen = d.path != null;
        boolean mine = n.path.id.equals(d.path);
        if (chosen && !mine) {
            lore.add("§cAutre voie.");
            return item(Material.BARRIER, "§8" + n.name, lore);
        }
        String cost = n.cost == 0 ? "§fCoût : §agratuit" : "§fCoût : §e" + n.cost + " points";
        if (!chosen) {
            lore.add(cost);
            lore.add("§7Choisis d'abord cette voie.");
            return item(n.icon, "§7" + n.name, lore);
        }
        if (d.owned.contains(n.id)) {
            boolean off = d.off.contains(n.id);
            lore.add(off ? "§cDésactivée" : "§aActive");
            lore.add("§eClic pour " + (off ? "activer" : "désactiver"));
            return item(n.icon, (off ? "§c[OFF] " : "§a[ON] ") + n.name, lore);
        }
        Node prev = n.tier > 1 ? nodeOf(n.path, n.tier - 1) : null;
        if (prev != null && !d.owned.contains(prev.id)) {
            lore.add("§cDébloque d'abord : §f" + prev.name);
            return item(Material.BARRIER, "§8" + n.name, lore);
        }
        lore.add(cost);
        lore.add(d.points >= n.cost ? "§aClic pour acheter" : "§cPas assez de points");
        return item(n.icon, "§e" + n.name, lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (menu.page == 1 && slot == 8) {
            Bukkit.getScheduler().runTask(this, () -> openMenu(p, 2));
            return;
        }
        if (menu.page == 2 && slot == 0) {
            Bukkit.getScheduler().runTask(this, () -> openMenu(p, 1));
            return;
        }
        Path path = Path.bySlot(menu.page, slot);
        Node node = nodeBySlot(menu.page, slot);
        if (path != null) choosePath(p, path);
        else if (node != null) clickNode(p, node);
        else if (slot == 36) giveWand(p);
        else if (slot == 38 && menu.page == 2) bottleXp(p, e.isShiftClick());
        else if (slot == 44) tryReset(p);
        applyEffects(p);
        fill(e.getView().getTopInventory(), p, menu.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void choosePath(Player p, Path path) {
        Data d = data(p);
        if (d.path != null) {
            p.sendMessage(c(path.id.equals(d.path) ? "§eC'est déjà ta voie."
                    : "§cTu as déjà choisi une voie. Réinitialise ta progression pour changer."));
            return;
        }
        d.path = path.id;
        for (Node n : NODES) {
            if (n.path == path && n.cost == 0) {
                d.owned.add(n.id);
                d.off.remove(n.id);
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(c("§aTu as choisi la §e" + path.label + "§a !"));
    }

    private void clickNode(Player p, Node n) {
        Data d = data(p);
        if (d.path == null) {
            p.sendMessage(c("§cChoisis d'abord une voie (ligne du haut)."));
            return;
        }
        if (!n.path.id.equals(d.path)) {
            p.sendMessage(c("§cTu as choisi une autre voie. Réinitialise ta progression pour changer."));
            return;
        }
        if (d.owned.contains(n.id)) {
            if (d.off.remove(n.id)) {
                p.sendMessage(c("§a" + n.name + " activée."));
            } else {
                d.off.add(n.id);
                p.sendMessage(c("§c" + n.name + " désactivée."));
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        Node prev = n.tier > 1 ? nodeOf(n.path, n.tier - 1) : null;
        if (prev != null && !d.owned.contains(prev.id)) {
            p.sendMessage(c("§cDébloque d'abord : " + prev.name));
            return;
        }
        if (d.points < n.cost) {
            p.sendMessage(c("§cIl te faut " + n.cost + " points (tu en as " + d.points + ")."));
            return;
        }
        d.points -= n.cost;
        d.owned.add(n.id);
        d.off.remove(n.id);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(c("§aCompétence débloquée : §e" + n.name));
    }

    private void resetPlayer(Player p, Data d) {
        endCamoState(p);
        despawnMinions(p.getUniqueId());
        d.owned.clear();
        d.off.clear();
        d.path = null;
        d.wand = null;
        removeWands(p);
    }

    private int spent(Data d) {
        int sum = 0;
        for (Node n : NODES) {
            if (d.owned.contains(n.id)) sum += n.cost;
        }
        return sum;
    }

    private void tryReset(Player p) {
        Data d = data(p);
        if (d.path == null && d.owned.isEmpty()) {
            p.sendMessage(c("§eTu n'as rien à réinitialiser."));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = pendingReset.get(p.getUniqueId());
        if (last == null || now - last > 10000L) {
            pendingReset.put(p.getUniqueId(), now);
            p.sendMessage(c("§cClique encore sur le TNT dans les 10 secondes pour confirmer "
                    + "(tu perds la moitié des points dépensés)."));
            return;
        }
        pendingReset.remove(p.getUniqueId());
        int spent = spent(d);
        int refund = spent / 2;
        d.points += refund;
        resetPlayer(p, d);
        p.sendMessage(c("§aProgression réinitialisée. §7Points rendus : §e" + refund
                + " §7(sur " + spent + " dépensés)."));
    }

    private void addPoints(Player p, Data d, int amount, String why) {
        d.points += amount;
        p.sendMessage(c("§b+" + amount + " points de compétence ! §7(" + why + ") Total : "
                + d.points + " - /skills"));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    // ------------------------------------------------------------------ gain de points + XP bonus
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        // serviteurs du nécromancien : aucun butin, aucun XP, aucun point
        if (minions.remove(dead.getUniqueId()) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;
        Data d = data(killer);
        if (dead instanceof Player) {
            d.playerKills++;
            addPoints(killer, d, POINTS_PLAYER_KILL, "joueur tué");
        } else {
            if (dead instanceof Enemy) {
                d.kills++;
                if (d.kills % MOBS_PER_REWARD == 0) addPoints(killer, d, POINTS_MOBS, MOBS_PER_REWARD + " monstres tués");
            }
            double mult = xpMult(killer);
            if (mult > 1.0) e.setDroppedExp(scaleXp(e.getDroppedExp(), mult));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (ORES.contains(e.getBlockPlaced().getType())) placed.add(key(e.getBlockPlaced()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (e.getExpToDrop() > 0) {
            double mult = xpMult(p);
            if (mult > 1.0) e.setExpToDrop(scaleXp(e.getExpToDrop(), mult));
        }
        Material type = b.getType();
        if (ORES.contains(type)) {
            if (placed.remove(key(b))) return; // bloc posé par un joueur : pas de point
            Data d = data(p);
            d.ores++;
            if (d.ores % ORES_PER_REWARD == 0) addPoints(p, d, POINTS_ORES, ORES_PER_REWARD + " minerais minés");
        } else if (CROPS.contains(type)) {
            BlockData bd = b.getBlockData();
            if (bd instanceof Ageable a && a.getAge() == a.getMaximumAge()) {
                Data d = data(p);
                d.crops++;
                if (d.crops % CROPS_PER_REWARD == 0) addPoints(p, d, POINTS_CROPS, CROPS_PER_REWARD + " cultures récoltées");
            }
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || e.getExpToDrop() <= 0) return;
        double mult = xpMult(e.getPlayer());
        if (mult > 1.0) e.setExpToDrop(scaleXp(e.getExpToDrop(), mult));
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent e) {
        if (e.getExpToDrop() <= 0) return;
        double mult = xpMult(e.getPlayer());
        if (mult > 1.0) e.setExpToDrop(scaleXp(e.getExpToDrop(), mult));
    }

    @EventHandler
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || e.getExperience() <= 0) return;
        double mult = xpMult(p);
        if (mult > 1.0) e.setExperience(scaleXp(e.getExperience(), mult));
    }

    // ------------------------------------------------------------------ expérience : multiplicateur + fioles
    private double xpMult(Player p) {
        int t = tierOf(data(p), Path.XP);
        return t <= 0 ? 1.0 : xpMultOf(t);
    }

    /** Arrondi aléatoire : 5 XP x 1.2 = 6 une fois sur cinq, sinon 6... (moyenne exacte). */
    private static int scaleXp(int base, double mult) {
        double v = base * mult;
        int f = (int) Math.floor(v);
        if (Math.random() < v - f) f++;
        return f;
    }

    private static int xpForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private static int xpToNext(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    private static int totalXp(Player p) {
        int l = p.getLevel();
        return xpForLevel(l) + Math.round(p.getExp() * xpToNext(l));
    }

    private static void setTotalXp(Player p, int total) {
        p.setLevel(0);
        p.setExp(0f);
        p.giveExp(Math.max(0, total));
    }

    private void bottleXp(Player p, boolean max) {
        Data d = data(p);
        int tier = tierOf(d, Path.XP);
        if (tier < 2) {
            p.sendMessage(c("§cDébloque d'abord §eEmbouteilleur §c(palier II de la Voie de l'Expérience)."));
            return;
        }
        int cost = bottleCostOf(tier);
        int total = totalXp(p);
        int empty = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.GLASS_BOTTLE) empty += it.getAmount();
        }
        if (empty <= 0) {
            p.sendMessage(c("§cIl te faut des fioles vides (bouteilles en verre)."));
            return;
        }
        int n = Math.min(max ? 16 : 1, Math.min(total / cost, empty));
        if (n <= 0) {
            p.sendMessage(c("§cPas assez d'XP : il en faut " + cost + " par fiole."));
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, n));
        setTotalXp(p, total - n * cost);
        Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, n));
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.3f);
        p.sendMessage(c("§a" + n + " fiole(s) d'expérience remplie(s) §7(-" + (n * cost) + " XP)."));
    }

    // ------------------------------------------------------------------ combat : wither, sanguinaire, serviteurs
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        dashUses.remove(p.getUniqueId());
        pendingReset.remove(p.getUniqueId());
        endCamoState(p);
        despawnMinions(p.getUniqueId());
        save();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        endCamoState(p);
        despawnMinions(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Entity victim = e.getEntity();
        Entity source = damager;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) source = shooter;

        // 1) un serviteur ne blesse jamais son maître, et le maître ne blesse pas ses serviteurs
        if (victim instanceof Player vp) {
            Minion m = minions.get(source.getUniqueId());
            if (m != null && m.owner.equals(vp.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }
        Minion vm = minions.get(victim.getUniqueId());
        if (vm != null && source instanceof Player sp && sp.getUniqueId().equals(vm.owner)) {
            e.setCancelled(true);
            return;
        }

        // 2) le maître est attaqué -> ses serviteurs ripostent
        if (victim instanceof Player vp && source instanceof LivingEntity att && !att.equals(vp)) {
            assist(vp, att);
        }
        // 3) le maître frappe -> ses serviteurs l'aident
        if (source instanceof Player sp && victim instanceof LivingEntity lv) {
            assist(sp, lv);
        }

        // 4) pouvoirs au corps à corps
        if (!(damager instanceof Player p) || !(victim instanceof LivingEntity target)) return;
        Data d = data(p);
        if (active(d, N_WITHER) && Math.random() * 100.0 < WITHER_CHANCE_PERCENT) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, WITHER_SECONDS * 20, 1));
        }
        int bt = tierOf(d, Path.BLOOD);
        if (bt > 0 && !(target instanceof ArmorStand) && e.getFinalDamage() > 0) {
            lifesteal(p, target, bt, e.getFinalDamage());
        }
        // camouflage : frapper un joueur te révèle (configurable)
        if (target instanceof Player && camoUntil.containsKey(p.getUniqueId())
                && cfgB("camouflage.break-on-pvp-attack", true)) {
            endCamoState(p);
            p.sendActionBar(c("§cTu as été révélé en attaquant !"));
        }
    }

    private void lifesteal(Player p, LivingEntity target, int tier, double finalDamage) {
        if (Math.random() * 100.0 >= bloodChanceOf(tier)) return;
        double steal = Math.min(bloodStealOf(tier), finalDamage * cfgD("sanguinaire.max-fraction-of-damage", 0.6));
        if (target instanceof Player) steal *= cfgD("sanguinaire.pvp-multiplier", 0.75);
        if (steal <= 0.05) return;
        final double wanted = steal;
        // une fois les dégâts du coup appliqués : on retire les PV à la cible (sans jamais la tuer) et on les donne au joueur
        Bukkit.getScheduler().runTask(this, () -> {
            if (!target.isValid() || target.isDead() || !p.isOnline() || p.isDead()) return;
            double cur = target.getHealth();
            double drain = Math.min(wanted, Math.max(0.0, cur - 0.5));
            if (drain <= 0.0) return;
            target.setHealth(cur - drain);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double cap = max != null ? max.getValue() : 20.0;
            p.setHealth(Math.min(cap, p.getHealth() + drain));
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 5);
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 3);
        });
    }

    // ------------------------------------------------------------------ sceptre : clic droit
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isWand(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!isValidWand(p, e.getItem())) {
            p.sendActionBar(c("§cCe sceptre n'est pas valide pour toi."));
            return;
        }
        Data d = data(p);
        Path path = d.path == null ? null : Path.byId(d.path);
        if (path == Path.SPEED) useDash(p, d);
        else if (path == Path.CAMO) useCamo(p, d);
        else if (path == Path.NECRO) useNecro(p, d);
        else p.sendActionBar(c("§cTon sceptre n'a aucun pouvoir pour ta voie."));
    }

    private void useDash(Player p, Data d) {
        if (!active(d, N_DASH)) {
            p.sendActionBar(c("§cDash non débloqué ou désactivé (/skills)"));
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> dq = dashUses.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        while (!dq.isEmpty() && now - dq.peekFirst() > DASH_WINDOW_SECONDS * 1000L) dq.pollFirst();
        if (!dq.isEmpty() && now - dq.peekLast() < DASH_DELAY_MS) return;
        if (dq.size() >= DASH_MAX_USES) {
            long wait = (dq.peekFirst() + DASH_WINDOW_SECONDS * 1000L - now) / 1000L + 1L;
            p.sendActionBar(c("§cDash rechargé dans " + wait + "s"));
            return;
        }
        dq.addLast(now);
        Vector v = p.getLocation().getDirection().multiply(1.8);
        v.setY(Math.max(v.getY(), 0.0) + 0.25);
        p.setVelocity(v);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.2f);
        p.sendActionBar(c("§aDash ! §7(" + (DASH_MAX_USES - dq.size()) + "/" + DASH_MAX_USES + " restants)"));
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isWand(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onFuel(FurnaceBurnEvent e) {
        if (isWand(e.getFuel())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ CAMOUFLAGE
    private void useCamo(Player p, Data d) {
        if (!active(d, N_CAMO)) {
            p.sendActionBar(c("§cManteau d'ombre non débloqué ou désactivé (/skills)"));
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = camoUntil.get(id);
        if (until != null && until > now) {
            p.sendActionBar(c("§7Tu es déjà invisible (" + ((until - now) / 1000L + 1L) + "s)"));
            return;
        }
        Long ready = camoReady.get(id);
        if (ready != null && ready > now) {
            p.sendActionBar(c("§cCamouflage rechargé dans " + ((ready - now) / 1000L + 1L) + "s"));
            return;
        }
        int dur = camoDuration();
        camoUntil.put(id, now + dur * 1000L);
        camoReady.put(id, now + camoCooldown() * 1000L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur * 20, 0, false, false, true));
        hideEquipment(p);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 25);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1f);
        p.sendActionBar(c("§5Tu disparais pendant " + dur + "s..."));
    }

    /** Envoie "rien" aux autres joueurs pour toutes les pièces visibles (armure, main, offhand). */
    private void hideEquipment(Player p) {
        ItemStack air = new ItemStack(Material.AIR);
        Location loc = p.getLocation();
        for (Player v : p.getWorld().getPlayers()) {
            if (v.equals(p) || v.getLocation().distanceSquared(loc) > 128.0 * 128.0) continue;
            for (EquipmentSlot s : VISIBLE_SLOTS) v.sendEquipmentChange(p, s, air);
        }
    }

    /** Remet l'équipement réel côté clients des autres joueurs. */
    private void restoreEquipment(Player p) {
        EntityEquipment eq = p.getEquipment();
        Location loc = p.getLocation();
        for (Player v : p.getWorld().getPlayers()) {
            if (v.equals(p) || v.getLocation().distanceSquared(loc) > 128.0 * 128.0) continue;
            for (EquipmentSlot s : VISIBLE_SLOTS) {
                ItemStack real = eq == null ? null : eq.getItem(s);
                v.sendEquipmentChange(p, s, real == null ? new ItemStack(Material.AIR) : real);
            }
        }
    }

    /** Fin du camouflage : retire l'effet et restaure l'équipement. */
    private void endCamo(Player p) {
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        restoreEquipment(p);
    }

    /** Arrête proprement un camouflage en cours (si il y en a un). */
    private void endCamoState(Player p) {
        if (camoUntil.remove(p.getUniqueId()) != null) endCamo(p);
    }

    private void tickCamo() {
        if (camoUntil.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = camoUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead() || en.getValue() <= now) {
                it.remove();
                if (p != null && p.isOnline()) endCamo(p);
                continue;
            }
            if (!p.hasPotionEffect(PotionEffectType.INVISIBILITY)) { // ex : lait bu -> on remet le reste du temps
                int ticks = (int) Math.max(1L, (en.getValue() - now) / 50L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));
            }
            hideEquipment(p);
        }
    }

    // ------------------------------------------------------------------ NÉCROMANCIEN
    private void useNecro(Player p, Data d) {
        int tier = tierOf(d, Path.NECRO);
        if (tier <= 0) {
            p.sendActionBar(c("§cAppel des morts non débloqué ou désactivé (/skills)"));
            return;
        }
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long ready = necroReady.get(id);
        if (ready != null && ready > now) {
            p.sendActionBar(c("§cInvocation rechargée dans " + ((ready - now) / 1000L + 1L) + "s"));
            return;
        }
        despawnMinions(id);
        int dur = necroDurationOf(tier);
        long expires = now + dur * 1000L;
        necroReady.put(id, now + necroCooldownOf(tier) * 1000L);

        int zombies = 2;
        int skeletons = tier >= 2 ? 1 : 0;
        int withers = tier >= 3 ? 1 : 0;
        int total = zombies + skeletons + withers;
        int index = 0;
        double[] zHp = {0, 24, 32, 40};
        double[] zDmg = {0, 4, 5, 6};
        double[] sHp = {0, 20, 26, 34};

        for (int i = 0; i < zombies; i++) {
            Location l = safeSpot(p, index++, total);
            Zombie z = spawnMinion(p, Zombie.class, l, zHp[tier], zDmg[tier], expires);
            z.setBaby(false);
            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                if (tier >= 2) eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                if (tier >= 3) {
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
                noDrops(eq);
            }
        }
        for (int i = 0; i < skeletons; i++) {
            Location l = safeSpot(p, index++, total);
            Skeleton s = spawnMinion(p, Skeleton.class, l, sHp[tier], 0, expires);
            EntityEquipment eq = s.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.BOW));
                noDrops(eq);
            }
        }
        for (int i = 0; i < withers; i++) {
            Location l = safeSpot(p, index++, total);
            WitherSkeleton w = spawnMinion(p, WitherSkeleton.class, l, 40.0, 6.0, expires);
            EntityEquipment eq = w.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                noDrops(eq);
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.8f);
        p.sendActionBar(c("§5Tes serviteurs répondent à l'appel (" + dur + "s)"));
    }

    private static void noDrops(EntityEquipment eq) {
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private Location safeSpot(Player p, int index, int total) {
        double angle = 2.0 * Math.PI * index / Math.max(1, total);
        Location base = p.getLocation();
        Location l = base.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
        if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable()) return l;
        return base;
    }

    private static void setAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private <T extends Mob> T spawnMinion(Player owner, Class<T> type, Location loc, double hp, double dmg,
                                          long expires) {
        T m = loc.getWorld().spawn(loc, type);
        setAttr(m, Attribute.MAX_HEALTH, hp);
        if (dmg > 0) setAttr(m, Attribute.ATTACK_DAMAGE, dmg);
        m.setHealth(hp);
        m.setPersistent(false);
        m.setRemoveWhenFarAway(true);
        m.setCanPickupItems(false);
        m.customName(c("§5Serviteur de " + owner.getName()));
        m.setCustomNameVisible(true);
        // pas de brûlure au soleil
        m.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0,
                false, false, false));
        minions.put(m.getUniqueId(), new Minion(owner.getUniqueId(), expires));
        loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 12);
        return m;
    }

    private void despawnMinions(UUID owner) {
        Iterator<Map.Entry<UUID, Minion>> it = minions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Minion> en = it.next();
            if (!en.getValue().owner.equals(owner)) continue;
            it.remove();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent != null) {
                ent.getWorld().spawnParticle(Particle.SOUL, ent.getLocation().add(0, 1, 0), 10);
                ent.remove();
            }
        }
    }

    /** Les serviteurs du joueur attaquent la cible (sauf le joueur lui-même et ses autres serviteurs). */
    private void assist(Player owner, LivingEntity target) {
        if (minions.isEmpty() || target.isDead() || !target.isValid()) return;
        if (target instanceof Player tp
                && (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR)) return;
        Minion tm = minions.get(target.getUniqueId());
        if (tm != null && tm.owner.equals(owner.getUniqueId())) return;
        for (Map.Entry<UUID, Minion> en : minions.entrySet()) {
            if (!en.getValue().owner.equals(owner.getUniqueId())) continue;
            Entity ent = Bukkit.getEntity(en.getKey());
            if (ent instanceof Mob mob && mob.getWorld().equals(target.getWorld())) mob.setTarget(target);
        }
    }

    /** Les serviteurs ne choisissent jamais seuls leur cible : seul notre code (reason CUSTOM) le fait. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionTarget(EntityTargetLivingEntityEvent e) {
        Minion m = minions.get(e.getEntity().getUniqueId());
        if (m == null || e.getTarget() == null) return;
        if (e.getReason() != EntityTargetEvent.TargetReason.CUSTOM) {
            e.setCancelled(true);
            return;
        }
        LivingEntity t = e.getTarget();
        Minion tm = minions.get(t.getUniqueId());
        if (t.getUniqueId().equals(m.owner) || (tm != null && tm.owner.equals(m.owner))) e.setCancelled(true);
    }

    private void tickMinions() {
        if (minions.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Minion>> it = minions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Minion> en = it.next();
            Minion m = en.getValue();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof Mob mob) || !mob.isValid()) {
                it.remove();
                continue;
            }
            Player owner = Bukkit.getPlayer(m.owner);
            if (owner == null || !owner.isOnline() || owner.isDead() || m.expires <= now
                    || !owner.getWorld().equals(mob.getWorld())) {
                it.remove();
                mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation().add(0, 1, 0), 10);
                mob.remove();
                continue;
            }
            LivingEntity tg = mob.getTarget();
            double ownerDist2 = mob.getLocation().distanceSquared(owner.getLocation());
            if (tg != null && (tg.isDead() || !tg.isValid()
                    || tg.getLocation().distanceSquared(owner.getLocation()) > 24.0 * 24.0)) {
                mob.setTarget(null);
                tg = null;
            }
            if (tg == null) {
                if (ownerDist2 > 28.0 * 28.0) {
                    mob.teleport(owner.getLocation());
                } else if (ownerDist2 > 5.0 * 5.0) {
                    mob.getPathfinder().moveTo(owner.getLocation(), 1.15);
                }
            }
        }
    }

    // ------------------------------------------------------------------ commandes
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            if (!sender.hasPermission("skills.admin")) {
                sender.sendMessage(c("§cTu n'as pas la permission."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(c("§c/skills give <joueur> <nombre> | /skills reset <joueur>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(c("§cJoueur introuvable."));
                return true;
            }
            Data d = data(target);
            if (args[0].equalsIgnoreCase("give")) {
                int amount;
                try {
                    amount = Integer.parseInt(args.length > 2 ? args[2] : "1");
                } catch (NumberFormatException ex) {
                    sender.sendMessage(c("§cNombre invalide."));
                    return true;
                }
                d.points += amount;
                sender.sendMessage(c("§a" + amount + " point(s) donné(s) à " + target.getName() + "."));
                target.sendMessage(c("§b+" + amount + " point(s) de compétence !"));
            } else {
                int refund = spent(d);
                d.points += refund;
                resetPlayer(target, d);
                applyEffects(target);
                sender.sendMessage(c("§aProgression de " + target.getName() + " réinitialisée (" + refund
                        + " points rendus)."));
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(c("§cCommande réservée aux joueurs."));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "wand" -> giveWand(p);
            case "points" -> {
                Data d = data(p);
                p.sendMessage(c("§bPoints : §f" + d.points + " §7| Joueurs : §f" + d.playerKills
                        + " §7| Monstres : §f" + (d.kills % MOBS_PER_REWARD) + "/" + MOBS_PER_REWARD
                        + " §7| Cultures : §f" + (d.crops % CROPS_PER_REWARD) + "/" + CROPS_PER_REWARD
                        + " §7| Minerais : §f" + (d.ores % ORES_PER_REWARD) + "/" + ORES_PER_REWARD));
            }
            default -> openMenu(p, 1);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("skills.admin");
        if (args.length == 1) {
            out.add("wand");
            out.add("points");
            if (admin) {
                out.add("give");
                out.add("reset");
            }
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
        } else if (args.length == 2 && admin
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
