using System.IO;

namespace WurstBPlus.Downloader;

internal static class AppStorage
{
    public static string DirectoryPath { get; private set; } = "";

    public static void Initialize()
    {
        var candidate = Path.Combine(WindowsFolders.Documents, "WDownloader");
        try
        {
            var temp = Path.Combine(candidate, "Temp");
            Directory.CreateDirectory(temp);
            var probe = Path.Combine(temp, Guid.NewGuid().ToString("N") + ".tmp");
            using (new FileStream(probe, FileMode.CreateNew, FileAccess.Write, FileShare.None, 1, FileOptions.DeleteOnClose)) { }
            DirectoryPath = candidate;
            // Only this process and its WebView2 children use the private cache.
            // Do not change the user's system environment or directory permissions.
            Environment.SetEnvironmentVariable("TEMP", temp);
            Environment.SetEnvironmentVariable("TMP", temp);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            throw new IOException("无法写入系统文档中的下载器数据目录：\n" + candidate + "\n" + ex.Message, ex);
        }
    }

    public static void Log(Exception error)
    {
        if (string.IsNullOrEmpty(DirectoryPath)) return;
        try { File.AppendAllText(Path.Combine(DirectoryPath, "startup-error.log"), $"{DateTimeOffset.Now:O}\n{error}\n\n"); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { }
    }
}
