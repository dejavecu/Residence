package net.t00thpick1.residence.api;

import java.io.IOException;
import java.util.Collection;

import net.t00thpick1.residence.api.areas.CuboidArea;
import net.t00thpick1.residence.api.areas.ResidenceArea;

import org.bukkit.Location;

public interface ResidenceManager {

    public ResidenceArea getByLocation(Location location);

    public void save() throws IOException;

    public ResidenceArea getByName(String string);

    public Collection<ResidenceArea> getOwnedResidences(String player);

    public int getOwnedZoneCount(String player);

    public boolean createResidence(String residenceName, String player, CuboidArea area);

    public void remove(ResidenceArea res);

}