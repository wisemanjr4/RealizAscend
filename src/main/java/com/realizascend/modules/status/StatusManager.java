package com.realizascend.modules.status;

import com.realizascend.RealizAscend;
import com.realizascend.core.ConfigManager;
import com.realizascend.core.RealizModule;
import com.realizascend.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatusManager extends RealizModule {

    private static final long FLAVOR_COOLDOWN = 90000L; // 条件ごとの再表示間隔 (90秒)

    private static final Map<String, String> FLAVOR = new LinkedHashMap<>();
    private static final Map<String, String> REASON = new LinkedHashMap<>();

    static {
        FLAVOR.put("水分不足", "喉が渇いた...水が欲しい。");
        FLAVOR.put("空腹", "お腹が鳴っている...何か食べたい。");
        FLAVOR.put("カロリー不足", "腹が減って力が出ない...何か食べないと。");
        FLAVOR.put("タンパク質不足", "体に力が入らない...肉を食べるべきだ。");
        FLAVOR.put("ビタミン不足", "なんだか体が重い...野菜が必要だ。");
        FLAVOR.put("塩分不足", "足がつりそうだ...塩分が足りない。");
        FLAVOR.put("塩分過多", "喉がひどく渇く...塩辛いものを食べ過ぎた。");
        FLAVOR.put("失血", "目の前がチカチカする...血を失っている。");
        FLAVOR.put("頭部負傷", "頭が割れるように痛む...手当てが必要だ。");
        FLAVOR.put("胴部負傷", "胸の内側が痛む...内出血かもしれない。");
        FLAVOR.put("脚部負傷", "脚が痛んでまともに歩けない。");
        FLAVOR.put("骨折", "骨折した箇所がズキズキ痛む...添え木が必要だ。");
        FLAVOR.put("感染症", "傷口が熱を持って腫れてきた...感染している。");
        FLAVOR.put("スタミナ切れ", "息が切れて動けない...少し休まないと。");
        FLAVOR.put("極度の疲労", "目が重くて仕方ない...眠らないと倒れてしまう。");
        FLAVOR.put("睡眠不足", "頭がぼーっとする...まともに眠れていない。");
        FLAVOR.put("ストレス過多", "心が休まらない...気が休まらない。");
        FLAVOR.put("無気力", "何をやってもやる気が起きない。");
        FLAVOR.put("重量オーバー", "荷物が重すぎて身動きが取れない。");
        FLAVOR.put("高温", "暑すぎる...このままだと倒れてしまう。");
        FLAVOR.put("低温", "寒すぎる...体が震えて止まらない。");

        REASON.put("水分不足", "水分が低下している");
        REASON.put("空腹", "胃袋が空いている");
        REASON.put("カロリー不足", "カロリーが不足している");
        REASON.put("タンパク質不足", "タンパク質が不足している");
        REASON.put("ビタミン不足", "ビタミンが不足している");
        REASON.put("塩分不足", "塩分が不足している");
        REASON.put("塩分過多", "塩分を取り過ぎている");
        REASON.put("失血", "血液が低下している");
        REASON.put("頭部負傷", "頭部に負傷がある");
        REASON.put("胴部負傷", "胴部に負傷がある");
        REASON.put("脚部負傷", "脚部に負傷がある");
        REASON.put("骨折", "骨折している");
        REASON.put("感染症", "傷口が感染している");
        REASON.put("スタミナ切れ", "スタミナが尽きている");
        REASON.put("極度の疲労", "疲労が限界に達している");
        REASON.put("睡眠不足", "睡眠負債が溜まっている");
        REASON.put("ストレス過多", "ストレスが高すぎる");
        REASON.put("無気力", "ストレスが低すぎて無気力");
        REASON.put("重量オーバー", "所持重量が限界を超えている");
        REASON.put("高温", "体温が危険なほど高い");
        REASON.put("低温", "体温が危険なほど低い");
    }

    private final Map<UUID, Map<String, Long>> lastFlavor = new HashMap<>();
    private BukkitTask task;

    public StatusManager(RealizAscend plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        int interval = plugin.getConfigManager().statusFlavorInterval;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processFlavor(player);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        lastFlavor.clear();
    }

    private void processFlavor(Player player) {
        List<String> conditions = getActiveConditions(player);
        if (conditions.isEmpty()) return;

        long now = System.currentTimeMillis();
        Map<String, Long> map = lastFlavor.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        // まだ表示していない条件を1つ選ぶ
        String picked = null;
        for (String condition : conditions) {
            Long last = map.get(condition);
            if (last == null || now - last > FLAVOR_COOLDOWN) {
                picked = condition;
                break;
            }
        }
        if (picked == null) return;

        map.put(picked, now);
        String flavor = FLAVOR.get(picked);
        if (flavor != null) {
            player.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + flavor);
        }
    }

    /**
     * 現在プレイヤーが受けている異常状態の一覧を返す。
     * 例: ["水分不足 (水分が低下している)", "失血 (血液が低下している)"]
     */
    public List<String> getActiveConditions(Player player) {
        List<String> conditions = new ArrayList<>();
        PlayerData data = plugin.getDataManager().getData(player);
        ConfigManager cfg = plugin.getConfigManager();

        if (data.getHydration() < cfg.hydrationWarningThreshold) conditions.add("水分不足");
        if (player.getFoodLevel() < 6) conditions.add("空腹");
        if (data.getCalories() < cfg.nutritionWarningThreshold) conditions.add("カロリー不足");
        if (data.getProtein() < cfg.nutritionWarningThreshold) conditions.add("タンパク質不足");
        if (data.getVitamins() < cfg.nutritionWarningThreshold) conditions.add("ビタミン不足");
        if (data.getSalt() < cfg.nutritionWarningThreshold) conditions.add("塩分不足");
        if (data.getSalt() > 75) conditions.add("塩分過多");
        if (data.getBlood() < 50) conditions.add("失血");
        if (data.isHeadInjured()) conditions.add("頭部負傷");
        if (data.isTorsoInjured()) conditions.add("胴部負傷");
        if (data.isLegsInjured()) conditions.add("脚部負傷");
        if (data.isFractured()) conditions.add("骨折");
        if (data.isInfected()) conditions.add("感染症");
        if (data.getStamina() <= 5) conditions.add("スタミナ切れ");
        if (data.getFatigue() > 70) conditions.add("極度の疲労");
        if (data.getSleepDebt() > 0) conditions.add("睡眠不足");
        if (data.getStress() > 70) conditions.add("ストレス過多");
        if (data.getStress() < 30) conditions.add("無気力");
        if (data.getCurrentWeight() > cfg.weightOverLimit) conditions.add("重量オーバー");

        String zone = data.getTempZone();
        if (zone.equals("CRITICAL") || zone.equals("FATAL")) {
            double bodyTemp = data.getBodyTemperature();
            if (bodyTemp > cfg.tempComfortableMax) conditions.add("高温");
            else if (bodyTemp < cfg.tempComfortableMin) conditions.add("低温");
        }

        return conditions;
    }

    public String getReason(String condition) {
        return REASON.getOrDefault(condition, "");
    }
}