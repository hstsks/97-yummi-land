package kr.yuumi97.core.rpg;

import org.joml.Vector3f;

public final class VfxDirectionCheck {
    public static void run() {
        Vector3f[] directions={new Vector3f(1,0,0),new Vector3f(-1,0,0),new Vector3f(0,1,0),new Vector3f(0,-1,0),new Vector3f(0,0,1),new Vector3f(0,0,-1),new Vector3f(2,3,-5),new Vector3f(-3,-2,4)};
        for(Vector3f axis:new Vector3f[]{new Vector3f(0,0,-1),new Vector3f(0,-1,0)})for(Vector3f direction:directions)for(float spin:new float[]{0,.3f,1.7f}){
            Vector3f result=SkillVfxChoreographer.orientation(axis,direction,spin).transform(new Vector3f(axis));
            if(result.distance(new Vector3f(direction).normalize())>1.e-5f)throw new AssertionError("VFX tip does not follow travel: "+axis+" -> "+direction+" = "+result);
        }
        Vector3f fallback=SkillVfxChoreographer.orientation(new Vector3f(0,0,-1),new Vector3f(),0).transform(new Vector3f(0,0,-1));
        if(!fallback.isFinite()||fallback.distance(new Vector3f(0,0,-1))>1.e-5f)throw new AssertionError("Stationary VFX orientation is invalid");
        System.out.println("VFX_DIRECTION_CHECK PASS: 48 tip/path alignments including vertical, oblique and local spin; zero-motion fallback");
    }
}
