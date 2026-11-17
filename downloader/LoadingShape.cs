// NeoRaven loading shape / Ease port, GPL-3.0. See licenses/NeoRaven-motion-NOTICE.txt.
using System;

namespace WurstBPlus.Downloader
{
    internal static class LoadingShape
    {
        private static readonly double[][] Forms = {
            new[] {12d, .72, .28, 0}, new[] {9d, .68, .32, 0},
            new[] {5d, .70, .30, 0}, new[] {9d, .68, .32, 1}
        };

        // Sample the zero contour of the client's blended radial distance fields.
        public static double[,] Points(double milliseconds)
        {
            double cycle = milliseconds / 900, fraction = cycle - Math.Floor(cycle);
            double lo = 0, hi = 1, t = fraction;
            for (int i = 0; i < 28; i++)
            {
                t = (lo + hi) / 2;
                double x = Bezier(t, .05, .1);
                if (x < fraction) lo = t; else hi = t;
            }
            double eased = Bezier(t, .7, 1);
            int index = (int)(Math.Floor(cycle) % 4);
            var a = Forms[index]; var b = Forms[(index + 1) % 4];
            double circle = a[3] + (b[3] - a[3]) * eased;
            double rotation = milliseconds / 1800 * Math.PI * 2;
            double scale = 1 - .05 * (1 - eased);
            var points = new double[360, 2];
            for (int i = 0; i < 360; i++)
            {
                double angle = i * Math.PI / 180;
                double ra = Boundary(a, angle + rotation), rb = Boundary(b, angle + rotation);
                double radius = ((ra + (rb - ra) * eased) * (1 - circle) + circle) * 32 * scale;
                points[i, 0] = Math.Cos(angle) * radius;
                points[i, 1] = Math.Sin(angle) * radius;
            }
            return points;
        }

        private static double Bezier(double t, double a, double b)
        { return 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t; }
        private static double Boundary(double[] form, double angle)
        {
            double segment = Math.PI * 2 / form[0];
            double u = (angle + segment / 2) % segment - segment / 2;
            return form[1] / Math.Max(Math.Cos(u), .001) + form[2];
        }
    }
}
