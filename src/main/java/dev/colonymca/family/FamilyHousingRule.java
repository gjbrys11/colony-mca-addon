package dev.colonymca.family;

import java.util.Collection;
import java.util.Objects;

public final class FamilyHousingRule {
    public enum Status { NO_KNOWN_FAMILY, TOGETHER, APART }
    private FamilyHousingRule() {}

    public static Status evaluate(Object home, Collection<?> familyHomes) {
        if (familyHomes.isEmpty()) return Status.NO_KNOWN_FAMILY;
        if (home == null) return Status.APART;
        return familyHomes.stream().allMatch(other -> other != null && Objects.equals(home, other))
                ? Status.TOGETHER : Status.APART;
    }
}
