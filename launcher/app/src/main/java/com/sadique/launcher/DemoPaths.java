package com.sadique.launcher;
import android.graphics.Path;
final class DemoPaths {
    private DemoPaths() { }
    static void set(Path path,int style,float w,float h,float radius) {
        path.reset();
        for(float[] cmd:DemoGeometry.outline(style,w,h,radius)) switch((int)cmd[0]) {
            case 0:path.moveTo(cmd[1],cmd[2]);break;
            case 1:path.lineTo(cmd[1],cmd[2]);break;
            case 2:path.quadTo(cmd[1],cmd[2],cmd[3],cmd[4]);break;
            default:path.close();break;
        }
    }
}
