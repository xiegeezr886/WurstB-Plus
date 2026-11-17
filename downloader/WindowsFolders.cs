using System.Runtime.InteropServices;

namespace WurstBPlus.Downloader;

internal static class WindowsFolders
{
    // FOLDERID_Downloads: respects Explorer's Location setting and redirected folders.
    private static readonly Guid DownloadsId = new("374DE290-123F-4565-9164-39C4925E467B");
    private static readonly Guid DocumentsId = new("FDD39AD0-238F-46AF-ADB4-6C85480369C7");

    public static string Documents => GetKnownFolder(DocumentsId);

    public static string Downloads => GetKnownFolder(DownloadsId);

    private static string GetKnownFolder(Guid id)
    {
        var result = SHGetKnownFolderPath(ref id, 0x4000, IntPtr.Zero, out var path); // KF_FLAG_DONT_VERIFY
        try
        {
            Marshal.ThrowExceptionForHR(result);
            return Marshal.PtrToStringUni(path) ?? throw new InvalidOperationException("无法获取系统文件夹。");
        }
        finally { if (path != IntPtr.Zero) Marshal.FreeCoTaskMem(path); }
    }

    [DllImport("shell32.dll")]
    private static extern int SHGetKnownFolderPath(ref Guid folderId, uint flags, IntPtr token, out IntPtr path);
}
