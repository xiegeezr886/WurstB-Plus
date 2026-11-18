package net.wurstclient.gui.title;

/** Original launcher sizes retained at the user's request. */
public record TitleMenuLayout(int size, int gap, int rowY, int width,
    int height, int margin)
{
    public static TitleMenuLayout of(int width, int height)
    {
        int margin = Math.max(8, Math.min(20, width / 30));
        int gap = Math.max(4, Math.min(12, width / 60));
        int size = Math.max(12, Math.min(64,
            Math.min((width - margin * 2 - gap * 5) / 6, height / 4)));
        return new TitleMenuLayout(size, gap,
            Math.max(0, height - margin - 24 - size), width, height, margin);
    }
    public int x(int index, int count)
    {
        int columns = columns(count);
        int first = count - (rows(count) - 1) * columns;
        int rowCount = index < first ? first : columns;
        int column = index < first ? index : (index - first) % columns;
        return (width - rowCount * size - (rowCount - 1) * gap) / 2
            + column * (size + gap);
    }
    public int y(int index, int count)
    {
        int columns = columns(count);
        int rows = rows(count);
        int first = count - (rows - 1) * columns;
        int row = index < first ? 0 : 1 + (index - first) / columns;
        return rowY - (rows - 1 - row) * (size + gap);
    }
    private int rows(int count)
    {
        int capacity = Math.max(1, (width - 2 * margin + gap) / (size + gap));
        return (count + capacity - 1) / capacity;
    }
    private int columns(int count) { return (count + rows(count) - 1) / rows(count); }
    public static float backIn(float t) { return t * t * (2.70158F * t - 1.70158F); }
    public static float backOut(float t) { return 1 + backIn(t - 1) + 3.40316F * (t - 1) * (t - 1); }
}
