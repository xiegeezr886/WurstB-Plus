using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace WurstBPlus.Downloader.Core;

public sealed class DownloadEngine(HttpClient http)
{
    private const int SampleSize = 256 * 1024;
    public const int ProbeConcurrency = 8;

    public async Task<IReadOnlyList<ProbeResult>> ProbeAsync(ReleaseAsset asset, IProgress<ProbeResult>? progress, CancellationToken token,
        IReadOnlyList<DownloadSource>? sources = null)
    {
        ValidateAsset(asset);
        using var slots = new SemaphoreSlim(ProbeConcurrency);
        var results = await Task.WhenAll((sources ?? DownloadSource.BuiltIn).Select(async source =>
        {
            await slots.WaitAsync(token);
            try
            {
                token.ThrowIfCancellationRequested();
                var result = await ProbeOneAsync(asset, source, token);
                progress?.Report(result);
                return result;
            }
            finally { slots.Release(); }
        }));
        token.ThrowIfCancellationRequested();
        return results.OrderByDescending(r => r.Available).ThenByDescending(r => r.BytesPerSecond).ToArray();
    }

    public async Task<IReadOnlyList<ProbeResult>> ProbeForDownloadAsync(ReleaseAsset asset, string preferredSourceId,
        IReadOnlyList<ProbeResult> previousResults, IProgress<ProbeResult>? progress, CancellationToken token)
    {
        // Recheck the requested source and previously fast sources, then work through
        // all mirrors in bounded batches. Download can start without waiting for 115 timeouts.
        var candidates = DownloadSource.BuiltIn.Where(s => s.Id == preferredSourceId)
            .Concat(previousResults.Where(p => p.Available).OrderByDescending(p => p.BytesPerSecond).Select(p => p.Source))
            .Concat(DownloadSource.BuiltIn).DistinctBy(s => s.Id).ToArray();
        foreach (var batch in candidates.Chunk(ProbeConcurrency))
        {
            var results = await ProbeAsync(asset, progress, token, batch);
            if (results.Any(p => p.Available)) return results;
        }
        return [];
    }

    private async Task<ProbeResult> ProbeOneAsync(ReleaseAsset asset, DownloadSource source, CancellationToken token)
    {
        var watch = Stopwatch.StartNew();
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(token);
        deadline.CancelAfter(TimeSpan.FromSeconds(8));
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, source.Resolve(asset.Url));
            request.Headers.Range = new RangeHeaderValue(0, Math.Min(SampleSize, asset.Size) - 1);
            using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, deadline.Token);
            response.EnsureSuccessStatusCode();
            if (response.StatusCode == HttpStatusCode.PartialContent && (response.Content.Headers.ContentRange?.From != 0 || response.Content.Headers.ContentRange?.To != Math.Min(SampleSize, asset.Size) - 1 || response.Content.Headers.ContentRange?.Length != asset.Size))
                throw new IOException("分段响应与发布文件不一致");
            await using var stream = await response.Content.ReadAsStreamAsync(deadline.Token);
            var buffer = new byte[Math.Min(SampleSize, asset.Size)];
            var count = 0;
            while (count < buffer.Length)
            {
                var n = await stream.ReadAsync(buffer.AsMemory(count), deadline.Token);
                if (n == 0) break;
                count += n;
            }
            if (count < 4 || buffer[0] != 0x50 || buffer[1] != 0x4b || buffer[2] != 3 || buffer[3] != 4)
                throw new IOException("返回内容不是 JAR");
            if (count != buffer.Length) throw new IOException("测速数据不完整");
            return new(source, true, count / Math.Max(watch.Elapsed.TotalSeconds, .001), watch.ElapsedMilliseconds, "可用", response.StatusCode == HttpStatusCode.PartialContent);
        }
        catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or IOException or OperationCanceledException)
        {
            return new(source, false, 0, watch.ElapsedMilliseconds, e is OperationCanceledException ? "连接超时" : e.Message);
        }
    }

    public async Task<DownloadResult> DownloadAsync(ReleaseAsset asset, string destination, IReadOnlyList<ProbeResult> probes,
        string? preferredSourceId, IProgress<TransferProgress>? progress, CancellationToken token, int connections = 4)
    {
        ValidateAsset(asset);
        if (connections is not (1 or 2 or 4 or 8)) throw new ArgumentOutOfRangeException(nameof(connections));
        var candidates = probes.Where(p => p.Available).OrderByDescending(p => p.Source.Id == preferredSourceId)
            .ThenByDescending(p => p.BytesPerSecond).DistinctBy(p => p.Source.Id).ToArray();
        if (candidates.Length == 0) throw new IOException("所有下载源均不可用。请检查网络，或稍后重新测速。");
        var path = Path.GetFullPath(destination);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        // Unique temporary files preserve any existing JAR until verification succeeds.
        var temp = path + "." + Guid.NewGuid().ToString("N") + ".part";
        var failures = new List<string>();
        try
        {
            for (var i = 0; i < candidates.Length; i++)
            {
                token.ThrowIfCancellationRequested();
                var source = candidates[i].Source;
                try
                {
                    progress?.Report(new(i == 0 ? "正在连接" : "失败后自动切换", source.Name, 0, asset.Size, 0, i + 1));
                    if (connections > 1 && candidates[i].SupportsRanges && asset.Size >= connections * 64 * 1024L)
                    {
                        try { await TransferParallelAsync(asset, source, temp, i + 1, connections, progress, token); }
                        catch (RangeNotSupportedException) when (!token.IsCancellationRequested)
                        {
                            progress?.Report(new("线路不支持分段，切换单线程", source.Name, 0, asset.Size, 0, i + 1));
                            await TransferAsync(asset, source, temp, i + 1, progress, token);
                        }
                    }
                    else await TransferAsync(asset, source, temp, i + 1, progress, token);
                    progress?.Report(new("正在校验 JAR", source.Name, asset.Size, asset.Size, 0, i + 1));
                    var hash = await VerifyAsync(temp, asset, token);
                    token.ThrowIfCancellationRequested();
                    File.Move(temp, path, true);
                    return new(path, source.Name, hash, asset.Sha256 is not null);
                }
                catch (Exception e) when (!token.IsCancellationRequested && e is HttpRequestException or IOException or InvalidDataException or OperationCanceledException)
                {
                    failures.Add($"{source.Name}: {(e is OperationCanceledException ? "连接或读取超时" : e.Message)}");
                }
            }
            throw new IOException("下载未完成，已尝试全部可用源。\n" + string.Join("\n", failures));
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }

    private sealed class RangeNotSupportedException : IOException;

    private async Task TransferParallelAsync(ReleaseAsset asset, DownloadSource source, string temp, int attempt,
        int connections, IProgress<TransferProgress>? progress, CancellationToken token)
    {
        using var group = CancellationTokenSource.CreateLinkedTokenSource(token);
        using var file = File.OpenHandle(temp, FileMode.Create, FileAccess.Write, FileShare.None, FileOptions.Asynchronous | FileOptions.RandomAccess);
        RandomAccess.SetLength(file, asset.Size);
        var watch = Stopwatch.StartNew();
        var reportLock = new object();
        long received = 0, lastBytes = 0;
        var lastReport = TimeSpan.Zero;
        Exception? firstFailure = null;
        progress?.Report(new("正在下载", source.Name, 0, asset.Size, 0, attempt, connections));
        var tasks = Enumerable.Range(0, connections).Select(async index =>
        {
            var start = asset.Size * index / connections;
            var end = asset.Size * (index + 1) / connections - 1;
            try
            {
                using var connection = CancellationTokenSource.CreateLinkedTokenSource(group.Token);
                connection.CancelAfter(TimeSpan.FromSeconds(15));
                using var request = new HttpRequestMessage(HttpMethod.Get, source.Resolve(asset.Url));
                request.Headers.Range = new RangeHeaderValue(start, end);
                using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, connection.Token);
                if (response.StatusCode is HttpStatusCode.OK or HttpStatusCode.RequestedRangeNotSatisfiable)
                    throw new RangeNotSupportedException();
                response.EnsureSuccessStatusCode();
                var range = response.Content.Headers.ContentRange;
                if (response.StatusCode != HttpStatusCode.PartialContent || range?.Unit != "bytes" || range.From != start || range.To != end || range.Length != asset.Size)
                    throw new IOException("分段响应范围与请求不一致。");
                if (response.Content.Headers.ContentLength is { } length && length != end - start + 1)
                    throw new IOException("分段长度与请求不一致。");
                await using var input = await response.Content.ReadAsStreamAsync(group.Token);
                using var idle = CancellationTokenSource.CreateLinkedTokenSource(group.Token);
                var buffer = new byte[131072];
                var offset = start;
                while (true)
                {
                    idle.CancelAfter(TimeSpan.FromSeconds(20));
                    var count = await input.ReadAsync(buffer, idle.Token);
                    idle.CancelAfter(Timeout.InfiniteTimeSpan);
                    if (count == 0) break;
                    if (count > end - offset + 1) throw new IOException("分段超出请求范围。");
                    await RandomAccess.WriteAsync(file, buffer.AsMemory(0, count), offset, group.Token);
                    offset += count;
                    lock (reportLock)
                    {
                        received += count;
                        var now = watch.Elapsed;
                        if ((now - lastReport).TotalMilliseconds >= 150 || received == asset.Size)
                        {
                            progress?.Report(new("正在下载", source.Name, received, asset.Size,
                                (received - lastBytes) / Math.Max((now - lastReport).TotalSeconds, .001), attempt, connections));
                            lastBytes = received;
                            lastReport = now;
                        }
                    }
                }
                if (offset != end + 1) throw new IOException("分段连接提前结束。");
            }
            catch (Exception ex)
            {
                // Preserve the original failure, cancel siblings, and await all writers before retrying.
                Interlocked.CompareExchange(ref firstFailure, ex, null);
                await group.CancelAsync();
                throw;
            }
        }).ToArray();
        try { await Task.WhenAll(tasks); }
        catch
        {
            token.ThrowIfCancellationRequested();
            System.Runtime.ExceptionServices.ExceptionDispatchInfo.Capture(firstFailure!).Throw();
            throw;
        }
    }

    private async Task TransferAsync(ReleaseAsset asset, DownloadSource source, string temp, int attempt, IProgress<TransferProgress>? progress, CancellationToken token)
    {
        using var connection = CancellationTokenSource.CreateLinkedTokenSource(token);
        connection.CancelAfter(TimeSpan.FromSeconds(15));
        using var request = new HttpRequestMessage(HttpMethod.Get, source.Resolve(asset.Url));
        using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, connection.Token);
        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength is { } length && length != asset.Size)
            throw new IOException("文件长度与 GitHub 发布记录不符。");
        await using var input = await response.Content.ReadAsStreamAsync(token);
        await using var output = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None, 131072, true);
        var buffer = new byte[131072];
        long received = 0, lastBytes = 0;
        var watch = Stopwatch.StartNew();
        var lastReport = TimeSpan.Zero;
        using var idle = CancellationTokenSource.CreateLinkedTokenSource(token);
        while (true)
        {
            idle.CancelAfter(TimeSpan.FromSeconds(20));
            var count = await input.ReadAsync(buffer, idle.Token);
            idle.CancelAfter(Timeout.InfiniteTimeSpan);
            if (count == 0) break;
            received += count;
            if (received > asset.Size) throw new IOException("下载内容超出发布文件大小。");
            await output.WriteAsync(buffer.AsMemory(0, count), token);
            var now = watch.Elapsed;
            if ((now - lastReport).TotalMilliseconds >= 150 || received == asset.Size)
            {
                progress?.Report(new("正在下载", source.Name, received, asset.Size,
                    (received - lastBytes) / Math.Max((now - lastReport).TotalSeconds, .001), attempt));
                lastBytes = received;
                lastReport = now;
            }
        }
        if (received != asset.Size) throw new IOException("连接提前结束，文件不完整。");
    }

    public static async Task<string> VerifyAsync(string path, ReleaseAsset asset, CancellationToken token)
    {
        await using var stream = File.OpenRead(path);
        if (stream.Length != asset.Size) throw new InvalidDataException("文件大小校验失败。");
        var hash = Convert.ToHexString(await SHA256.HashDataAsync(stream, token)).ToLowerInvariant();
        if (asset.Sha256 is { } expected && !string.Equals(expected, hash, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("SHA-256 校验失败，该下载源返回的文件不可信。");
        stream.Position = 0;
        using var archive = new ZipArchive(stream, ZipArchiveMode.Read, true);
        if (archive.Entries.Count == 0 || !archive.Entries.Any(e => e.FullName is "fabric.mod.json" or "META-INF/mods.toml" or "META-INF/neoforge.mods.toml"))
            throw new InvalidDataException("文件不是有效的客户端模组 JAR。");
        return hash;
    }

    private static void ValidateAsset(ReleaseAsset asset)
    {
        if (!asset.IsClientJar) throw new ArgumentException("只能下载本项目 GitHub Release 中的客户端 JAR。");
    }
}
