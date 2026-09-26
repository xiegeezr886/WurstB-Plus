import java.io.File;
import java.net.URL;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.actions.ClientInstall;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Util;

// Some early NeoForge installers bundle ClientInstall but expose only --installServer.
public class LegacyClientInstall
{
    public static void main(String[] args) throws Exception
    {
        if(args.length != 2)
            throw new IllegalArgumentException("Usage: <minecraft-dir> <installer-jar>");
        SimpleInstaller.headless = true;
        SimpleInstaller.mirror = new URL("https://bmclapi2.bangbang93.com/maven/");
        ClientInstall action = new ClientInstall(Util.loadInstallProfile(),
            ProgressCallback.TO_STD_OUT);
        if(!action.run(new File(args[0]), path -> true, new File(args[1])))
            System.exit(1);
    }
}
