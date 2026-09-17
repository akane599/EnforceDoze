package com.akylas.enforcedoze;
import androidx.annotation.Keep;
/** Root-mode bridge to named platform interfaces, launched only for fixed internal operations. */
@Keep public final class PrivilegedCommand {
    @Keep public static void main(String[] args) {
        if (args.length == 1 && args[0].startsWith("@")) System.out.println(new PrivilegedService().run(args[0]));
        else System.out.println("{\"code\":-1,\"output\":\"Invalid internal operation\"}");
        System.exit(0);
    }
}
