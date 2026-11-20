// Native Minecraft model coordinates, not Blender coordinates.
export function rotateX(v,degrees){const a=degrees*Math.PI/180,c=Math.cos(a),s=Math.sin(a);return[v[0],v[1]*c-v[2]*s,v[1]*s+v[2]*c];}
export function rotateY(v,degrees){const a=degrees*Math.PI/180,c=Math.cos(a),s=Math.sin(a);return[v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c];}
export function worldPoint(v,{floor=[0,0,0],scale=1,yaw=180,pitch=0,offset=[0,0,0],root=false}={}){
 const anchor=root?[8,0,8]:[8,8,8];let p=v.map((n,i)=>n-anchor[i]);
 if(!root)p=rotateX(p,-pitch).map((n,i)=>n+offset[i]);
 p=rotateY(p,180-yaw);return p.map((n,i)=>floor[i]+n*scale/16);
}
export const goldenFixture={floor:[0,0,0],scale:1,yaw:0,pitch:-60,offset:[0,16,0],vertex:[8,8,-8],expectedPivot:[0,1,0],expectedPoint:[0,1.8660254037844386,.5]};
