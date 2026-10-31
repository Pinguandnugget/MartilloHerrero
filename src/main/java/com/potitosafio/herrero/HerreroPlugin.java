package com.potitosafio.herrero;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class HerreroPlugin extends JavaPlugin implements Listener {
    private static final int MODEL = 100101;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); getLogger().info("Martillo del Herrero activado"); }
    private boolean hammer(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == MODEL && meta.isUnbreakable();
    }
    private boolean ready(Player p) { return System.currentTimeMillis() - cooldown.getOrDefault(p.getUniqueId(), 0L) >= 1000; }
    private int xpToNext(int level) { return level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7; }
    private boolean charge(Player p) {
        int level = p.getLevel();
        float points = p.getExp() * xpToNext(level);
        if (level == 0 && points < 0.999f) return false;
        if (points >= 0.999f) p.setExp(Math.max(0f, (points - 1f) / xpToNext(level)));
        else { p.setLevel(level - 1); p.setExp((xpToNext(level - 1) - 1f) / xpToNext(level - 1)); }
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
        return true;
    }
    private boolean damaged(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof Damageable)) return false;
        return ((Damageable) item.getItemMeta()).getDamage() > 0;
    }
    private boolean repair(ItemStack item, int amount) {
        if (!damaged(item)) return false;
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(Math.max(0, meta.getDamage() - amount));
        item.setItemMeta(meta);
        return true;
    }
    private void feedback(Player p, String msg) {
        p.sendActionBar(ChatColor.GREEN + msg);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 8, .35, .5, .35);
    }
    private void selfRepair(Player p) {
        if (!ready(p)) return;
        ItemStack target = p.getInventory().getItemInMainHand();
        if (hammer(target) || !damaged(target)) { p.sendActionBar(ChatColor.YELLOW + "Sostén un objeto dañado en la mano principal"); return; }
        if (!charge(p)) { p.sendActionBar(ChatColor.RED + "Necesitas experiencia para reparar"); return; }
        if (repair(target, 10)) { p.getInventory().setItemInMainHand(target); feedback(p, "✦ Objeto reparado +10 ✦"); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.isSneaking() || !hammer(p.getInventory().getItemInOffHand())) return;
        event.setCancelled(true);
        selfRepair(p);
    }
    // Alternativa fiable cuando otro plugin o el objeto principal consume el clic derecho: agacharse + F.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!p.isSneaking() || !hammer(p.getInventory().getItemInOffHand())) return;
        event.setCancelled(true);
        selfRepair(p);
    }
    private void allyRepair(Player p, Player ally) {
        if (!ready(p)) return;
        ItemStack[] armor = ally.getInventory().getArmorContents();
        boolean hasDamage = false;
        for (ItemStack piece : armor) if (damaged(piece)) { hasDamage = true; break; }
        if (!hasDamage) { p.sendActionBar(ChatColor.YELLOW + "La armadura ya está reparada"); return; }
        if (!charge(p)) { p.sendActionBar(ChatColor.RED + "Necesitas experiencia para reparar"); return; }
        for (ItemStack piece : armor) repair(piece, 2);
        ally.getInventory().setArmorContents(armor);
        feedback(p, "✦ Armadura aliada reparada +2 ✦");
        feedback(ally, "✦ Tu armadura ha sido reparada ✦");
    }
    // Clic derecho en un aliado: funciona aunque el PvP esté desactivado.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAllyClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player)) return;
        Player p = event.getPlayer();
        if (!hammer(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        allyRepair(p, (Player) event.getRightClicked());
    }
    // Golpes: nunca causan daño. Reparar usando clic derecho evita depender de las protecciones PvP.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player p = (Player) event.getDamager();
        if (!hammer(p.getInventory().getItemInMainHand())) return;
        event.setDamage(0.0);
        event.setCancelled(true);
        if (event.getEntity() instanceof Player) allyRepair(p, (Player) event.getEntity());
    }
}
