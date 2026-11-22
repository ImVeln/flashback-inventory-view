package re.effects.fbinv;

import com.moulberry.flashback.exporting.ExportSettings;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.WeakHashMap;

public final class ExportInv {
    private static final Map<ExportSettings, InvScreen> VIEWS = new WeakHashMap<>();

    private ExportInv() {
    }

    public static synchronized void bind(ExportSettings cfg, InvScreen view) {
        VIEWS.put(cfg, view);
    }

    public static synchronized InvScreen take(ExportSettings cfg) {
        if (VIEWS.containsKey(cfg)) {
            return VIEWS.remove(cfg);
        }
        return InvScreen.get(Minecraft.getInstance().screen);
    }
}
