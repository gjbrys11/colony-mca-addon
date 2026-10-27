package dev.colonymca;

import dev.colonymca.family.FamilyHousingRule;
import java.util.Arrays;
import java.util.List;

/** Dependency-free regression runner: no Minecraft bootstrap needed for housing policy. */
public final class FamilyHousingRuleTest {
    public static void main(String[] args) {
        check("unrelated residents are neutral", "A", List.of(), FamilyHousingRule.Status.NO_KNOWN_FAMILY);
        check("unrelated homeless resident is neutral", null, List.of(), FamilyHousingRule.Status.NO_KNOWN_FAMILY);
        check("spouse in same residence", "A", List.of("A"), FamilyHousingRule.Status.TOGETHER);
        check("whole family together", "A", List.of("A", "A", "A"), FamilyHousingRule.Status.TOGETHER);
        check("spouse in other residence", "A", List.of("B"), FamilyHousingRule.Status.APART);
        check("one child lives elsewhere", "A", List.of("A", "B"), FamilyHousingRule.Status.APART);
        check("homeless family member", "A", Arrays.asList("A", null), FamilyHousingRule.Status.APART);
        check("both residents homeless", null, Arrays.asList((Object) null), FamilyHousingRule.Status.APART);
        check("residence identifiers compare by value", new String("A"), List.of(new String("A")), FamilyHousingRule.Status.TOGETHER);
        System.out.println("9 family housing regression checks passed");
    }

    private static void check(String name, Object home, List<?> family, FamilyHousingRule.Status expected) {
        var actual = FamilyHousingRule.evaluate(home, family);
        if (actual != expected) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
}
