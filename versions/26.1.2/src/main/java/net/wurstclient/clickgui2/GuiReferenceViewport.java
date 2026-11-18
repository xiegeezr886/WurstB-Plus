package net.wurstclient.clickgui2;

/** Screenshot dimensions, independent of Minecraft's global GUI scale. */
public record GuiReferenceViewport(float scale, int width, int height)
{
    // Opai capture: 1440 px game area, 64 px module rows = 19.6 local units.
    static final int OPAI_HEIGHT = 441;
    // Rise capture: 653 px game area, 640x480 window = 400x300 local units.
    public static final float RISE_HEIGHT = 653 / 1.6F;

    public static GuiReferenceViewport fit(int screenWidth, int screenHeight, float referenceHeight)
    {
        float scale = Math.max(1, screenHeight) / referenceHeight;
        return new GuiReferenceViewport(scale, Math.round(screenWidth / scale), Math.round(referenceHeight));
    }

    double local(double coordinate) { return coordinate / scale; }
    double screen(double coordinate) { return coordinate * scale; }
}
