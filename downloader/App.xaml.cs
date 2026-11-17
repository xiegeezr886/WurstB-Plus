using System.Windows;

namespace WurstBPlus.Downloader;
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        try
        {
            AppStorage.Initialize();
            MainWindow = new MainWindow();
            MainWindow.Show();
        }
        catch (Exception ex)
        {
            AppStorage.Log(ex);
            MessageBox.Show("下载器无法启动：\n" + ex.Message, "下载器启动失败", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }
}
