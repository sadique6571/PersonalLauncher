package com.sadique.launcher;

import java.util.HashMap;
import java.util.Map;

/** Per-device getevent frame state. Unrelated devices cannot release a held volume key. */
final class VolumeInputRouter {
    private final FastVolumeController controller;
    private final Map<String,FastVolumeController.Parser> parsers=new HashMap<>();
    private String active;
    private int key;
    VolumeInputRouter(FastVolumeController controller) { this.controller=controller; }
    void line(String line) {
        int start=line.indexOf("/dev/input/event"); if (start<0) return;
        int end=start+16;
        while (end<line.length() && Character.isDigit(line.charAt(end))) end++;
        if (end==start+16) return;
        String device=line.substring(start,end);
        if (line.startsWith("remove device") || line.startsWith("add device")) {
            parsers.remove(device);
            if (device.equals(active)) { controller.stop(); active=null; key=0; }
            return;
        }
        FastVolumeController.Parser parser=parsers.get(device);
        if (parser==null) {
            parser=new FastVolumeController.Parser(new FastVolumeController.Parser.Sink() {
                public void down(int value) { active=device; key=value; controller.down(value); }
                public void up(int value) { if (device.equals(active) && key==value) { controller.up(value); active=null; key=0; } }
                public void unknownUp() { if (device.equals(active)) { controller.unknownUp(); active=null; key=0; } }
                public void stop() { if (device.equals(active)) { controller.stop(); active=null; key=0; } }
            });
            parsers.put(device,parser);
        }
        parser.line(line);
    }
}
