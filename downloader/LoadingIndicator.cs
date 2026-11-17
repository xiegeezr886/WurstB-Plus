using System.Diagnostics;
using System.Windows;
using System.Windows.Media;

namespace WurstBPlus.Downloader;

public sealed class LoadingIndicator : FrameworkElement
{
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private readonly SolidColorBrush _accent = new(Color.FromRgb(181, 190, 240));
    public LoadingIndicator()
    {
        _accent.Freeze();
        IsVisibleChanged += (_, _) =>
        {
            CompositionTarget.Rendering -= Animate;
            if (IsVisible) CompositionTarget.Rendering += Animate;
        };
        Unloaded += (_, _) => CompositionTarget.Rendering -= Animate;
    }
    private void Animate(object? sender, EventArgs e)
    {
        if (IsVisible && SystemParameters.ClientAreaAnimation) InvalidateVisual();
    }
    protected override void OnRender(DrawingContext context)
    {
        base.OnRender(context);
        var points = LoadingShape.Points(SystemParameters.ClientAreaAnimation ? _clock.Elapsed.TotalMilliseconds : 0);
        var geometry = new StreamGeometry();
        using (var path = geometry.Open())
        {
            path.BeginFigure(new Point(ActualWidth / 2 + points[0, 0], ActualHeight / 2 + points[0, 1]), true, true);
            for (var i = 1; i < points.GetLength(0); i++)
                path.LineTo(new Point(ActualWidth / 2 + points[i, 0], ActualHeight / 2 + points[i, 1]), true, false);
        }
        geometry.Freeze();
        context.DrawGeometry(_accent, null, geometry);
    }
}
