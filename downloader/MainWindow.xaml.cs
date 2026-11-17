using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Animation;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;
using WurstBPlus.Downloader.Core;

namespace WurstBPlus.Downloader;

public partial class MainWindow : Window
{
    private const string AppUrl = "https://downloader.local/";
    private static string StateDirectory => AppStorage.DirectoryPath;
    private readonly HttpClient _http = new(new SocketsHttpHandler { ConnectTimeout = TimeSpan.FromSeconds(8), MaxConnectionsPerServer = 8, PooledConnectionLifetime = TimeSpan.FromMinutes(5) }) { Timeout = Timeout.InfiniteTimeSpan };
    private readonly ReleaseCatalog _catalog;
    private readonly DownloadEngine _engine;
    private readonly Dictionary<string, ProbeResult> _probes = [];
    private IReadOnlyList<Release> _releases = [];
    private CancellationTokenSource? _operation;
    private string _operationName = "", _status = "正在获取发布列表…", _detail = "", _catalogInfo = "";
    private TransferProgress? _progress;
    private DownloadResult? _completed;
    private Preferences _preferences = new();
    private bool _ready, _initialized, _closeAfterCancel;
    private readonly TaskCompletionSource _frontendReady = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private bool Busy => _operation is not null;
    private ReleaseAsset? SelectedAsset => _releases.FirstOrDefault(r => r.Tag == _preferences.Release)?.Jars.FirstOrDefault(a => a.Minecraft == _preferences.Minecraft && a.Loader == _preferences.Loader);

    public MainWindow()
    {
        InitializeComponent();
        _http.DefaultRequestHeaders.UserAgent.ParseAdd("WurstBPlus-Downloader/1.1");
        _catalog = new(_http); _engine = new(_http);
        try { _preferences = JsonSerializer.Deserialize<Preferences>(File.ReadAllText(Path.Combine(StateDirectory, "settings.json"))) ?? new(); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or JsonException) { }
        var oldDefault = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "WurstBPlus");
        if (string.IsNullOrEmpty(_preferences.Folder) || !Directory.Exists(_preferences.Folder) || string.Equals(_preferences.Folder, oldDefault, StringComparison.OrdinalIgnoreCase))
            _preferences = _preferences with { Folder = WindowsFolders.Downloads };
        if (_preferences.Connections is not (1 or 2 or 4 or 8)) _preferences = _preferences with { Connections = 4 };
        if (_preferences.SourceId != "auto" && !DownloadSource.BuiltIn.Any(s => s.Id == _preferences.SourceId)) _preferences = _preferences with { SourceId = "auto" };
    }

    private async void Window_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            var browserDirectory = Path.Combine(StateDirectory, "WebView2");
            Directory.CreateDirectory(browserDirectory);
            var environment = await CoreWebView2Environment.CreateAsync(null, browserDirectory).WaitAsync(TimeSpan.FromSeconds(30));
            await Browser.EnsureCoreWebView2Async(environment).WaitAsync(TimeSpan.FromSeconds(30));
            var web = Browser.CoreWebView2;
            web.Settings.AreDefaultContextMenusEnabled = false;
            web.Settings.AreDevToolsEnabled = false;
            web.Settings.IsStatusBarEnabled = false;
            web.Settings.IsZoomControlEnabled = false;
            web.Settings.AreHostObjectsAllowed = false;
            web.AddWebResourceRequestedFilter(AppUrl + "*", CoreWebView2WebResourceContext.All);
            web.WebResourceRequested += (_, args) =>
            {
                if (args.Request.Uri != AppUrl) { args.Response = environment.CreateWebResourceResponse(null, 404, "Not Found", ""); return; }
                var html = typeof(MainWindow).Assembly.GetManifestResourceStream("Downloader.Frontend.html");
                args.Response = environment.CreateWebResourceResponse(html, 200, "OK", "Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store");
            };
            web.NavigationStarting += (_, args) => { if (args.Uri != AppUrl) args.Cancel = true; };
            web.NewWindowRequested += (_, args) => args.Handled = true;
            web.PermissionRequested += (_, args) => args.State = CoreWebView2PermissionState.Deny;
            web.WebMessageReceived += WebMessageReceived;
            web.Navigate(AppUrl);
            await _frontendReady.Task.WaitAsync(TimeSpan.FromSeconds(30));
        }
        catch (Exception ex)
        {
            var reason = ex is WebView2RuntimeNotFoundException
                ? "请安装 Microsoft Edge WebView2 Runtime 后重新打开下载器。"
                : "无法初始化界面，请检查数据目录是否可写：\n" + StateDirectory;
            AppStorage.Log(ex);
            MessageBox.Show(this, reason + "\n\n" + ex.Message, "下载器启动失败", MessageBoxButton.OK, MessageBoxImage.Error);
            Close();
        }
    }

    private void Startup_Drag(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.OriginalSource is not System.Windows.Controls.Button && e.ButtonState == System.Windows.Input.MouseButtonState.Pressed) DragMove();
    }
    private void Startup_Close(object sender, RoutedEventArgs e) => Close();

    private async Task RevealInterfaceAsync()
    {
        if (!IsVisible) return;
        if (SystemParameters.ClientAreaAnimation)
        {
            var finished = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
            var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(180))
            { EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseInOut } };
            fade.Completed += (_, _) => finished.TrySetResult();
            StartupOverlay.BeginAnimation(OpacityProperty, fade);
            await finished.Task;
        }
        if (!IsVisible) return;
        // WebView2 owns a native child window: fade its HTML, not WPF Opacity.
        Browser.Visibility = Visibility.Visible;
        StartupOverlay.Visibility = Visibility.Collapsed;
        Browser.CoreWebView2.PostWebMessageAsJson("{\"type\":\"reveal\"}");
    }

    private async void WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        if (e.Source != AppUrl) return;
        try
        {
            using var message = JsonDocument.Parse(e.WebMessageAsJson);
            var action = message.RootElement.GetProperty("action").GetString();
            var payload = message.RootElement.TryGetProperty("payload", out var p) ? p : default;
            switch (action)
            {
                case "ready":
                    _frontendReady.TrySetResult();
                    _ready = true; Publish();
                    if (!_initialized)
                    {
                        _initialized = true;
                        try { await LoadReleasesAsync(); }
                        finally { await RevealInterfaceAsync(); }
                    }
                    break;
                case "refresh" when !Busy: await LoadReleasesAsync(); break;
                case "configure" when !Busy: Configure(payload); break;
                case "download" when !Busy: Configure(payload); await DownloadAsync(); break;
                case "probe" when !Busy: await ProbeAsync(); break;
                case "cancel": _operation?.Cancel(); break;
                case "browse" when !Busy:
                    var dialog = new OpenFolderDialog { Title = "选择 JAR 保存文件夹", InitialDirectory = _preferences.Folder };
                    if (dialog.ShowDialog(this) == true) { _preferences = _preferences with { Folder = dialog.FolderName }; Save(); Publish(); }
                    break;
                case "resetFolder" when !Busy: _preferences = _preferences with { Folder = WindowsFolders.Downloads }; Save(); Publish(); break;
                case "openFolder" when _completed is not null: Shell("explorer.exe", $"/select,\"{_completed.Path}\""); break;
                case "copyHash" when _completed is not null: Clipboard.SetText(_completed.Sha256); _detail = "SHA-256 已复制。"; Publish(); break;
                case "repository": Shell(ReleaseCatalog.Repository); break;
                case "releaseNotes": Shell(ReleaseCatalog.Repository + "/releases" + (string.IsNullOrEmpty(_preferences.Release) ? "" : "/tag/" + Uri.EscapeDataString(_preferences.Release))); break;
                case "licenses": ShowLicenses(); break;
                case "minimize": WindowState = WindowState.Minimized; break;
                case "maximize": WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized; break;
                case "drag": ReleaseCapture(); SendMessage(new WindowInteropHelper(this).Handle, 0xA1, new IntPtr(2), IntPtr.Zero); break;
                case "close": Close(); break;
            }
        }
        catch (Exception ex) { _status = "操作未完成"; _detail = ex.Message; Publish(); }
    }

    private void Configure(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return;
        string Read(string name, string current) => payload.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? current : current;
        var release = Read("release", _preferences.Release);
        var minecraft = Read("minecraft", _preferences.Minecraft);
        var loader = Read("loader", _preferences.Loader);
        if (_releases.Any(r => r.Tag == release && r.Jars.Any(a => a.Minecraft == minecraft && a.Loader == loader)))
        {
            if (release != _preferences.Release || minecraft != _preferences.Minecraft || loader != _preferences.Loader)
            { _completed = null; _progress = null; _probes.Clear(); _status = "准备就绪"; _detail = ""; }
            _preferences = _preferences with { Release = release, Minecraft = minecraft, Loader = loader };
        }
        var source = Read("sourceId", _preferences.SourceId);
        if (source == "auto" || DownloadSource.BuiltIn.Any(s => s.Id == source)) _preferences = _preferences with { SourceId = source };
        if (payload.TryGetProperty("connections", out var connections) && int.TryParse(connections.ToString(), out var count) && count is 1 or 2 or 4 or 8)
            _preferences = _preferences with { Connections = count };
        Save(); Publish();
    }

    private async Task LoadReleasesAsync()
    {
        Begin("catalog", "正在获取发布列表…");
        var token = _operation!.Token;
        try
        {
            try
            {
                var result = await _catalog.LoadAsync(token);
                _releases = result.Releases;
                _catalogInfo = $"实时列表 · {result.Source}";
                try { Directory.CreateDirectory(StateDirectory); await File.WriteAllBytesAsync(Path.Combine(StateDirectory, "releases.json"), JsonSerializer.SerializeToUtf8Bytes(_releases), token); }
                catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
            }
            catch (Exception ex) when (!token.IsCancellationRequested && ex is HttpRequestException or IOException or JsonException)
            {
                _releases = [];
                var cache = Path.Combine(StateDirectory, "releases.json");
                try
                {
                    if (File.Exists(cache)) { _releases = ReleaseCatalog.Parse(await File.ReadAllBytesAsync(cache, token)); _catalogInfo = $"离线缓存 · {File.GetLastWriteTime(cache):yyyy-MM-dd HH:mm} · 可在设置中刷新"; }
                }
                catch (Exception cacheError) when (cacheError is IOException or UnauthorizedAccessException or JsonException) { }
                if (_releases.Count == 0)
                {
                    await using var stream = typeof(MainWindow).Assembly.GetManifestResourceStream("WurstBPlus.Downloader.ReleaseSnapshot.json")!;
                    using var snapshot = await JsonDocument.ParseAsync(stream, cancellationToken: token);
                    _releases = ReleaseCatalog.Parse(Encoding.UTF8.GetBytes(snapshot.RootElement.GetProperty("releases").GetRawText()));
                    _catalogInfo = $"随包列表 · {snapshot.RootElement.GetProperty("fetched_at").GetDateTimeOffset():yyyy-MM-dd} · 可能不是最新版本，可在设置中刷新";
                }
            }
            var release = _releases.FirstOrDefault(r => r.Tag == _preferences.Release) ?? _releases.FirstOrDefault(r => !r.Prerelease) ?? _releases.First();
            var asset = release.Jars.FirstOrDefault(a => a.Minecraft == _preferences.Minecraft && a.Loader == _preferences.Loader)
                ?? release.Jars.FirstOrDefault(a => a.Loader == "Fabric") ?? release.Jars.First();
            _preferences = _preferences with { Release = release.Tag, Minecraft = asset.Minecraft, Loader = asset.Loader };
            _status = _catalogInfo.StartsWith("实时") ? "准备就绪" : _catalogInfo;
            _completed = null; _progress = null; _probes.Clear(); Save();
        }
        catch (OperationCanceledException) { _status = "已取消刷新"; }
        catch (Exception ex) { _status = "获取发布列表失败"; _detail = ex.Message; }
        finally { End(); }
    }

    private async Task<IReadOnlyList<ProbeResult>> ProbeSelectedAsync(ReleaseAsset asset, CancellationToken token)
    {
        _probes.Clear(); Publish();
        return await _engine.ProbeAsync(asset, new Progress<ProbeResult>(p => { _probes[p.Source.Id] = p; Publish(); }), token);
    }
    private async Task ProbeAsync()
    {
        if (SelectedAsset is not { } asset) return;
        Begin("probe", "正在测试下载线路…");
        try
        {
            var probes = await ProbeSelectedAsync(asset, _operation!.Token);
            _status = probes.FirstOrDefault(p => p.Available) is { } best ? $"推荐线路：{best.Source.Name}" : "所有线路暂时不可用";
        }
        catch (OperationCanceledException) { _status = "测速已取消"; }
        catch (Exception ex) { _status = "测速失败"; _detail = ex.Message; }
        finally { End(); }
    }
    private async Task DownloadAsync()
    {
        if (SelectedAsset is not { } asset) return;
        var path = Path.Combine(_preferences.Folder, asset.Name);
        if (File.Exists(path) && MessageBox.Show(this, "已存在同名文件，是否在下载校验成功后替换？", "替换已有 JAR", MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return;
        _completed = null; _progress = null;
        Begin("download", "正在自动选择下载线路…");
        var token = _operation!.Token;
        try
        {
            var previousResults = _probes.Values.ToArray();
            _probes.Clear(); Publish();
            var probes = await _engine.ProbeForDownloadAsync(asset, _preferences.SourceId, previousResults,
                new Progress<ProbeResult>(p => { _probes[p.Source.Id] = p; Publish(); }), token);
            _completed = await _engine.DownloadAsync(asset, path, probes, _preferences.SourceId, new Progress<TransferProgress>(p =>
            { _progress = p; _status = p.Stage; Publish(); }), token, _preferences.Connections);
            _status = "下载完成";
            _detail = _completed.DigestVerified ? "SHA-256 与发布记录一致 · JAR 校验通过" : "大小与 JAR 结构校验通过 · 该发布未提供 SHA-256";
        }
        catch (OperationCanceledException) { _status = "下载已取消"; _detail = "临时文件已清理，可以重新下载。"; _progress = null; }
        catch (Exception ex) { _status = "下载失败，可重试"; _detail = ex.Message; _progress = null; }
        finally { End(); }
    }
    private void Begin(string operation, string status) { _operation = new(); _operationName = operation; _status = status; _detail = ""; Publish(); }
    private void End() { _operation?.Dispose(); _operation = null; _operationName = ""; Publish(); if (_closeAfterCancel) Close(); }
    private void Save()
    {
        try { Directory.CreateDirectory(StateDirectory); File.WriteAllText(Path.Combine(StateDirectory, "settings.json"), JsonSerializer.Serialize(_preferences)); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
    }
    private void Publish()
    {
        if (!_ready) return;
        Browser.CoreWebView2.PostWebMessageAsJson(JsonSerializer.Serialize(new { type = "state", state = new
        {
            releases = _releases.Select(r => new { tag = r.Tag, label = r.Label, assets = r.Jars.Select(a => new { name = a.Name, loader = a.Loader, minecraft = a.Minecraft, size = a.Size }) }),
            busy = Busy, operation = _operationName, status = _status, detail = _detail, catalogInfo = _catalogInfo, folder = _preferences.Folder,
            selection = new { release = _preferences.Release, minecraft = _preferences.Minecraft, loader = _preferences.Loader }, sourceId = _preferences.SourceId, connections = _preferences.Connections,
            sources = DownloadSource.BuiltIn.Select(s => new { id = s.Id, name = s.Name, url = s.Prefix ?? "https://github.com/" }),
            probes = _probes.Values.Select(p => new { id = p.Source.Id, name = p.Source.Name, available = p.Available, speed = p.BytesPerSecond, milliseconds = p.Milliseconds, detail = p.Detail, ranges = p.SupportsRanges }),
            progress = _progress is null ? null : new { percent = _progress.Percent, received = _progress.Received, total = _progress.Total, speed = _progress.BytesPerSecond, connections = _progress.Connections, source = _progress.Source },
            completed = _completed is null ? null : new { path = _completed.Path, sha256 = _completed.Sha256, verified = _completed.DigestVerified }
        }}));
    }
    private void ShowLicenses()
    {
        var folder = Path.Combine(StateDirectory, "licenses"); Directory.CreateDirectory(folder);
        var assembly = typeof(MainWindow).Assembly;
        const string prefix = "WurstBPlus.Downloader.Frontend.public.licenses.";
        foreach (var name in assembly.GetManifestResourceNames().Where(n => n.StartsWith(prefix, StringComparison.Ordinal)))
        { using var input = assembly.GetManifestResourceStream(name)!; using var output = File.Create(Path.Combine(folder, name[prefix.Length..])); input.CopyTo(output); }
        Shell(folder);
    }
    private static void Shell(string target, string arguments = "") => Process.Start(new ProcessStartInfo(target) { UseShellExecute = true, Arguments = arguments });
    private void Window_Closing(object? sender, CancelEventArgs e)
    {
        if (Busy) { e.Cancel = true; _closeAfterCancel = true; _operation!.Cancel(); }
        else { Save(); _ready = false; Browser.Dispose(); _http.Dispose(); }
    }
    private void Window_SourceInitialized(object? sender, EventArgs e) { var value = 2; _ = DwmSetWindowAttribute(new WindowInteropHelper(this).Handle, 33, ref value, sizeof(int)); }
    [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
    [DllImport("user32.dll")] private static extern bool ReleaseCapture();
    [DllImport("user32.dll")] private static extern IntPtr SendMessage(IntPtr window, int message, IntPtr wParam, IntPtr lParam);
    private sealed record Preferences
    {
        public string Folder { get; init; } = "";
        public string Release { get; init; } = "";
        public string Minecraft { get; init; } = "";
        public string Loader { get; init; } = "Fabric";
        public string SourceId { get; init; } = "auto";
        public int Connections { get; init; } = 4;
    }
}
