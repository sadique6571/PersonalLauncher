package com.sadique.launcher;

/** Reviewed local-shell setup for this package only; stock launchers and other helpers are preserved. */
final class HomeSetupCommand {
    static final String MARKER="PL_HOME_AUTO_OK";
    static final String FAILED="PL_HOME_AUTO_FAILED";
    private HomeSetupCommand() { }
    static String build(String pkg,int user,int sdk) {
        if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || user<0 || sdk<26)
            throw new IllegalArgumentException("Invalid local Home target");
        String component=pkg+"/com.sadique.launcher.MainActivity";
        String resolve="cmd package resolve-activity --brief --user "+user
                +" -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1";
        String ours=pkg+"/.MainActivity|"+pkg+"/com.sadique.launcher.MainActivity|"
                +pkg+"/.PersonalHomeActivity|"+pkg+"/com.sadique.launcher.PersonalHomeActivity";
        String setup="pm grant --user "+user+" "+pkg+" android.permission.WRITE_SECURE_SETTINGS || exit 1\n"
                +"services=$(settings --user "+user+" get secure enabled_accessibility_services) || exit 1\n"
                +"case \"$services\" in null) services=\"\" ;; esac\noriginal=\"$services\"\n"
                +"for name in HomeKeyService InputKeyService; do\n"
                +"case \":$services:\" in *\":"+pkg+"/.$name:\"*|*\":"+pkg+"/com.sadique.launcher.$name:\"*) ;; "
                +"*) services=\"${services:+$services:}"+pkg+"/com.sadique.launcher.$name\" ;; esac\ndone\n"
                +"if [ \"$services\" != \"$original\" ]; then\n"
                +(sdk>=33?"cmd appops set --user "+user+" "+pkg+" ACCESS_RESTRICTED_SETTINGS allow || exit 1\n":"")
                +"settings --user "+user+" put secure enabled_accessibility_services \"$services\" || exit 1; fi\n"
                +"settings --user "+user+" put secure accessibility_enabled 1 || exit 1\n"
                +"resolved=$("+resolve+")\ncase \"$resolved\" in "+ours+") ;; *) "
                +"cmd package set-home-activity --user "+user+" "+component+" || exit 1 ;; esac\n"
                +"resolved=$("+resolve+")\ncase \"$resolved\" in "+ours+") exit 0 ;; *) exit 1 ;; esac";
        return "if (\n"+setup+"\n); then echo "+MARKER+"; else echo "+FAILED+"; fi\nread -r pl_home_ack";
    }
}
