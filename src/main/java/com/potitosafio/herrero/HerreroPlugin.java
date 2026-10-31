package com.potitosafio.herrero;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class HerreroPlugin extends JavaPlugin implements Listener {
    private static final int MODEL = 100101;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); getLogger().info("Martillo del Herrero activado"); }
    private boolean hammer(ItemStack item) {
        return item != null && item.getType() == Material.MACE && item.hasItemMeta()
            && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == MODEL
            && item.getItemMeta().isUnbreakable();
    }
    private boolean ready(Player p) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < 1000) return false;
        return true;
    }
    private boolean charge(Player p) {
        int level = p.getLevel();
        float progress = p.getExp();
        int perLevel = level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7;
        // Consume exactamente un punto de experiencia, sin quitar un nivel entero.
        if (level == 0 && progress * perLevel < 0.999f) return false;
        if (progress * perLevel >= 0.999f) {
            p.setExp(Math.max(0f, (progress * perLevel - 1f) / perLevel));
        } else {
            int previous = level - 1;
            if (previous < 0) return false;
            p.setLevel(previous);
            int prevRequired = previous >= 30 ? 9 * previous - 158 : previous >= 15 ? 5 * previous - 38 : 2 * previous + 7;
            p.setExp((prevRequired - 1f) / prevRequired);
        }
        cooldown.put(p.getUniqueId(), System.currentTimeMillis());
        return true;
    }
    private boolean repair(ItemStack item, int amount) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable)) return false;
        Damageable meta = (Damageable) item.getItemMeta();
        if (item.getType().getMaxDurability() <= 0 || !meta.hasDamage() || meta.getDamage() <= 0) return false;
        meta.setDamage(Math.max(0, meta.getDamage() - amount));
        item.setItemMeta(meta);
        return true;
    }
    private void feedback(Player p, String msg) {
        p.sendActionBar(ChatColor.GREEN + msg);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0,1,0), 8, 0.35,0.5,0.35);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.isSneaking() || !hammer(p.getInventory().getItemInOffHand())) return;
        event.setCancelled(true);
        ItemStack target = p.getInventory().getItemInMainHand();
        if (target == null || target.getType().isAir() || hammer(target) || !(target.getItemMeta() instanceof Damageable)) return;
        Damageable d = (Damageable) target.getItemMeta();
        if (d.getDamage() <= 0) return;
        if (!ready(p)) return;
        if (!charge(p)) { p.sendActionBar(ChatColor.RED + "Necesitas experiencia para reparar"); return; }
        if (repair(target, 10)) { p.getInventory().setItemInMainHand(target); feedback(p, "✦ Objeto reparado +10 ✦"); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player p = (Player) event.getDamager();
        if (!hammer(p.getInventory().getItemInMainHand())) return;
        event.setDamage(0.0);
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player) || !event.getEntity().getWorld().getPVP()) return;
        Player ally = (Player) event.getEntity();
        if (!ready(p)) return;
        boolean damaged = false;
        for (ItemStack piece : ally.getInventory().getArmorContents()) {
            if (piece != null && piece.hasItemMeta() && piece.getItemMeta() instanceof Damageable && ((Damageable)piece.getItemMeta()).getDamage() > 0) { damaged = true; break; }
        }
        if (!damaged) return;
        if (!charge(p)) { p.sendActionBar(ChatColor.RED + "Necesitas experiencia para reparar"); return; }
        ItemStack[] armor = ally.getInventory().getArmorContents();
        for (ItemStack piece : armor) repair(piece, 2);
        ally.getInventory().setArmorContents(armor);
        feedback(p, "✦ Armadura aliada reparada +2 ✦");
        feedback(ally, "✦ Tu armadura ha sido reparada ✦");
    }
}
