package moe.rikaaa0928.uot;

import android.content.Context;

public class Init {
    private static native void init(Context ctx);

    public void callInit(Context ctx) {
        init(ctx);
    }
}
