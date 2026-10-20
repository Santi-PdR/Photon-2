/** Confirms the target pack's Kotlin module and Photon's private Kotlin module both load. */
public final class KotlinRuntimeLayerProbe {
    private static final String PRIVATE_INTRINSICS =
            "com.lowdragmc.photon.internal.embedded.kotlin.jvm.internal.Intrinsics";

    private KotlinRuntimeLayerProbe() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> profileRuntime = Class.forName("kotlin.jvm.internal.Intrinsics");
        Class<?> photonRuntime = Class.forName(PRIVATE_INTRINSICS);
        Object profileVersion = Class.forName("kotlin.KotlinVersion").getField("CURRENT").get(null);
        Object photonVersion = Class.forName(
                "com.lowdragmc.photon.internal.embedded.kotlin.KotlinVersion").getField("CURRENT").get(null);
        Class<?> ldlibKotlinClass = Class.forName(
                "com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder_dslKt$rpcEvent$1");
        Class<?> relocatedKotlinApi = java.util.Arrays.stream(ldlibKotlinClass.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .filter(type -> type.getName().startsWith("com.lowdragmc.photon.internal.embedded.kotlin."))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "LDLib2 Kotlin class did not link to Photon's private Kotlin runtime"));

        if (profileRuntime.getModule() == photonRuntime.getModule()) {
            throw new IllegalStateException("Photon Kotlin runtime was not isolated from profile Kotlin");
        }

        photonRuntime.getMethod("checkNotNullParameter", Object.class, String.class)
                .invoke(null, new Object(), "photon runtime probe");
        profileRuntime.getMethod("checkNotNullParameter", Object.class, String.class)
                .invoke(null, new Object(), "profile runtime probe");
        if (!photonVersion.toString().equals("2.2.21")) {
            throw new IllegalStateException("Unexpected private Kotlin version: " + photonVersion);
        }
        System.out.println("Kotlin runtimes loaded in distinct modules: "
                + photonRuntime.getModule().getName() + " (" + photonVersion + ") and "
                + profileRuntime.getModule().getName() + " (" + profileVersion + ")"
                + "; LDLib2 field linked to " + relocatedKotlinApi.getName());
    }
}
