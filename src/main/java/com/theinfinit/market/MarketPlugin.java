
package com.theinfinit.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MarketPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String MARKET_TITLE = "§8THE INFINIT | Mercado";
    private static final String CONFIRM_TITLE = "§8Confirmar troca";
    private static final int[] MARKET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Map<String, Offer> offers = new HashMap<>();
    private final Map<UUID, String> pending = new HashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Long> bounties = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadOffers();
        loadEconomyAndBounties();
        getConfig().addDefault("economy.starting-balance", 0L);
        getConfig().addDefault("bounty.kills-to-become-wanted", 4);
        getConfig().addDefault("bounty.reward-per-kill", 100L);
        getConfig().addDefault("bounty.max-reward", 1000000000L);
        getConfig().options().copyDefaults(true);
        saveConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("loja") != null) {
            getCommand("loja").setExecutor(this);
            getCommand("loja").setTabCompleter(this);
        }
        if (getCommand("store") != null) {
            getCommand("store").setExecutor(this);
            getCommand("store").setTabCompleter(this);
        }
        if (getCommand("ic") != null) {
            getCommand("ic").setExecutor(this);
            getCommand("ic").setTabCompleter(this);
        }
        if (getCommand("procurados") != null) {
            getCommand("procurados").setExecutor(this);
        }

        getLogger().info("THE INFINIT MARKET carregado: " + offers.size() + " oferta(s).");
    }

    @Override
    public void onDisable() {
        saveOffers();
        saveEconomyAndBounties();
        pending.clear();
    }

    private void loadOffers() {
        offers.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("offers");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "offers." + id + ".";
            String sellerText = getConfig().getString(path + "seller");
            ItemStack offered = getConfig().getItemStack(path + "offered");
            ItemStack wanted = getConfig().getItemStack(path + "wanted");
            long priceIc = getConfig().getLong(path + "price-ic", 0L);
            String sellerName = getConfig().getString(path + "seller-name", "Jogador");

            if (sellerText == null || offered == null || offered.getType().isAir()) continue;
            if (priceIc <= 0 && (wanted == null || wanted.getType().isAir())) continue;

            try {
                offers.put(id, new Offer(id, UUID.fromString(sellerText), sellerName, offered, wanted, priceIc));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Oferta ignorada por UUID inválido: " + id);
            }
        }
    }

    private void saveOffers() {
        getConfig().set("offers", null);
        for (Offer offer : offers.values()) {
            String path = "offers." + offer.id + ".";
            getConfig().set(path + "seller", offer.seller.toString());
            getConfig().set(path + "seller-name", offer.sellerName);
            getConfig().set(path + "offered", offer.offered);
            getConfig().set(path + "wanted", offer.wanted);
            getConfig().set(path + "price-ic", offer.priceIc);
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("loja")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Este comando só pode ser usado dentro do jogo.");
                return true;
            }
            openMarket(player);
            return true;
        }

        if (name.equals("ic")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Este comando só pode ser usado dentro do jogo.");
                return true;
            }
            handleIc(player, args);
            return true;
        }

        if (name.equals("procurados")) {
            showWanted(sender);
            return true;
        }

        if (!name.equals("store")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado dentro do jogo.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("ajuda")) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "criar", "create" -> createOffer(player, args);
            case "criaric", "createic" -> createIcOffer(player, args);
            case "minhas", "mine" -> listOwnOffers(player);
            case "cancelar", "cancel" -> cancelOffer(player, args);
            case "coletar", "claim" -> collectClaims(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§lTHE INFINIT MARKET §7| Ajuda");
        player.sendMessage("§e/loja §7- abre o mercado público");
        player.sendMessage("§e/store criar <qtd-pagamento> <material> §7- anuncia por itens");
        player.sendMessage("§e/store criaric <preço> §7- anuncia por Infinit Coin (IC)");
        player.sendMessage("§e/store minhas §7- mostra suas ofertas");
        player.sendMessage("§e/store cancelar <id> §7- cancela uma oferta sua");
        player.sendMessage("§e/store coletar §7- recebe itens de vendas/cancelamentos");
        player.sendMessage("§e/ic saldo §7- mostra seu saldo de Infinit Coin");
        player.sendMessage("§e/ic pagar <jogador> <valor> §7- transfere IC");
        player.sendMessage("§e/procurados §7- mostra jogadores com recompensa");
        player.sendMessage("§8Exemplo: segure 3 diamantes e use /store criar 16 iron_ingot");
    }

    private void createOffer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUso: /store criar <quantidade-pagamento> <material>");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cSegure na mão o item que deseja anunciar.");
            return;
        }

        int amount;
        Material wantedType;
        try {
            amount = Integer.parseInt(args[1]);
            wantedType = Material.matchMaterial(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cQuantidade inválida.");
            return;
        }

        if (amount < 1 || amount > 64 || wantedType == null || wantedType.isAir() || !wantedType.isItem()) {
            player.sendMessage("§cUse uma quantidade de 1 a 64 e um material válido.");
            return;
        }

        if (wantedType == hand.getType()) {
            player.sendMessage("§cO item oferecido e o pagamento não podem ser iguais.");
            return;
        }

        ItemStack offered = hand.clone();
        offered.setAmount(1);
        int offerAmount = hand.getAmount();
        offered.setAmount(offerAmount);
        ItemStack wanted = new ItemStack(wantedType, amount);
        String id = UUID.randomUUID().toString().substring(0, 8);

        hand.setAmount(0);
        offers.put(id, new Offer(id, player.getUniqueId(), player.getName(), offered, wanted));
        saveOffers();
        player.sendMessage("§aOferta criada! §7ID: §f" + id);
        player.sendMessage("§7Você anunciou §f" + describe(offered) + " §7por §f" + describe(wanted) + "§7.");
    }

    private void createIcOffer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /store criaric <preço-em-IC>");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage("§cSegure na mão o item que deseja anunciar.");
            return;
        }

        long price;
        try {
            price = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cPreço inválido.");
            return;
        }

        if (price <= 0 || price > 1000000000L) {
            player.sendMessage("§cO preço deve ser entre 1 e 1.000.000.000 IC.");
            return;
        }

        ItemStack offered = hand.clone();
        String id = UUID.randomUUID().toString().substring(0, 8);
        hand.setAmount(0);
        offers.put(id, new Offer(id, player.getUniqueId(), player.getName(), offered, null, price));
        saveOffers();
        player.sendMessage("§aOferta criada! §7ID: §f" + id + " §7| Preço: §e" + price + " IC");
    }

    private void openMarket(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MARKET_TITLE);
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, "§r");

        for (int i = 0; i < inv.getSize(); i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, pane);
        }

        ItemStack info = named(Material.BOOK, "§eComo comprar");
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setLore(List.of("§7Clique em uma oferta para ver", "§7a confirmação da troca.", "§aVerde confirma. §cVermelho cancela."));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        int index = 0;
        for (Offer offer : offers.values()) {
            if (index >= MARKET_SLOTS.length) break;
            ItemStack icon = offer.offered.clone();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Vendedor: §f" + offer.sellerName);
            lore.add(offer.priceIc > 0
                    ? "§7Preço: §e" + offer.priceIc + " IC"
                    : "§7Pede: §e" + describe(offer.wanted));
            lore.add("§8ID: " + offer.id);
            lore.add("§aClique para ver a troca");
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(MARKET_SLOTS[index++], icon);
        }

        if (offers.isEmpty()) inv.setItem(22, named(Material.BARRIER, "§cNão há ofertas no momento"));
        player.openInventory(inv);
    }

    private void openConfirmation(Player player, Offer offer) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);
        ItemStack offered = offer.offered.clone();
        ItemMeta offeredMeta = offered.getItemMeta();
        offeredMeta.setDisplayName("§aVocê recebe");
        offeredMeta.setLore(List.of("§f" + describe(offer.offered), "§7Vendedor: §f" + offer.sellerName));
        offered.setItemMeta(offeredMeta);
        inv.setItem(11, offered);

        ItemStack payment;
        if (offer.priceIc > 0) {
            payment = named(Material.PAPER, "§eVocê paga: " + offer.priceIc + " IC");
            ItemMeta paymentMeta = payment.getItemMeta();
            paymentMeta.setLore(List.of("§7Moeda: §eInfinit Coin (IC)"));
            payment.setItemMeta(paymentMeta);
        } else {
            payment = offer.wanted.clone();
            ItemMeta paymentMeta = payment.getItemMeta();
            paymentMeta.setDisplayName("§cVocê paga");
            paymentMeta.setLore(List.of("§f" + describe(offer.wanted)));
            payment.setItemMeta(paymentMeta);
        }
        inv.setItem(15, payment);

        ItemStack confirm = named(Material.LIME_DYE, "§a§lCONFIRMAR COMPRA");
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setLore(List.of("§7Clique para concluir a troca."));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(13, confirm);

        ItemStack cancel = named(Material.RED_DYE, "§c§lCANCELAR");
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setLore(List.of("§7Clique para desistir da compra."));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(22, cancel);

        pending.put(player.getUniqueId(), offer.id);
        player.openInventory(inv);
    }

    @EventHandler
    public void onMarketClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String title = e.getView().getTitle();

        if (MARKET_TITLE.equals(title)) {
            e.setCancelled(true);
            if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
            int slot = e.getRawSlot();

            for (int i = 0; i < MARKET_SLOTS.length; i++) {
                if (MARKET_SLOTS[i] != slot) continue;
                if (i >= offers.size()) return;
                Offer offer = new ArrayList<>(offers.values()).get(i);

                if (offer.seller.equals(player.getUniqueId())) {
                    player.sendMessage("§cVocê não pode comprar sua própria oferta.");
                    return;
                }
                openConfirmation(player, offer);
                return;
            }
            return;
        }

        if (CONFIRM_TITLE.equals(title)) {
            e.setCancelled(true);
            if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
            String id = pending.get(player.getUniqueId());
            if (id == null) return;

            if (e.getRawSlot() == 13) {
                purchase(player, id);
            } else if (e.getRawSlot() == 22) {
                pending.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§cCompra cancelada!");
            }
        }
    }

    private void purchase(Player buyer, String id) {
        Offer offer = offers.get(id);
        if (offer == null) {
            pending.remove(buyer.getUniqueId());
            buyer.closeInventory();
            buyer.sendMessage("§cEssa oferta não existe mais.");
            return;
        }

        if (offer.seller.equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cVocê não pode comprar sua própria oferta.");
            return;
        }

        if (offer.priceIc > 0) {
            if (getBalance(buyer.getUniqueId()) < offer.priceIc) {
                buyer.sendMessage("§cSaldo insuficiente. Você precisa de " + offer.priceIc + " IC.");
                return;
            }
        } else if (!hasItems(buyer, offer.wanted)) {
            buyer.sendMessage("§cVocê não tem " + describe(offer.wanted) + " para pagar.");
            return;
        }

        if (offer.priceIc > 0) {
            balances.put(buyer.getUniqueId(), getBalance(buyer.getUniqueId()) - offer.priceIc);
            addBalance(offer.seller, offer.priceIc);
        } else {
            removeItems(buyer, offer.wanted);
            addClaim(offer.seller, offer.wanted);
        }

        Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(offer.offered.clone());
        for (ItemStack leftover : leftovers.values()) buyer.getWorld().dropItemNaturally(buyer.getLocation(), leftover);

        offers.remove(id);
        pending.remove(buyer.getUniqueId());
        saveOffers();
        buyer.closeInventory();
        buyer.sendMessage("§aCompra concluída! Você recebeu " + describe(offer.offered)
                + (offer.priceIc > 0 ? " por " + offer.priceIc + " IC." : " por " + describe(offer.wanted) + "."));

        OfflinePlayer seller = Bukkit.getOfflinePlayer(offer.seller);
        if (seller.isOnline() && seller.getPlayer() != null) {
            if (offer.priceIc > 0) {
                seller.getPlayer().sendMessage("§aSua oferta " + id + " foi comprada! Você recebeu " + offer.priceIc + " IC.");
            } else {
                seller.getPlayer().sendMessage("§aSua oferta " + id + " foi comprada! Use §f/store coletar §apara receber os itens do pagamento.");
            }
        }
    }

    private boolean hasItems(Player player, ItemStack wanted) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != wanted.getType()) continue;
            if (item.isSimilar(wanted)) count += item.getAmount();
        }
        return count >= wanted.getAmount();
    }

    private void removeItems(Player player, ItemStack wanted) {
        int remaining = wanted.getAmount();
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(wanted)) continue;

            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private void addClaim(UUID owner, ItemStack item) {
        String path = "claims." + owner;
        List<ItemStack> items = new ArrayList<>();
        List<?> stored = getConfig().getList(path);

        if (stored != null) {
            for (Object value : stored) if (value instanceof ItemStack stack) items.add(stack);
        }

        items.add(item.clone());
        getConfig().set(path, items);
        saveConfig();
    }

    private void collectClaims(Player player) {
        String path = "claims." + player.getUniqueId();
        List<?> stored = getConfig().getList(path);

        if (stored == null || stored.isEmpty()) {
            player.sendMessage("§7Você não tem itens para coletar.");
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (Object value : stored) {
            if (!(value instanceof ItemStack stack)) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            remaining.addAll(leftovers.values());
        }

        if (remaining.isEmpty()) getConfig().set(path, null);
        else getConfig().set(path, remaining);
        saveConfig();
        player.sendMessage(remaining.isEmpty() ? "§aItens coletados!" : "§eSeu inventário encheu. O restante continua guardado.");
    }

    private void listOwnOffers(Player player) {
        boolean found = false;
        player.sendMessage("§6§lSuas ofertas:");

        for (Offer offer : offers.values()) {
            if (!offer.seller.equals(player.getUniqueId())) continue;
            found = true;
            player.sendMessage("§e" + offer.id + " §7| Oferece: §f" + describe(offer.offered)
                    + (offer.priceIc > 0 ? " §7| Preço: §e" + offer.priceIc + " IC"
                    : " §7| Pede: §f" + describe(offer.wanted)));
        }

        if (!found) player.sendMessage("§7Você não tem ofertas ativas.");
    }

    private void cancelOffer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /store cancelar <id>");
            return;
        }

        Offer found = null;
        for (Offer offer : offers.values()) {
            if (offer.id.equalsIgnoreCase(args[1]) && offer.seller.equals(player.getUniqueId())) {
                found = offer;
                break;
            }
        }

        if (found == null) {
            player.sendMessage("§cOferta não encontrada ou não pertence a você.");
            return;
        }

        addClaim(player.getUniqueId(), found.offered);
        offers.remove(found.id);
        saveOffers();
        player.sendMessage("§aOferta cancelada. Use §f/store coletar §apara recuperar os itens.");
    }

    private void loadEconomyAndBounties() {
        balances.clear();
        playerKills.clear();
        bounties.clear();

        ConfigurationSection walletSection = getConfig().getConfigurationSection("economy.balances");
        if (walletSection != null) {
            for (String key : walletSection.getKeys(false)) {
                try {
                    balances.put(UUID.fromString(key), Math.max(0L, walletSection.getLong(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection killsSection = getConfig().getConfigurationSection("bounty.kills");
        if (killsSection != null) {
            for (String key : killsSection.getKeys(false)) {
                try {
                    playerKills.put(UUID.fromString(key), Math.max(0, killsSection.getInt(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection bountySection = getConfig().getConfigurationSection("bounty.active");
        if (bountySection != null) {
            for (String key : bountySection.getKeys(false)) {
                try {
                    long amount = Math.max(0L, bountySection.getLong(key));
                    if (amount > 0) bounties.put(UUID.fromString(key), amount);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void saveEconomyAndBounties() {
        getConfig().set("economy.balances", null);
        getConfig().set("bounty.kills", null);
        getConfig().set("bounty.active", null);

        for (Map.Entry<UUID, Long> entry : balances.entrySet())
            getConfig().set("economy.balances." + entry.getKey(), entry.getValue());
        for (Map.Entry<UUID, Integer> entry : playerKills.entrySet())
            getConfig().set("bounty.kills." + entry.getKey(), entry.getValue());
        for (Map.Entry<UUID, Long> entry : bounties.entrySet())
            getConfig().set("bounty.active." + entry.getKey(), entry.getValue());

        saveConfig();
    }

    private long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, getConfig().getLong("economy.starting-balance", 0L));
    }

    private void addBalance(UUID playerId, long amount) {
        if (amount <= 0) return;
        long current = getBalance(playerId);
        long next;
        try {
            next = Math.addExact(current, amount);
        } catch (ArithmeticException ex) {
            next = Long.MAX_VALUE;
        }
        balances.put(playerId, next);
    }

    private void handleIc(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("saldo")) {
            player.sendMessage("§6Seu saldo: §e" + getBalance(player.getUniqueId()) + " IC");
            return;
        }

        if (args[0].equalsIgnoreCase("pagar")) {
            if (args.length < 3) {
                player.sendMessage("§cUso: /ic pagar <jogador> <valor>");
                return;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || target.equals(player)) {
                player.sendMessage("§cJogador não encontrado ou inválido. O jogador precisa estar online.");
                return;
            }

            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                player.sendMessage("§cValor inválido.");
                return;
            }

            if (amount <= 0 || amount > getBalance(player.getUniqueId())) {
                player.sendMessage("§cValor inválido ou saldo insuficiente.");
                return;
            }

            balances.put(player.getUniqueId(), getBalance(player.getUniqueId()) - amount);
            addBalance(target.getUniqueId(), amount);
            saveEconomyAndBounties();
            player.sendMessage("§aVocê enviou " + amount + " IC para " + target.getName() + ".");
            target.sendMessage("§aVocê recebeu " + amount + " IC de " + player.getName() + ".");
            return;
        }

        if (args[0].equalsIgnoreCase("dar") && player.hasPermission("theinfinitmarket.admin")) {
            if (args.length < 3) {
                player.sendMessage("§cUso: /ic dar <jogador> <valor>");
                return;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage("§cJogador não encontrado ou offline.");
                return;
            }

            try {
                long amount = Long.parseLong(args[2]);
                if (amount <= 0) throw new NumberFormatException();
                addBalance(target.getUniqueId(), amount);
                saveEconomyAndBounties();
                player.sendMessage("§aAdicionado " + amount + " IC para " + target.getName() + ".");
                target.sendMessage("§aVocê recebeu " + amount + " IC de um administrador.");
            } catch (NumberFormatException ex) {
                player.sendMessage("§cValor inválido.");
            }
            return;
        }

        player.sendMessage("§e/ic saldo §7| §e/ic pagar <jogador> <valor>");
        if (player.hasPermission("theinfinitmarket.admin"))
            player.sendMessage("§e/ic dar <jogador> <valor>");
    }

    private void showWanted(CommandSender sender) {
        if (bounties.isEmpty()) {
            sender.sendMessage("§aNão há jogadores procurados no momento.");
            return;
        }

        sender.sendMessage("§4§l☠ JOGADORES PROCURADOS ☠");
        bounties.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(entry -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                    String playerName = target.getName() == null ? entry.getKey().toString() : target.getName();
                    sender.sendMessage("§c" + playerName + " §7- §e" + entry.getValue() + " IC");
                });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        long victimBounty = bounties.getOrDefault(victimId, 0L);
        if (victimBounty > 0) {
            addBalance(killerId, victimBounty);
            bounties.remove(victimId);
            killer.sendMessage("§6☠ CAÇA CONCLUÍDA! §eVocê recebeu " + victimBounty + " IC pela recompensa de " + victim.getName() + ".");
            victim.sendMessage("§cSua recompensa de " + victimBounty + " IC foi reivindicada por " + killer.getName() + ".");

            // Caçar um procurado não aumenta a contagem de crimes do caçador.
            saveEconomyAndBounties();
            return;
        }

        int kills = playerKills.getOrDefault(killerId, 0) + 1;
        playerKills.put(killerId, kills);
        int threshold = getConfig().getInt("bounty.kills-to-become-wanted", 4);

        if (kills >= threshold) {
            long increase = Math.max(1L, getConfig().getLong("bounty.reward-per-kill", 100L));
            long maximum = Math.max(increase, getConfig().getLong("bounty.max-reward", 1000000000L));
            long current = bounties.getOrDefault(killerId, 0L);
            long next;

            try {
                next = Math.min(maximum, Math.addExact(current, increase));
            } catch (ArithmeticException ex) {
                next = maximum;
            }

            bounties.put(killerId, next);
            killer.sendMessage("§c☠ Você está procurado! Sua recompensa agora é §e" + next + " IC§c.");

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(killer)) continue;
                online.sendMessage("§4☠ " + killer.getName() + " agora está procurado por §e" + next + " IC§4!");
            }
        }

        saveEconomyAndBounties();
    }

    @EventHandler
    public void onMarketClose(InventoryCloseEvent e) {
        if (!CONFIRM_TITLE.equals(e.getView().getTitle())) return;
        pending.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static String describe(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return item.getAmount() + "x " + name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("store") && args.length == 1) {
            return List.of("criar", "minhas", "cancelar", "coletar", "ajuda");
        }
        return List.of();
    }

    private static final class Offer {
        private final String id;
        private final UUID seller;
        private final String sellerName;
        private final ItemStack offered;
        private final ItemStack wanted;
        private final long priceIc;

        private Offer(String id, UUID seller, String sellerName, ItemStack offered, ItemStack wanted) {
            this(id, seller, sellerName, offered, wanted, 0L);
        }

        private Offer(String id, UUID seller, String sellerName, ItemStack offered, ItemStack wanted, long priceIc) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.offered = offered.clone();
            this.wanted = wanted == null ? null : wanted.clone();
            this.priceIc = Math.max(0L, priceIc);
        }
    }
}
