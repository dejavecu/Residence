package net.t00thpick1.residence.protection.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.milkbowl.vault.economy.Economy;
import net.t00thpick1.residence.ConfigManager;
import net.t00thpick1.residence.Residence;
import net.t00thpick1.residence.api.Flag;
import net.t00thpick1.residence.api.FlagManager;
import net.t00thpick1.residence.api.ResidenceAPI;
import net.t00thpick1.residence.api.ResidenceArea;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class YAMLResidenceArea extends YAMLCuboidArea implements ResidenceArea {
    private String name;
    private ResidenceArea parent;
    private Map<String, ResidenceArea> subzoneObjects;
    private ConfigurationSection data;
    private Location tpLoc;
    private ConfigurationSection flags;
    private ConfigurationSection groupFlags;
    private ConfigurationSection playerFlags;
    private ConfigurationSection rentFlags;
    private ConfigurationSection rentLinks;
    private ConfigurationSection subzones;
    private ConfigurationSection marketData;
    private ConfigurationSection rentData;
    private List<ResidenceArea> rentLinkObjects;

    public YAMLResidenceArea(ConfigurationSection section, YAMLResidenceArea parent) throws Exception {
        super();
        name = section.getName();
        this.parent = parent;
        data = section.getConfigurationSection("Data");
        if (data.isConfigurationSection("RentData")) {
            rentData = data.getConfigurationSection("RentData");
        }
        marketData = data.getConfigurationSection("MarketData");
        initMarketState();
        loadArea(data.getConfigurationSection("Area"));
        loadTpLoc();
        if (!section.isConfigurationSection("Flags")) {
            section.createSection("Flags");
        }
        flags = section.getConfigurationSection("Flags");
        if (!section.isConfigurationSection("Groups")) {
            section.createSection("Groups");
        }
        groupFlags = section.getConfigurationSection("Groups");
        if (!section.isConfigurationSection("Players")) {
            section.createSection("Players");
        }
        playerFlags = section.getConfigurationSection("Players");
        if (!section.isConfigurationSection("RentFlags")) {
            section.createSection("RentFlags");
        }
        rentFlags = section.getConfigurationSection("RentFlags");
        if (!section.isConfigurationSection("RentLinks")) {
            section.createSection("RentLinks").set("Links", new ArrayList<String>());
        }
        rentLinks = section.getConfigurationSection("RentLinks");
        subzones = section.getConfigurationSection("Subzones");
        subzoneObjects = new HashMap<String, ResidenceArea>();
        loadSubzones();
        if (getParent() == null) {
            loadRentLinks();
        }
    }

    private void initMarketState() {
        if (isRented()) {
            YAMLEconomyManager.setRented(this);
        }
        if (isForRent()) {
            YAMLEconomyManager.setForRent(this);
        }
        if (isForSale()) {
            YAMLEconomyManager.setForSale(this);
        }
    }

    private void loadRentLinks() {
        for (String rentLink : rentLinks.getStringList("Links")) {
            rentLinkObjects.add(getSubzoneByName(rentLink.substring(rentLink.indexOf(".") + 1)));
        }
        for (ResidenceArea subzone : subzoneObjects.values()) {
            ((YAMLResidenceArea) subzone).loadRentLinks();
        }
    }

    private void loadSubzones() throws Exception {
        for (String subzone : subzones.getKeys(false)) {
            subzoneObjects.put(subzone, new YAMLResidenceArea(subzones.getConfigurationSection(subzone), this));
        }
    }

    private void loadTpLoc() {
        if (!data.isConfigurationSection("TPLocation")) {
            data.createSection("TPLocation");
            setTeleportLocation(getCenter());
        }
        ConfigurationSection tpLocation = data.getConfigurationSection("TPLocation");
        tpLoc = new Location(getWorld(), tpLocation.getDouble("X"), tpLocation.getDouble("Y"), tpLocation.getDouble("Z"));
    }

    public boolean allowAction(Flag flag) {
        if (flags.contains(flag.getName())) {
            return flags.getBoolean(flag.getName());
        }
        if (flag.getParent() != null) {
            return allowAction(flag.getParent());
        }
        return ResidenceAPI.getResidenceWorld(world).allowAction(flag);
    }

    public boolean allowAction(Player player, Flag flag) {
        Flag origFlag = flag;
        String name = player.getName();
        if (flag == FlagManager.ADMIN && getOwner().equalsIgnoreCase(name)) {
            return true;
        }
        while (true) {
            if (playerFlags.isConfigurationSection(name)) {
                ConfigurationSection playerPerms = playerFlags.getConfigurationSection(name);
                if (playerPerms.contains(flag.getName())) {
                    return playerPerms.getBoolean(flag.getName());
                }
            }

            String group = YAMLGroupManager.getPlayerGroup(player.getName());
            if (groupFlags.isConfigurationSection(group)) {
                ConfigurationSection groupPerms = groupFlags.getConfigurationSection(group);
                if (groupPerms.contains(flag.getName())) {
                    return groupPerms.getBoolean(flag.getName());
                }
            }
            if (rentFlags.contains(flag.getName())) {
                if (isRented() && getRenter().equalsIgnoreCase(name)) {
                    return rentFlags.getBoolean(flag.getName());
                }
                for (ResidenceArea rentLocation : rentLinkObjects) {
                    if (rentLocation.getRenter().equalsIgnoreCase(name)) {
                        return rentFlags.getBoolean(flag.getName());
                    }
                }
            }
            if (flags.contains(flag.getName())) {
                return flags.getBoolean(flag.getName());
            }
            if (flag.getParent() == null) {
                return ResidenceAPI.getResidenceWorld(world).allowAction(origFlag);
            } else {
                flag = flag.getParent();
            }
        }
    }

    public boolean createSubzone(String name, String owner, Location loc1, Location loc2) {
        if (subzones.isConfigurationSection(name)) {
            return false;
        }
        YAMLCuboidArea newArea = new YAMLCuboidArea(loc1, loc2);
        if (!isAreaWithin(newArea)) {
            return false;
        }
        for (ResidenceArea subzone : getSubzoneList()) {
            if (subzone.checkCollision(newArea)) {
                return false;
            }
        }

        try {
            ConfigurationSection res = subzones.createSection(name);
            ConfigurationSection data = res.createSection("Data");
            data.set("Owner", owner);
            data.set("CreationDate", System.currentTimeMillis());
            data.set("EnterMessage", YAMLGroupManager.getDefaultEnterMessage(owner));
            data.set("LeaveMessage", YAMLGroupManager.getDefaultLeaveMessage(owner));
            newArea.saveArea(data.createSection("Area"));
            ConfigurationSection marketData = data.createSection("MarketData");
            marketData.set("ForSale", false);
            marketData.set("ForRent", false);
            marketData.set("Cost", 0);
            marketData.set("IsAutoRenew", ConfigManager.getInstance().isAutoRenewDefault());
            res.createSection("Flags");
            res.createSection("Groups");
            res.createSection("Players");
            res.createSection("Subzones");
            ResidenceArea newRes = new YAMLResidenceArea(res, this);
            subzoneObjects.put(name, newRes);
            newRes.applyDefaultFlags();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public ResidenceArea getSubzoneByLoc(Location loc) {
        ResidenceArea residence = null;
        for (ResidenceArea res : subzoneObjects.values()) {
            if (res.containsLocation(loc)) {
                residence = res;
                break;
            }
        }
        if (residence == null) {
            return null;
        }
        ResidenceArea subrez = residence.getSubzoneByLoc(loc);
        if (subrez == null) {
            return residence;
        }
        return subrez;
    }

    public ResidenceArea getSubzoneByName(String subzonename) {
        if (!subzonename.contains(".")) {
            return subzoneObjects.get(subzonename);
        }
        String split[] = subzonename.split("\\.");
        ResidenceArea get = subzoneObjects.get(split[0]);
        for (int i = 1; i < split.length; i++) {
            if (get == null) {
                return null;
            }
            get = get.getSubzoneByName(split[i]);
        }
        return get;
    }

    public Collection<ResidenceArea> getSubzoneList() {
        return subzoneObjects.values();
    }

    public Collection<String> getSubzoneNameList() {
        return subzoneObjects.keySet();
    }

    public ResidenceArea getParent() {
        return parent;
    }

    public ResidenceArea getTopParent() {
        if (parent != null) {
            return this;
        }
        return parent.getTopParent();
    }

    public void rentLink(ResidenceArea res) {
        if (res.getTopParent() != this.getTopParent()) {
            return;
        }
        rentLinkObjects.add(res);
        List<String> data = rentLinks.getStringList("Links");
        data.add(res.getFullName());
        rentLinks.set("Links", data);
    }

    public int getSubzoneDepth() {
        int count = 0;
        ResidenceArea res = parent;
        while (res != null) {
            count++;
            res = res.getParent();
        }
        return count;
    }

    public boolean removeSubzone(String subzone) {
        if (subzoneObjects.remove(subzone) == null) {
            return false;
        }
        subzones.set(subzone, null);
        return true;
    }

    public String getEnterMessage() {
        return data.getString("EnterMessage");
    }

    public String getLeaveMessage() {
        return data.getString("LeaveMessage");
    }

    public void setEnterMessage(String message) {
        data.set("EnterMessage", message);
    }

    public void setLeaveMessage(String message) {
        data.set("LeaveMessage", message);
    }

    public Location getOutsideFreeLoc(Location insideLoc) {
        int maxIt = 100;
        if (!containsLocation(insideLoc)) {
            return insideLoc;
        }
        Location highLoc = getHighLocation();
        Location newLoc = new Location(highLoc.getWorld(), highLoc.getBlockX(), highLoc.getBlockY(), highLoc.getBlockZ());
        boolean found = false;
        int it = 0;
        while (!found && it < maxIt) {
            it++;
            Location lowLoc;
            newLoc.setX(newLoc.getBlockX() + 1);
            newLoc.setZ(newLoc.getBlockZ() + 1);
            lowLoc = new Location(newLoc.getWorld(), newLoc.getBlockX(), 254, newLoc.getBlockZ());
            newLoc.setY(255);
            while ((newLoc.getBlock().getTypeId() != 0 || lowLoc.getBlock().getTypeId() == 0) && lowLoc.getBlockY() > -126) {
                newLoc.setY(newLoc.getY() - 1);
                lowLoc.setY(lowLoc.getY() - 1);
            }
            if (newLoc.getBlock().getTypeId() == 0 && lowLoc.getBlock().getTypeId() != 0) {
                found = true;
            }
        }
        if (found) {
            return newLoc;
        } else {
            return getWorld().getSpawnLocation();
        }
    }

    public Location getTeleportLocation() {
        return tpLoc;
    }

    public boolean setTeleportLocation(Location location) {
        if (!this.containsLocation(location)) {
            return false;
        }
        tpLoc = location;
        ConfigurationSection tpLocation = data.getConfigurationSection("TPLocation");
        tpLocation.set("X", location.getX());
        tpLocation.set("Y", location.getY());
        tpLocation.set("Z", location.getZ());
        return true;
    }

    public boolean rename(String newName) {
        if (parent == null) {
            if (Residence.getInstance().getResidenceManager().rename(this, newName)) {
                this.name = newName;
                return true;
            }
            return false;
        } else {
            ResidenceArea parent = getParent();
            if (parent.renameSubzone(name, newName)) {
                name = newName;
                return true;
            }
            return false;
        }
    }

    public boolean renameSubzone(String oldName, String newName) {
        if (!subzoneObjects.containsKey(oldName)) {
            return false;
        }
        if (subzoneObjects.containsKey(newName)) {
            return false;
        }
        subzones.createSection(newName, subzones.getConfigurationSection(oldName).getValues(true));
        subzones.set(oldName, null);
        subzoneObjects.put(newName, subzoneObjects.remove(oldName));
        return true;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        if (parent != null) {
            return parent.getFullName() + "." + name;
        } else {
            return name;
        }
    }

    public String getOwner() {
        return data.getString("Owner");
    }

    public void setOwner(String string) {
        data.set("Owner", string);
        clearFlags();
        applyDefaultFlags();
    }

    public boolean isRented() {
        return rentData != null;
    }

    public String getRenter() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residences have no expiration");
        }
        return rentData.getString("Renter");
    }

    public boolean rent(String renter, boolean autoRenew) {
        if (renter == null) {
            throw new IllegalArgumentException("Renter cannot be null");
        }
        if (rentData == null) {
            rentData = data.createSection("RentData");
        }
        rentData.set("Renter", renter);
        rentData.set("IsAutoRenew", autoRenew);
        YAMLEconomyManager.setRented(this);
        return checkRent();
    }

    public boolean checkRent() {
        if (System.currentTimeMillis() >= rentData.getLong("NextPayment", 0)) {
            return true;
        }
        Economy econ = Residence.getInstance().getEconomy();
        if (econ.getBalance(getRenter()) < getCost()) {
            evict();
            return false;
        }
        econ.withdrawPlayer(getRenter(), getCost());
        econ.depositPlayer(getOwner(), getCost());
        rentData.set("NextPayment", System.currentTimeMillis() + getRentPeriod());
        return true;
    }

    public long getRentPeriod() {
        return marketData.getLong("RentPeriod");
    }

    public int getCost() {
        return marketData.getInt("Cost");
    }

    public void evict() {
        rentData = null;
        data.set("RentData", null);
        if (!isForRent()) {
            marketData.set("Cost", 0);
            marketData.set("RentPeriod", 0);
        }
        YAMLEconomyManager.evict(this);
        return;
    }

    public boolean isAutoRenew() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        return rentData.getBoolean("IsAutoRenew");
    }

    public boolean isAutoRenewEnabled() {
        return marketData.getBoolean("IsAutoRenew");
    }

    public void setAutoRenew(boolean autoRenew) {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        rentData.set("IsAutoRenew", autoRenew);
    }

    public void setAutoRenewEnabled(boolean autoRenew) {
        marketData.set("IsAutoRenew", autoRenew);
    }

    public List<Player> getPlayersInResidence() {
        List<Player> within = new ArrayList<Player>();
        Player[] players = Residence.getInstance().getServer().getOnlinePlayers();
        for (Player player : players) {
            if (containsLocation(player.getLocation())) {
                within.add(player);
            }
        }
        return within;
    }

    @Override
    public void setFlag(Flag flag, Boolean value) {
        flags.set(flag.getName(), value);
    }

    @Override
    public void setGroupFlag(String group, Flag flag, Boolean value) {
        ConfigurationSection groupPerms;
        if (!groupFlags.isConfigurationSection(group)) {
            groupPerms = groupFlags.createSection(group);
        } else {
            groupPerms = groupFlags.getConfigurationSection(group);
        }
        groupPerms.set(flag.getName(), value);
        if (value == null && groupPerms.getKeys(false).size() == 0) {
            groupFlags.set(group, null);
        }
    }

    @Override
    public void setPlayerFlag(String player, Flag flag, Boolean value) {
        ConfigurationSection playerPerms;
        if (!playerFlags.isConfigurationSection(player)) {
            playerPerms = playerFlags.createSection(player);
        } else {
            playerPerms = playerFlags.getConfigurationSection(player);
        }
        playerPerms.set(flag.getName(), value);
        if (value == null && playerPerms.getKeys(false).size() == 0) {
            playerFlags.set(player, null);
        }
    }

    @Override
    public void setRentFlag(Flag flag, Boolean value) {
        rentFlags.set(flag.getName(), value);
    }

    @Override
    public boolean isForRent() {
        return marketData.getBoolean("ForRent");
    }

    @Override
    public void setForRent(int cost, long rentPeriod, boolean isAutoRenewEnabled) {
        marketData.set("ForRent", true);
        marketData.set("Cost", cost);
        marketData.set("RentPeriod", rentPeriod);
        marketData.set("IsAutoRenew", isAutoRenewEnabled);
        YAMLEconomyManager.setForRent(this);
    }

    @Override
    public boolean isForSale() {
        return marketData.getBoolean("ForSale");
    }

    @Override
    public void setForSale(int cost) {
        marketData.set("ForSale", true);
        marketData.set("Cost", cost);
        YAMLEconomyManager.setForSale(this);
    }

    public void removeFromMarket() {
        marketData.set("ForSale", false);
        marketData.set("ForRent", false);
        YAMLEconomyManager.removeFromSale(this);
        YAMLEconomyManager.removeFromRent(this);
        if (!isRented()) {
            marketData.set("Cost", 0);
            marketData.set("RentPeriod", 0);
        }
    }

    @Override
    public boolean buy(String buyer) {
        Economy econ = Residence.getInstance().getEconomy();
        if (econ.getBalance(buyer) < getCost()) {
            return false;
        }
        econ.withdrawPlayer(buyer, getCost());
        econ.depositPlayer(getOwner(), getCost());
        removeFromMarket();
        YAMLEconomyManager.removeFromSale(this);
        setOwner(buyer);
        return true;
    }

    @Override
    public long getLastPaymentDate() {
        if (!isRented()) {
            throw new IllegalStateException("Unrented Residence");
        }
        return rentData.getLong("NextPayment");
    }

    public void applyDefaultFlags() {
        if (!getOwner().equalsIgnoreCase("Server Land")) {
            for (Entry<Flag, Boolean> defaultFlag : YAMLGroupManager.getDefaultAreaFlags(getOwner()).entrySet()) {
                setFlag(defaultFlag.getKey(), defaultFlag.getValue());
            }
        }
        for (Entry<Flag, Boolean> defaultFlag : YAMLGroupManager.getDefaultOwnerFlags(getOwner()).entrySet()) {
            setPlayerFlag(getOwner(), defaultFlag.getKey(), defaultFlag.getValue());
        }
        for (Entry<String, Map<Flag, Boolean>> group : YAMLGroupManager.getDefaultGroupFlags(getOwner()).entrySet()) {
            for (Entry<Flag, Boolean> defaultFlag : group.getValue().entrySet()) {
                setGroupFlag(group.getKey(), defaultFlag.getKey(), defaultFlag.getValue());
            }
        }
    }

    public void clearFlags() {
        removeAllPlayerFlags();
        removeAllGroupFlags();
        removeAllAreaFlags();
    }

    public void copyPermissions(ResidenceArea mirror) {
        ConfigurationSection parent = playerFlags.getParent();
        parent.set("Players", null);
        playerFlags = parent.createSection("Players", ((YAMLResidenceArea) mirror).playerFlags.getValues(true));
        parent = groupFlags.getParent();
        parent.set("Groups", null);
        groupFlags = parent.createSection("Groups", ((YAMLResidenceArea) mirror).groupFlags.getValues(true));
        parent = flags.getParent();
        parent.set("Flags", null);
        flags = parent.createSection("Flags", ((YAMLResidenceArea) mirror).flags.getValues(true));
    }

    public void removeAllPlayerFlags() {
        ConfigurationSection parent = playerFlags.getParent();
        parent.set("Players", null);
        playerFlags = parent.createSection("Players");
    }

    public void removeAllGroupFlags() {
        ConfigurationSection parent = groupFlags.getParent();
        parent.set("Groups", null);
        groupFlags = parent.createSection("Groups");
    }

    public void removeAllAreaFlags() {
        ConfigurationSection parent = flags.getParent();
        parent.set("Flags", null);
        flags = parent.createSection("Flags");
    }

    public void removeAllPlayerFlags(String player) {
        playerFlags.set(player, null);
    }

    @Override
    public Map<Flag, Boolean> getRentFlags() {
        HashMap<Flag, Boolean> rFlags = new HashMap<Flag, Boolean>();
        for (String flag : rentFlags.getKeys(false)) {
            Flag flagObj = FlagManager.getFlag(flag);
            if (flagObj != null) {
                rFlags.put(flagObj, rentFlags.getBoolean(flag));
            }
        }
        return rFlags;
    }

    @Override
    public Map<String, Map<Flag, Boolean>> getPlayerFlags() {
        Map<String, Map<Flag, Boolean>> playerFlags = new HashMap<String, Map<Flag, Boolean>>();
        for (String player : this.playerFlags.getKeys(false)) {
            ConfigurationSection playerSection = this.playerFlags.getConfigurationSection(player);
            HashMap<Flag, Boolean> pFlags = new HashMap<Flag, Boolean>();
            for (String flag : playerSection.getKeys(false)) {
                Flag flagObj = FlagManager.getFlag(flag);
                if (flagObj != null) {
                    pFlags.put(flagObj, playerSection.getBoolean(flag));
                }
            }
            if (pFlags.size() > 0) {
                playerFlags.put(player, pFlags);
            }
        }
        return playerFlags;
    }

    @Override
    public Map<Flag, Boolean> getPlayerFlags(String name) {
        HashMap<Flag, Boolean> pFlags = new HashMap<Flag, Boolean>();
        if (!playerFlags.isConfigurationSection(name)) {
            return pFlags;
        }
        ConfigurationSection playerSection = playerFlags.getConfigurationSection(name);
        for (String flag : playerSection.getKeys(false)) {
            Flag flagObj = FlagManager.getFlag(flag);
            if (flagObj != null) {
                pFlags.put(flagObj, playerSection.getBoolean(flag));
            }
        }
        return pFlags;
    }

    @Override
    public Map<Flag, Boolean> getAreaFlags() {
        HashMap<Flag, Boolean> areaFlags = new HashMap<Flag, Boolean>();
        for (String flag : flags.getKeys(false)) {
            Flag flagObj = FlagManager.getFlag(flag);
            if (flagObj != null) {
                areaFlags.put(flagObj, flags.getBoolean(flag));
            }
        }
        return areaFlags;
    }

    @Override
    public Map<String, Map<Flag, Boolean>> getGroupFlags() {
        Map<String, Map<Flag, Boolean>> groupFlags = new HashMap<String, Map<Flag, Boolean>>();
        for (String group : this.groupFlags.getKeys(false)) {
            ConfigurationSection groupSection = this.groupFlags.getConfigurationSection(group);
            HashMap<Flag, Boolean> gFlags = new HashMap<Flag, Boolean>();
            for (String flag : groupSection.getKeys(false)) {
                Flag flagObj = FlagManager.getFlag(flag);
                if (flagObj != null) {
                    gFlags.put(flagObj, groupSection.getBoolean(flag));
                }
            }
            groupFlags.put(group, gFlags);
        }
        return groupFlags;
    }

    @Override
    public Collection<String> getRentLinkStrings() {
        return rentLinks.getStringList("Links");
    }
}
