using System.IO.Compression;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using WurstBPlus.Downloader.Core;

var passed = 0;
var testRoot = Path.Combine(Path.GetTempPath(), "wurst-downloader-tests-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(testRoot);
var jar = CreateJar();
var asset = new ReleaseAsset
{
    Name = "WurstB+.Plus-1.5.2-Fabric-1.20.1.jar", Size = jar.Length,
    Url = "https://github.com/xiegeezr886/WurstB-Plus/releases/download/v1.5.2/WurstB%2B.Plus-1.5.2-Fabric-1.20.1.jar",
    Digest = "sha256:" + Convert.ToHexString(SHA256.HashData(jar)).ToLowerInvariant()
};

try
{
    await Check("All 115 supplied mirrors are unique HTTPS sources, including the internationalized host", () =>
    {
        Assert(DownloadSource.BuiltIn.Count == 116);
        Assert(DownloadSource.BuiltIn.Select(s => s.Id).Distinct().Count() == 116);
        var mirrors = DownloadSource.BuiltIn.Where(s => s.Prefix is not null).ToArray();
        Assert(mirrors.Select(s => new Uri(s.Prefix!).IdnHost).Distinct().Count() == 115);
        Assert(mirrors.All(s => new Uri(s.Prefix!).Scheme == "https" && s.Prefix!.EndsWith('/')));
        Assert(mirrors.Any(s => s.Name == "ghf.无名氏.top"));
        return Task.CompletedTask;
    });

    await Check("Full scan bounds concurrency and checks every source", async () =>
    {
        int active = 0, maximum = 0, checkedCount = 0;
        var sync = new object();
        using var http = Client(async (_, token) =>
        {
            lock (sync) { active++; checkedCount++; maximum = Math.Max(maximum, active); }
            try { await Task.Delay(20, token); return Response(jar[..262144], true, jar.Length); }
            finally { lock (sync) active--; }
        });
        var results = await new DownloadEngine(http).ProbeAsync(asset, null, default);
        Assert(checkedCount == 116 && results.Count == 116 && results.All(p => p.Available));
        Assert(maximum == DownloadEngine.ProbeConcurrency && active == 0);
    });

    await Check("Cancellation stops queued mirror probes as well as active requests", async () =>
    {
        int started = 0;
        using var cancel = new CancellationTokenSource();
        var allStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        using var http = Client(async (_, token) =>
        {
            if (Interlocked.Increment(ref started) == DownloadEngine.ProbeConcurrency) allStarted.TrySetResult();
            await Task.Delay(Timeout.Infinite, token);
            return new HttpResponseMessage(HttpStatusCode.OK);
        });
        var scan = new DownloadEngine(http).ProbeAsync(asset, null, cancel.Token);
        await allStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        cancel.Cancel();
        await Throws<OperationCanceledException>(() => scan);
        Assert(started == DownloadEngine.ProbeConcurrency);
    });

    await Check("Download probes prioritize manual source and advance past a failed first batch", async () =>
    {
        var requested = new List<string>();
        var selected = DownloadSource.BuiltIn.Last();
        using var http = Client((request, _) =>
        {
            requested.Add(request.RequestUri!.IdnHost);
            return Task.FromResult(Response(jar[..262144], true, jar.Length));
        });
        var results = await new DownloadEngine(http).ProbeForDownloadAsync(asset, selected.Id, [], null, default);
        Assert(requested.First() == new Uri(selected.Prefix!).IdnHost && requested.Count == DownloadEngine.ProbeConcurrency);
        Assert(results.Any(p => p.Source.Id == selected.Id && p.Available));
        int count = 0;
        using var fallbackHttp = Client((_, _) => Task.FromResult(++count <= DownloadEngine.ProbeConcurrency
            ? new HttpResponseMessage(HttpStatusCode.BadGateway) : Response(jar[..262144], true, jar.Length)));
        results = await new DownloadEngine(fallbackHttp).ProbeForDownloadAsync(asset, "auto", [], null, default);
        Assert(count == DownloadEngine.ProbeConcurrency * 2 && results.All(p => p.Available));
    });

    await Check("Asset parser accepts supported loaders and rejects traversal / foreign URLs", () =>
    {
        Assert(asset.IsClientJar && asset.Minecraft == "1.20.1" && asset.Loader == "Fabric");
        foreach (var loader in new[] { "NeoForge", "Forge", "Fabric" }) Assert((asset with { Name = $"WurstB+.Plus-1.5.1-{loader}-26.3.jar" }).Loader == loader);
        foreach (var name in new[] { "sources.jar", "WurstB+.Plus-../../evil-Fabric-1.20.1.jar", "WurstB+.Plus-1.5.2-Fabric-1.20.1.jar.exe" }) Assert(!(asset with { Name = name }).IsClientJar);
        foreach (var url in new[] { "http://github.com/xiegeezr886/WurstB-Plus/releases/download/v1.5.2/a.jar", "https://github.com/other/repo/releases/download/a.jar", "https://github.com.evil.test/xiegeezr886/WurstB-Plus/releases/download/a.jar" }) Assert(!(asset with { Url = url }).IsClientJar);
        return Task.CompletedTask;
    });

    await Check("Catalog excludes draft / unrelated assets and keeps prereleases selectable", () =>
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new[]
        {
            new Release { Tag = "v1.5.2", Prerelease = true, Assets = [asset] },
            new Release { Tag = "draft", Draft = true, Assets = [asset] },
            new Release { Tag = "empty", Assets = [asset with { Name = "sources.jar" }] },
            new Release { Tag = "v1.5.1", Assets = [asset with { Name = "WurstB+.Plus-1.5.1-NeoForge-26.3.jar" }] }
        });
        var releases = ReleaseCatalog.Parse(bytes);
        Assert(releases.Count == 2 && releases.Any(r => r.Prerelease) && releases.Any(r => r.Jars[0].Minecraft == "26.3"));
        return Task.CompletedTask;
    });

    await Check("Catalog falls back from unavailable GitHub API to a working proxy", async () =>
    {
        using var http = Client((request, _) => Task.FromResult(request.RequestUri!.Host == "github.com" || request.RequestUri.Host == "api.github.com"
            ? new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
            : new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(JsonSerializer.SerializeToUtf8Bytes(new[] { new Release { Tag = "v1.5.2", Assets = [asset] } })) }));
        var catalog = await new ReleaseCatalog(http).LoadAsync(default);
        Assert(catalog.Releases.Count == 1 && catalog.Source != "GitHub 直连");
    });

    await Check("Bundled offline catalog contains valid dated releases and official asset digests", async () =>
    {
        using var snapshot = JsonDocument.Parse(await File.ReadAllBytesAsync(Path.Combine(AppContext.BaseDirectory, "ReleaseSnapshot.json")));
        Assert(snapshot.RootElement.GetProperty("fetched_at").GetDateTimeOffset() <= DateTimeOffset.UtcNow.AddMinutes(5));
        var releases = ReleaseCatalog.Parse(Encoding.UTF8.GetBytes(snapshot.RootElement.GetProperty("releases").GetRawText()));
        Assert(releases.Count > 0 && releases.Any(r => !r.Prerelease));
        Assert(releases.SelectMany(r => r.Assets).All(a => a.IsClientJar && a.Sha256 is not null));
    });

    await Check("Probes reject HTML errors, wrong ranges, and dead proxy routes", async () =>
    {
        using var http = Client((request, _) =>
        {
            var host = request.RequestUri!.Host;
            var response = host switch
            {
                "github.com" => Response(jar[..262144], true, jar.Length),
                "gh-proxy.com" => Response(Encoding.UTF8.GetBytes("<html>proxy failure</html>")),
                "ghfast.top" => Response(jar[..262144], true, jar.Length + 1),
                _ => new HttpResponseMessage(HttpStatusCode.BadGateway)
            };
            return Task.FromResult(response);
        });
        var probes = await new DownloadEngine(http).ProbeAsync(asset, null, default);
        Assert(probes.Count(p => p.Available) == 1 && probes[0].Source.Id == "direct" && probes[0].BytesPerSecond > 0);
    });

    await Check("Verified download replaces the destination and removes temporary files", async () =>
    {
        var path = Path.Combine(testRoot, "success.jar");
        await File.WriteAllTextAsync(path, "old file");
        using var http = Client((_, _) => Task.FromResult(Response(jar)));
        var result = await new DownloadEngine(http).DownloadAsync(asset, path, Probes(), null, null, default);
        Assert(result.DigestVerified && (await File.ReadAllBytesAsync(path)).SequenceEqual(jar));
        Assert(!Directory.EnumerateFiles(testRoot, "*.part").Any());
    });

    await Check("Bad SHA-256 triggers automatic fallback instead of saving corrupt content", async () =>
    {
        var corrupt = (byte[])jar.Clone();
        corrupt[100] ^= 0xff;
        using var http = Client((request, _) => Task.FromResult(Response(request.RequestUri!.Host == "github.com" ? corrupt : jar)));
        var result = await new DownloadEngine(http).DownloadAsync(asset, Path.Combine(testRoot, "fallback.jar"), Probes(), null, null, default);
        Assert(result.Source == DownloadSource.BuiltIn[1].Name && result.DigestVerified);
    });

    await Check("Truncated downloads fail, preserve existing JAR, and clean partial files", async () =>
    {
        var path = Path.Combine(testRoot, "preserved.jar");
        await File.WriteAllTextAsync(path, "original");
        using var http = Client((_, _) => Task.FromResult(Response(jar[..100])));
        await Throws<IOException>(() => new DownloadEngine(http).DownloadAsync(asset, path, Probes(), null, null, default));
        Assert(await File.ReadAllTextAsync(path) == "original" && !Directory.EnumerateFiles(testRoot, "*.part").Any());
    });

    await Check("Cancel interrupts active transfers and preserves the destination", async () =>
    {
        var path = Path.Combine(testRoot, "cancel.jar");
        await File.WriteAllTextAsync(path, "keep");
        using var cancel = new CancellationTokenSource(TimeSpan.FromMilliseconds(100));
        using var http = Client((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StreamContent(new BlockingStream()) }));
        await Throws<OperationCanceledException>(() => new DownloadEngine(http).DownloadAsync(asset, path, Probes(), null, null, cancel.Token));
        Assert(await File.ReadAllTextAsync(path) == "keep" && !Directory.EnumerateFiles(testRoot, "*.part").Any());
    });

    await Check("Manual source preference is attempted first with automatic backup retained", async () =>
    {
        var hosts = new List<string>();
        using var http = Client((request, _) => { hosts.Add(request.RequestUri!.Host); return Task.FromResult(hosts.Count == 1 ? new HttpResponseMessage(HttpStatusCode.BadGateway) : Response(jar)); });
        await new DownloadEngine(http).DownloadAsync(asset, Path.Combine(testRoot, "manual.jar"), Probes(), "ghproxy", null, default);
        Assert(hosts.SequenceEqual(new[] { "gh-proxy.com", "github.com" }));
    });

    await Check("Missing digest is reported accurately and invalid mod ZIP is rejected", async () =>
    {
        var path = Path.Combine(testRoot, "nodigest.jar");
        using var http = Client((_, _) => Task.FromResult(Response(jar)));
        var result = await new DownloadEngine(http).DownloadAsync(asset with { Digest = null }, path, Probes(), null, null, default);
        Assert(!result.DigestVerified && result.Sha256.Length == 64);
        var bad = new byte[jar.Length];
        await File.WriteAllBytesAsync(path, bad);
        await Throws<InvalidDataException>(() => DownloadEngine.VerifyAsync(path, asset with { Digest = null }, default));
    });

    await Check("Four byte ranges run concurrently, cover the file exactly, and verify the final hash", async () =>
    {
        var ranges = new System.Collections.Concurrent.ConcurrentBag<(long Start, long End)>();
        var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var active = 0;
        using var http = Client(async (request, token) =>
        {
            var range = request.Headers.Range!.Ranges.Single();
            ranges.Add((range.From!.Value, range.To!.Value));
            if (Interlocked.Increment(ref active) == 4) gate.SetResult();
            await gate.Task.WaitAsync(TimeSpan.FromSeconds(3), token);
            return RangeResponse(jar, range.From.Value, range.To.Value);
        });
        var progress = new List<TransferProgress>();
        var destination = Path.Combine(testRoot, "parallel.jar");
        var result = await new DownloadEngine(http).DownloadAsync(asset, destination, RangeProbes(), null, new ImmediateProgress<TransferProgress>(p => { lock(progress) progress.Add(p); }), default, 4);
        var ordered = ranges.OrderBy(r => r.Start).ToArray();
        Assert(ordered.Length == 4 && ordered[0].Start == 0 && ordered[^1].End == jar.Length - 1);
        for (var i = 1; i < ordered.Length; i++) Assert(ordered[i].Start == ordered[i - 1].End + 1);
        Assert(result.DigestVerified && (await File.ReadAllBytesAsync(destination)).SequenceEqual(jar));
        Assert(progress.Any(p => p.Connections == 4 && p.Received == asset.Size));
    });

    await Check("A range-ignoring proxy falls back to a single full response", async () =>
    {
        var fullRequests = 0;
        using var http = Client((request, _) => { if (request.Headers.Range is null) Interlocked.Increment(ref fullRequests); return Task.FromResult(Response(jar)); });
        var result = await new DownloadEngine(http).DownloadAsync(asset, Path.Combine(testRoot, "range-fallback.jar"), RangeProbes(), null, null, default, 4);
        Assert(result.DigestVerified && fullRequests == 1);
    });

    await Check("Incorrect Content-Range is rejected and another source restarts safely", async () =>
    {
        using var http = Client((request, _) =>
        {
            var range = request.Headers.Range!.Ranges.Single();
            var response = RangeResponse(jar, range.From!.Value, range.To!.Value);
            if (request.RequestUri!.Host == "github.com") response.Content.Headers.ContentRange = new ContentRangeHeaderValue(0, range.To.Value - range.From.Value, jar.Length + 1);
            return Task.FromResult(response);
        });
        var result = await new DownloadEngine(http).DownloadAsync(asset, Path.Combine(testRoot, "bad-range.jar"), RangeProbes(), null, null, default, 4);
        Assert(result.Source == DownloadSource.BuiltIn[1].Name && result.DigestVerified);
    });

    await Check("Cancellation stops all parallel writers and leaves no temporary files", async () =>
    {
        var destination = Path.Combine(testRoot, "parallel-cancel.jar");
        await File.WriteAllTextAsync(destination, "original");
        var started = 0;
        using var cancel = new CancellationTokenSource(TimeSpan.FromMilliseconds(150));
        using var http = Client((request, _) =>
        {
            var range = request.Headers.Range!.Ranges.Single(); Interlocked.Increment(ref started);
            var response = new HttpResponseMessage(HttpStatusCode.PartialContent) { Content = new StreamContent(new BlockingStream()) };
            response.Content.Headers.ContentRange = new ContentRangeHeaderValue(range.From!.Value, range.To!.Value, jar.Length);
            return Task.FromResult(response);
        });
        await Throws<OperationCanceledException>(() => new DownloadEngine(http).DownloadAsync(asset, destination, RangeProbes(), null, null, cancel.Token, 4));
        Assert(started == 4 && await File.ReadAllTextAsync(destination) == "original");
        Assert(!Directory.EnumerateFiles(testRoot, "*.part").Any());
    });

    await Check("Truncated parallel responses cannot pass through file preallocation", async () =>
    {
        using var http = Client((request, _) =>
        {
            var range = request.Headers.Range!.Ranges.Single();
            var response = new HttpResponseMessage(HttpStatusCode.PartialContent) { Content = new ByteArrayContent(jar[(int)range.From!.Value..((int)range.To!.Value)]) };
            response.Content.Headers.ContentRange = new ContentRangeHeaderValue(range.From.Value, range.To.Value, jar.Length);
            return Task.FromResult(response);
        });
        var destination = Path.Combine(testRoot, "truncated-parallel.jar");
        await Throws<IOException>(() => new DownloadEngine(http).DownloadAsync(asset, destination, RangeProbes(), null, null, default, 4));
        Assert(!File.Exists(destination) && !Directory.EnumerateFiles(testRoot, "*.part").Any());
    });

    if (args.Contains("--live"))
    {
        await Check("LIVE GitHub catalog, proxy probes, real JAR download, and official SHA-256", async () =>
        {
            using var http = new HttpClient(new SocketsHttpHandler { ConnectTimeout = TimeSpan.FromSeconds(8) }) { Timeout = Timeout.InfiniteTimeSpan };
            http.DefaultRequestHeaders.UserAgent.ParseAdd("WurstBPlus-Downloader-Tests/1.0");
            using var deadline = new CancellationTokenSource(TimeSpan.FromMinutes(5));
            var catalog = await new ReleaseCatalog(http).LoadAsync(deadline.Token);
            Console.WriteLine($"  Catalog: {catalog.Source}; " + string.Join(", ", catalog.Releases.Select(r => $"{r.Tag} ({r.Jars.Count} JARs)")));
            var liveAsset = catalog.Releases.First(r => !r.Prerelease).Jars.First(a => a.Minecraft == "1.20.1" && a.Loader == "Fabric");
            var engine = new DownloadEngine(http);
            var probes = await engine.ProbeAsync(liveAsset, new ImmediateProgress<ProbeResult>(p => Console.WriteLine($"  {p.Source.Name}: {(p.Available ? $"{p.BytesPerSecond / 1048576:F2} MB/s" : p.Detail)}")), deadline.Token);
            var destination = Path.Combine(testRoot, liveAsset.Name);
            var usedParallel = false;
            var result = await engine.DownloadAsync(liveAsset, destination, probes, null, new ImmediateProgress<TransferProgress>(p =>
            {
                if (p.Connections > 1 && !usedParallel) { usedParallel = true; Console.WriteLine($"  Parallel download: {p.Connections} connections"); }
            }), deadline.Token, 4);
            Assert(result.DigestVerified);
            Console.WriteLine($"  Downloaded {liveAsset.Size} bytes via {result.Source}; SHA-256: {result.Sha256}");
        });
    }
    Console.WriteLine($"PASS: {passed} tests");
}
finally { Directory.Delete(testRoot, true); }

async Task Check(string name, Func<Task> test) { await test(); passed++; Console.WriteLine("PASS " + name); }
static void Assert(bool condition) { if (!condition) throw new Exception("Assertion failed"); }
static async Task Throws<T>(Func<Task> action) where T : Exception
{
    try { await action(); } catch (T) { return; }
    throw new Exception("Expected " + typeof(T).Name);
}
static HttpClient Client(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> handler) => new(new StubHandler(handler));
static ProbeResult[] Probes() => [new(DownloadSource.BuiltIn[0], true, 100000, 10, "ok"), new(DownloadSource.BuiltIn[1], true, 50000, 20, "ok")];
static ProbeResult[] RangeProbes() => Probes().Select(p => p with { SupportsRanges = true }).ToArray();
static HttpResponseMessage RangeResponse(byte[] bytes, long from, long to)
{
    var response = new HttpResponseMessage(HttpStatusCode.PartialContent) { Content = new ByteArrayContent(bytes[(int)from..((int)to + 1)]) };
    response.Content.Headers.ContentRange = new ContentRangeHeaderValue(from, to, bytes.Length);
    return response;
}
static HttpResponseMessage Response(byte[] bytes, bool partial = false, long total = 0)
{
    var response = new HttpResponseMessage(partial ? HttpStatusCode.PartialContent : HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) };
    if (partial) response.Content.Headers.ContentRange = new ContentRangeHeaderValue(0, bytes.Length - 1, total);
    return response;
}
static byte[] CreateJar()
{
    using var stream = new MemoryStream();
    using (var zip = new ZipArchive(stream, ZipArchiveMode.Create, true))
    {
        using (var metadata = new StreamWriter(zip.CreateEntry("fabric.mod.json").Open())) metadata.Write("{\"id\":\"wurst\"}");
        using var payload = zip.CreateEntry("payload.bin", CompressionLevel.NoCompression).Open();
        payload.Write(RandomNumberGenerator.GetBytes(512 * 1024));
    }
    return stream.ToArray();
}
sealed class StubHandler(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> handler) : HttpMessageHandler
{
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => handler(request, cancellationToken);
}
sealed class ImmediateProgress<T>(Action<T> report) : IProgress<T> { public void Report(T value) => report(value); }
sealed class BlockingStream : Stream
{
    public override bool CanRead => true;
    public override bool CanSeek => false;
    public override bool CanWrite => false;
    public override long Length => throw new NotSupportedException();
    public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
    public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken token = default) { await Task.Delay(Timeout.Infinite, token); return 0; }
    public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    public override void Flush() => throw new NotSupportedException();
    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
    public override void SetLength(long value) => throw new NotSupportedException();
    public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
}
