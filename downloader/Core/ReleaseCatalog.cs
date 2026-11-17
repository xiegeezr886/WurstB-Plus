using System.Net.Http.Headers;
using System.Text.Json;

namespace WurstBPlus.Downloader.Core;

public sealed class ReleaseCatalog(HttpClient http)
{
    public const string Repository = "https://github.com/xiegeezr886/WurstB-Plus";
    public const string ApiUrl = "https://api.github.com/repos/xiegeezr886/WurstB-Plus/releases?per_page=100";

    public async Task<CatalogResult> LoadAsync(CancellationToken cancellationToken)
    {
        // Prefer metadata directly from GitHub, especially its SHA-256 digests.
        try { return await FetchAsync(DownloadSource.BuiltIn[0], TimeSpan.FromSeconds(6), cancellationToken); }
        catch (Exception e) when (e is HttpRequestException or IOException or JsonException or OperationCanceledException && !cancellationToken.IsCancellationRequested) { }

        using var race = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        race.CancelAfter(TimeSpan.FromSeconds(12));
        using var slots = new SemaphoreSlim(8);
        var pending = DownloadSource.BuiltIn.Skip(1).Select(async source =>
        {
            await slots.WaitAsync(race.Token);
            try { race.Token.ThrowIfCancellationRequested(); return await FetchAsync(source, TimeSpan.FromSeconds(6), race.Token); }
            finally { slots.Release(); }
        }).ToList();
        var failures = new List<string>();
        try
        {
            while (pending.Count > 0)
            {
                var done = await Task.WhenAny(pending);
                pending.Remove(done);
                try { return await done; }
                catch (Exception e) when (!cancellationToken.IsCancellationRequested) { failures.Add(e.Message); }
            }
            cancellationToken.ThrowIfCancellationRequested();
            throw new HttpRequestException("无法获取 GitHub 发布列表。请检查网络后刷新；GitHub API 也可能暂时限流。 " + string.Join(" / ", failures.Distinct()));
        }
        finally
        {
            await race.CancelAsync();
            try { await Task.WhenAll(pending); } catch { /* Observe losing requests. */ }
        }
    }

    private async Task<CatalogResult> FetchAsync(DownloadSource source, TimeSpan timeout, CancellationToken token)
    {
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(token);
        deadline.CancelAfter(timeout);
        using var request = new HttpRequestMessage(HttpMethod.Get, source.Resolve(ApiUrl));
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, deadline.Token);
        response.EnsureSuccessStatusCode();
        await using var stream = await response.Content.ReadAsStreamAsync(deadline.Token);
        // Limit metadata reads so a proxy error page or runaway response cannot consume unbounded memory.
        using var buffer = new MemoryStream();
        var chunk = new byte[16384];
        int read;
        while ((read = await stream.ReadAsync(chunk, deadline.Token)) > 0)
        {
            if (buffer.Length + read > 8 * 1024 * 1024) throw new IOException("发布列表超出大小限制。");
            buffer.Write(chunk, 0, read);
        }
        var releases = Parse(buffer.ToArray());
        if (releases.Count == 0) throw new IOException(source.Name + " 未返回有效的客户端发布。");
        return new(releases, source.Name);
    }

    public static IReadOnlyList<Release> Parse(byte[] json) =>
        (JsonSerializer.Deserialize<List<Release>>(json) ?? []).Where(r => !r.Draft && r.Jars.Count > 0)
        .OrderByDescending(r => r.PublishedAt).ToArray();
}
