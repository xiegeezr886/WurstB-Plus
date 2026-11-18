package net.wurstclient.hud2.redfix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.wurstclient.hud2.redfix.RedFixListLayout.Entry;
import net.wurstclient.hud2.redfix.RedFixListLayout.Row;

class RedFixListLayoutTest
{
    private static Entry entry(String id, float width)
    { return new Entry(id, id, "", 0xFFFFFF, width, width); }
    private static Row find(List<Row> rows, String id)
    { return rows.stream().filter(r -> r.entry().id().equals(id)).findFirst().orElseThrow(); }

    @Test void sortsByMeasuredWidthAndCanReverseWithoutLosingRows()
    {
        var layout = new RedFixListLayout();
        var input = List.of(entry("WWW", 30), entry("iiiiiiii", 20));
        var rows = layout.update(input, false, true, 0);
        assertEquals(0, find(rows, "WWW").y(200));
        assertEquals(13.5F, find(rows, "iiiiiiii").y(200));
        rows = layout.update(input, true, true, 200);
        assertEquals(0, find(rows, "iiiiiiii").y(400));
        assertEquals(-40.5F, find(rows, "WWW").x(400));
        assertEquals(2, rows.size());
    }

    @Test void removalKeepsOutgoingRowUntilAnimationFinishes()
    {
        var layout = new RedFixListLayout();
        layout.update(List.of(entry("A", 30), entry("B", 20)), false, true, 0);
        var rows = layout.update(List.of(entry("B", 20)), false, true, 200);
        assertEquals(1, find(rows, "A").alpha(200));
        assertEquals(.125F, find(rows, "A").alpha(300), .001);
        assertEquals(13.5F, find(rows, "B").y(200));
        assertEquals(0, find(rows, "B").y(400));
        assertEquals(1, layout.update(List.of(entry("B", 20)), false, true, 400).size());
    }

    @Test void togglingDuringExitReversesSmoothlyAndDoesNotDuplicate()
    {
        var layout = new RedFixListLayout();
        var input = List.of(entry("A", 30));
        layout.update(input, false, true, 0);
        var exiting = layout.update(List.of(), false, true, 200).get(0);
        float alpha = exiting.alpha(250), x = exiting.x(250);
        var returning = layout.update(input, false, true, 250);
        assertEquals(1, returning.size());
        assertEquals(alpha, returning.get(0).alpha(250));
        assertEquals(x, returning.get(0).x(250));
        assertEquals(1, returning.get(0).alpha(450));
    }

    @Test void tagsAffectSortAndReflowWithoutTeleporting()
    {
        var layout = new RedFixListLayout();
        layout.update(List.of(entry("A", 30), entry("B", 20)), false, true, 0);
        var rows = layout.update(List.of(entry("A", 30), entry("B", 50)), false, true, 200);
        assertEquals(13.5F, find(rows, "B").y(200));
        assertEquals(-30.5F, find(rows, "B").x(200));
        assertEquals(0, find(rows, "B").y(400));
        assertEquals(-60.5F, find(rows, "B").x(400));
    }

    @Test void namesAndLocalizedTagsAreSeparatedWithoutDuplication()
    {
        assertEquals("Auto EXP", RedFixListLayout.spacedName("AutoEXP"));
        assertEquals("ESP 2 D", RedFixListLayout.spacedName("ESP2D"));
        assertEquals("Vanilla", RedFixListLayout.tag("Flight", "飞行", "Flight [Vanilla]"));
        assertEquals("普通", RedFixListLayout.tag("Flight", "飞行", "飞行 [§a普通]"));
        assertEquals("", RedFixListLayout.tag("Flight", "飞行", "飞行"));
    }
}
