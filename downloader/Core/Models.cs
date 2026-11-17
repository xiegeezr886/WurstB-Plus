using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace WurstBPlus.Downloader.Core;

public sealed record Release
{
    [JsonPropertyName("tag_name")] public string Tag { get; init; } = "";
    [JsonPropertyName("name")] public string Name { get; init; } = "";
    [JsonPropertyName("prerelease")] public bool Prerelease { get; init; }
    [JsonPropertyName("draft")] public bool Draft { get; init; }
    [JsonPropertyName("published_at")] public DateTimeOffset? PublishedAt { get; init; }
    [JsonPropertyName("assets")] public List<ReleaseAsset> Assets { get; init; } = [];
    [JsonIgnore] public string Label => $"{Tag}  ·  {(Prerelease ? "预发布" : "正式版")}";
    [JsonIgnore] public IReadOnlyList<ReleaseAsset> Jars => Assets.Where(a => a.IsClientJar).ToArray();
    public override string ToString() => Label;
}

public sealed record ReleaseAsset
{
    private static readonly Regex Pattern = new(@"^WurstB\+\.Plus-(?<version>[0-9][0-9A-Za-z.+_-]*)-(?<loader>NeoForge|Forge|Fabric)-(?<mc>\d+(?:\.\d+){1,2})\.jar$", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);
    [JsonPropertyName("name")] public string Name { get; init; } = "";
    [JsonPropertyName("size")] public long Size { get; init; }
    [JsonPropertyName("browser_download_url")] public string Url { get; init; } = "";
    [JsonPropertyName("digest")] public string? Digest { get; init; }
    [JsonIgnore] public bool IsClientJar => Pattern.IsMatch(Name) && Size > 0 && IsOfficialAssetUrl(Url);
    [JsonIgnore] public string Loader => Pattern.Match(Name).Groups["loader"].Value;
    [JsonIgnore] public string Minecraft => Pattern.Match(Name).Groups["mc"].Value;
    [JsonIgnore] public string SizeLabel => $"{Size / 1048576d:F1} MB";
    [JsonIgnore] public string? Sha256 => Digest is { } d && Regex.IsMatch(d, "^sha256:[0-9a-fA-F]{64}$") ? d[7..] : null;

    public static bool IsOfficialAssetUrl(string url) => Uri.TryCreate(url, UriKind.Absolute, out var uri)
        && uri.Scheme == "https" && uri.Host == "github.com" && uri.IsDefaultPort
        && string.IsNullOrEmpty(uri.UserInfo) && uri.AbsolutePath.StartsWith("/xiegeezr886/WurstB-Plus/releases/download/", StringComparison.Ordinal)
        && string.IsNullOrEmpty(uri.Query) && string.IsNullOrEmpty(uri.Fragment);
}

public sealed record DownloadSource(string Id, string Name, string? Prefix)
{
    private static readonly DownloadSource[] OriginalSources = [
        new("direct", "GitHub 直连", null),
        new("ghproxy", "GH-Proxy", "https://gh-proxy.com/"),
        new("ghfast", "GHFast", "https://ghfast.top/"),
        new("ghproxynet", "GHProxy.net", "https://ghproxy.net/"),
        new("ghproxycc", "GHProxy.cc", "https://ghproxy.cc/")
    ];
    public static IReadOnlyList<DownloadSource> BuiltIn { get; } = LoadSources();
    private static IReadOnlyList<DownloadSource> LoadSources()
    {
        var sources = new List<DownloadSource>(OriginalSources);
        var hosts = sources.Where(s => s.Prefix is not null).Select(s => new Uri(s.Prefix!).IdnHost).ToHashSet(StringComparer.OrdinalIgnoreCase);
        using var resource = typeof(DownloadSource).Assembly.GetManifestResourceStream("WurstBPlus.Downloader.Core.MirrorHosts.txt")!;
        using var reader = new StreamReader(resource);
        while (reader.ReadLine() is { } line)
        {
            var host = line.Trim();
            if (host.Length == 0) continue;
            var uri = new Uri("https://" + host + "/");
            if (hosts.Add(uri.IdnHost)) sources.Add(new("mirror:" + uri.IdnHost, host, "https://" + uri.IdnHost + "/"));
        }
        return sources.AsReadOnly();
    }
    public string Resolve(string url) => Prefix is null ? url : Prefix + url;
    public override string ToString() => Name;
}

public sealed record ProbeResult(DownloadSource Source, bool Available, double BytesPerSecond, long Milliseconds, string Detail, bool SupportsRanges = false);
public sealed record TransferProgress(string Stage, string Source, long Received, long Total, double BytesPerSecond, int Attempt, int Connections = 1)
{
    public double Percent => Total > 0 ? Math.Clamp(Received * 100d / Total, 0, 100) : 0;
}
public sealed record DownloadResult(string Path, string Source, string Sha256, bool DigestVerified);
public sealed record CatalogResult(IReadOnlyList<Release> Releases, string Source);
