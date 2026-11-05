package com.sadique.launcher;

/** Detached shell installer survives the old app process being replaced; no untrusted shell input. */
final class UpdateInstallCommand {
    static final String OK="PL_UPDATE_QUEUED", FAILED="PL_UPDATE_FAILED";
    private UpdateInstallCommand() { }
    static String build(String pkg,int user,String sha256) {
        if (pkg==null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
                || user<0 || sha256==null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid update target");
        String file="/data/local/tmp/personal-launcher-update-"+sha256+".apk";
        String install="sleep 2; if pm install -r --user "+user+" "+file+"; then am start --user "+user+" -n "+pkg+"/com.sadique.launcher.MainActivity; fi; rm -f "+file;
        return "umask 077\nif content read --user "+user+" --uri content://"+pkg+".updates/apk > "+file
                +" && [ \"$(sha256sum "+file+" | cut -d ' ' -f 1)\" = '"+sha256+"' ]; then\n"
                +"chmod 400 "+file+"\nnohup sh -c '"+install+"' </dev/null >"+file+".log 2>&1 &\necho "+OK
                +"\nelse rm -f "+file+"; echo "+FAILED+"; fi\nread -r pl_update_ack";
    }
}
