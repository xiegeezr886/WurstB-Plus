using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("WDownloader")]
[assembly: AssemblyProduct("WDownloader")]
[assembly: AssemblyVersion("1.2.0.0")]
[assembly: AssemblyFileVersion("1.2.0.0")]

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        using (var splash = new StartupForm())
        {
            splash.Shown += delegate
            {
                ThreadPool.QueueUserWorkItem(delegate
                {
                    Launch(splash);
                    if (!splash.IsDisposed && splash.IsHandleCreated)
                        try { splash.BeginInvoke(new Action(splash.Close)); } catch (InvalidOperationException) { }
                });
            };
            Application.Run(splash);
        }
    }

    private static void Launch(StartupForm splash)
    {
        string data = null;
        try
        {
            var documentsId = new Guid("FDD39AD0-238F-46AF-ADB4-6C85480369C7");
            IntPtr pointer;
            Marshal.ThrowExceptionForHR(SHGetKnownFolderPath(ref documentsId, 0x4000, IntPtr.Zero, out pointer));
            try { data = Path.Combine(Marshal.PtrToStringUni(pointer), "WDownloader"); }
            finally { Marshal.FreeCoTaskMem(pointer); }
            Directory.CreateDirectory(data);
            string version;
            using (var input = Assembly.GetExecutingAssembly().GetManifestResourceStream("PayloadHash"))
            using (var reader = new StreamReader(input)) version = reader.ReadToEnd().Trim();
            if (version.Length != 64) throw new InvalidDataException("运行库版本信息损坏。");
            var runtime = Path.Combine(data, "Runtime", version);
            var executable = Path.Combine(runtime, "WDownloader.exe");
            using (var mutex = new Mutex(false, @"Local\WDownloader-Extract-" + version))
            {
                bool acquired = false;
                try
                {
                    try { acquired = mutex.WaitOne(TimeSpan.FromMinutes(2)); }
                    catch (AbandonedMutexException) { acquired = true; }
                    if (!acquired) throw new IOException("另一份 WDownloader 正在准备运行库，请稍后重试。");
                    if (!File.Exists(Path.Combine(runtime, ".complete")) || !File.Exists(executable)
                        || !File.Exists(Path.Combine(runtime, "WDownloader.dll"))
                        || !File.Exists(Path.Combine(runtime, "hostfxr.dll"))
                        || !File.Exists(Path.Combine(runtime, "coreclr.dll")))
                        Extract(runtime, version);
                }
                finally { if (acquired) mutex.ReleaseMutex(); }
            }
            var temp = Path.Combine(data, "Temp");
            Directory.CreateDirectory(temp);
            var start = new ProcessStartInfo(executable);
            start.UseShellExecute = false;
            start.WorkingDirectory = runtime;
            start.EnvironmentVariables["TEMP"] = temp;
            start.EnvironmentVariables["TMP"] = temp;
            if (splash.Cancelled) return;
            using (var process = Process.Start(start))
            {
                if (process != null)
                    try { process.WaitForInputIdle(30000); } catch (InvalidOperationException) { }
            }
        }
        catch (Exception error)
        {
            try { if (data != null) File.AppendAllText(Path.Combine(data, "bootstrap-error.log"), DateTimeOffset.Now + "\r\n" + error + "\r\n"); }
            catch { }
            MessageBox.Show("WDownloader 无法启动：\r\n" + error.Message +
                "\r\n\r\n如果程序位于受限的开发目录，请将这个 EXE 复制到桌面等普通文件夹后运行。",
                "WDownloader", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private static void Extract(string destination, string expectedHash)
    {
        var parent = Path.GetDirectoryName(destination);
        Directory.CreateDirectory(parent);
        var staging = Path.Combine(parent, ".extract-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(staging);
        try
        {
            using (var payload = Assembly.GetExecutingAssembly().GetManifestResourceStream("Payload"))
            {
                using (var hash = SHA256.Create())
                {
                    var actual = BitConverter.ToString(hash.ComputeHash(payload)).Replace("-", "").ToLowerInvariant();
                    if (!string.Equals(expectedHash, actual, StringComparison.OrdinalIgnoreCase))
                        throw new InvalidDataException("内嵌运行库校验失败，请重新获取程序。");
                }
                payload.Position = 0;
                using (var archive = new ZipArchive(payload, ZipArchiveMode.Read, true))
                {
                    var prefix = Path.GetFullPath(staging) + Path.DirectorySeparatorChar;
                    foreach (var entry in archive.Entries)
                    {
                        var target = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                        if (!target.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                            throw new InvalidDataException("运行库文件路径无效。");
                        if (string.IsNullOrEmpty(entry.Name)) { Directory.CreateDirectory(target); continue; }
                        Directory.CreateDirectory(Path.GetDirectoryName(target));
                        using (var input = entry.Open())
                        using (var output = new FileStream(target, FileMode.CreateNew, FileAccess.Write)) input.CopyTo(output);
                    }
                }
            }
            File.WriteAllText(Path.Combine(staging, ".complete"), expectedHash);
            // Interrupted extraction is never used; keep an incomplete old folder for diagnosis.
            if (Directory.Exists(destination)) Directory.Move(destination, destination + ".incomplete-" + Guid.NewGuid().ToString("N"));
            Directory.Move(staging, destination);
        }
        finally { if (Directory.Exists(staging)) Directory.Delete(staging, true); }
    }

    [DllImport("shell32.dll")]
    private static extern int SHGetKnownFolderPath(ref Guid id, uint flags, IntPtr token, out IntPtr path);
}

internal sealed class StartupForm : Form
{
    private readonly Stopwatch clock = Stopwatch.StartNew();
    private readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 16 };
    private readonly bool animate;
    public volatile bool Cancelled;

    public StartupForm()
    {
        Text = "WDownloader";
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        AutoScaleDimensions = new SizeF(96, 96);
        AutoScaleMode = AutoScaleMode.Dpi;
        ClientSize = new Size(1040, 700);
        BackColor = Color.White;
        DoubleBuffered = true;
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        bool enabled;
        animate = !SystemParametersInfo(0x1042, 0, out enabled, 0) || enabled;
        timer.Tick += delegate { Invalidate(); };
        if (animate) timer.Start();
        var close = new Button { Text = "×", Width = 43, Height = 36, Top = 12,
            Left = ClientSize.Width - 58, Anchor = AnchorStyles.Top | AnchorStyles.Right,
            FlatStyle = FlatStyle.Flat, BackColor = Color.White, TabStop = true, AccessibleName = "关闭" };
        close.FlatAppearance.BorderSize = 0;
        close.Click += delegate { Close(); };
        Controls.Add(close);
    }
    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        var outline = WurstBPlus.Downloader.LoadingShape.Points(animate ? clock.Elapsed.TotalMilliseconds : 0);
        var points = new PointF[outline.GetLength(0)];
        float scale = e.Graphics.DpiX / 96;
        for (int i = 0; i < points.Length; i++)
            points[i] = new PointF(ClientSize.Width / 2f + (float)outline[i, 0] * scale,
                ClientSize.Height / 2f + (float)outline[i, 1] * scale);
        using (var brush = new SolidBrush(Color.FromArgb(181, 190, 240))) e.Graphics.FillPolygon(brush, points);
        using (var pen = new Pen(Color.FromArgb(228, 231, 238))) e.Graphics.DrawRectangle(pen, 0, 0, ClientSize.Width - 1, ClientSize.Height - 1);
    }
    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        Cancelled = true;
        timer.Stop();
        base.OnFormClosing(e);
    }
    protected override void Dispose(bool disposing)
    {
        if (disposing) timer.Dispose();
        base.Dispose(disposing);
    }
    [DllImport("user32.dll")]
    private static extern bool SystemParametersInfo(uint action, uint param, out bool value, uint flags);
}
